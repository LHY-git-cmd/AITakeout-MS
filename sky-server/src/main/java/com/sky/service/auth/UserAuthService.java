package com.sky.service.auth;

import com.sky.auth.AuthClientContext;
import com.sky.constant.JwtClaimsConstant;
import com.sky.dto.UserPasswordLoginDTO;
import com.sky.dto.UserRegisterDTO;
import com.sky.entity.SmsVerification;
import com.sky.entity.User;
import com.sky.entity.UserSecurityAudit;
import com.sky.entity.UserSession;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.SmsVerificationMapper;
import com.sky.mapper.UserMapper;
import com.sky.mapper.UserSecurityAuditMapper;
import com.sky.mapper.UserSessionMapper;
import com.sky.properties.JwtProperties;
import com.sky.service.account.AccountService;
import com.sky.utils.JwtUtil;
import com.sky.vo.UserSessionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

/** Persistent phone account authentication and refresh-token lifecycle. */
@Service
public class UserAuthService {
    public static final long ACCESS_TTL_MILLIS = 15 * 60 * 1000L;
    /** Refresh 会话有效期与用户端 Cookie 保持一致，为 2 小时。 */
    public static final long REFRESH_TTL_HOURS = 2L;
    private static final String REGISTER_PURPOSE = "register";
    public static final String CHANGE_OLD_PHONE_PURPOSE = "change_old_phone";
    public static final String CHANGE_NEW_PHONE_PURPOSE = "change_new_phone";
    public static final String CHANGE_PASSWORD_PURPOSE = "change_password";
    private static final String DEFAULT_TEST_SECRET = "test-user-auth-secret-key-test-user-auth-secret-key";

    private final UserMapper userMapper;
    private final UserSessionMapper sessionMapper;
    private final SmsVerificationMapper smsMapper;
    private final UserSecurityAuditMapper auditMapper;
    private final UserSecurityAuditService auditService;
    private final SmsCooldownService cooldownService;
    private final PasswordEncoder passwordEncoder;
    private final AccountService accountService;
    private final SmsGateway smsGateway;
    private final String jwtSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public UserAuthService(UserMapper userMapper, UserSessionMapper sessionMapper,
                           SmsVerificationMapper smsMapper, UserSecurityAuditMapper auditMapper,
                           PasswordEncoder passwordEncoder, AccountService accountService,
                           SmsGateway smsGateway, JwtProperties jwtProperties,
                           UserSecurityAuditService auditService, SmsCooldownService cooldownService) {
        this(userMapper, sessionMapper, smsMapper, auditMapper, passwordEncoder, accountService,
                smsGateway, jwtProperties.getUserSecretKey(), auditService, cooldownService);
    }

    UserAuthService(UserMapper userMapper, UserSessionMapper sessionMapper,
                    SmsVerificationMapper smsMapper, UserSecurityAuditMapper auditMapper,
                    PasswordEncoder passwordEncoder, AccountService accountService, SmsGateway smsGateway) {
        this(userMapper, sessionMapper, smsMapper, auditMapper, passwordEncoder, accountService,
                smsGateway, DEFAULT_TEST_SECRET, new UserSecurityAuditService(auditMapper),
                new SmsCooldownService(smsMapper));
    }

    private UserAuthService(UserMapper userMapper, UserSessionMapper sessionMapper,
                            SmsVerificationMapper smsMapper, UserSecurityAuditMapper auditMapper,
                            PasswordEncoder passwordEncoder, AccountService accountService,
                            SmsGateway smsGateway, String jwtSecret, UserSecurityAuditService auditService,
                            SmsCooldownService cooldownService) {
        this.userMapper = userMapper;
        this.sessionMapper = sessionMapper;
        this.smsMapper = smsMapper;
        this.auditMapper = auditMapper;
        this.passwordEncoder = passwordEncoder;
        this.accountService = accountService;
        this.smsGateway = smsGateway;
        this.jwtSecret = jwtSecret;
        this.auditService = auditService;
        this.cooldownService = cooldownService;
    }

    /** Sends a five-minute one-time registration code with a sixty-second cooldown. */
    @Transactional
    public void sendSms(String phone, String purpose) {
        String normalizedPurpose = normalizePurpose(purpose);
        LocalDateTime now = LocalDateTime.now();
        SmsCooldownService.Reservation reservation = cooldownService.reserve(
                phone, normalizedPurpose, now, now.plusSeconds(60));
        if (reservation == null) {
            throw new LoginFailedException("验证码发送过于频繁");
        }
        try {
            String rawCode = smsGateway.sendCode(phone, normalizedPurpose);
            int inserted = smsMapper.insert(SmsVerification.builder().phone(phone).purpose(normalizedPurpose)
                    .codeHash(sha256(rawCode)).expiresAt(now.plusMinutes(5)).attemptCount(0).createTime(now).build());
            if (inserted != 1) throw new IllegalStateException("验证码记录保存失败");
        } catch (RuntimeException | Error ex) {
            cooldownService.release(reservation);
            throw ex;
        }
    }

    @Transactional
    public UserSessionVO register(UserRegisterDTO dto, AuthClientContext client) {
        if (userMapper.getByPhone(dto.getPhone()) != null) throw new LoginFailedException("该手机号已注册");
        consumeCode(dto.getPhone(), dto.getCode(), REGISTER_PURPOSE);
        User user = User.builder().phone(dto.getPhone()).name(dto.getName().trim())
                .password(passwordEncoder.encode(dto.getPassword())).createTime(LocalDateTime.now()).build();
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new LoginFailedException("该手机号已注册");
        }
        accountService.openUserAccount(user.getId(), 50_000L);
        audit(user.getId(), "REGISTER", client, null);
        return createSession(user, client.deviceId());
    }

    @Transactional
    public UserSessionVO login(UserPasswordLoginDTO dto, AuthClientContext client) {
        User user = userMapper.getByPhoneForUpdate(dto.getPhone());
        if (user == null) throw new LoginFailedException("手机号或密码错误");
        LocalDateTime now = LocalDateTime.now();
        UserSecurityAudit lock = auditService.latest(user.getId(), "LOGIN_LOCKED");
        if (lock != null && lock.getCreateTime() != null && lock.getCreateTime().plusMinutes(15).isAfter(now)) {
            throw new LoginFailedException("登录失败次数过多，请15分钟后重试");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            auditService.record(user.getId(), "LOGIN_FAILURE", client, null);
            if (auditService.countFailures(user.getId(), now.minusMinutes(15)) >= 5) {
                auditService.record(user.getId(), "LOGIN_LOCKED", client, "{\"minutes\":15}");
            }
            throw new LoginFailedException("手机号或密码错误");
        }
        audit(user.getId(), "LOGIN_SUCCESS", client, null);
        return createSession(user, client.deviceId());
    }

    /** Rotates an active refresh token. The old token becomes unusable immediately. */
    @Transactional
    public UserSessionVO refresh(String rawRefreshToken) {
        UserSession current = requireSessionOwner(rawRefreshToken);
        userMapper.getByIdForUpdate(current.getUserId());
        current = requireActiveSessionForUpdate(rawRefreshToken);
        if (sessionMapper.revoke(current.getId(), LocalDateTime.now()) != 1) {
            throw new LoginFailedException("刷新令牌已失效");
        }
        User user = userMapper.getById(current.getUserId());
        if (user == null) throw new LoginFailedException("用户不存在");
        return createSession(user, current.getDeviceId());
    }

    @Transactional
    public void logout(String rawRefreshToken, boolean allDevices) {
        UserSession current = requireSessionOwner(rawRefreshToken);
        userMapper.getByIdForUpdate(current.getUserId());
        LocalDateTime now = LocalDateTime.now();
        if (allDevices) sessionMapper.revokeAll(current.getUserId(), now);
        else if (sessionMapper.revokeUserDevice(current.getUserId(), current.getDeviceId(), now) == 0) {
            throw new LoginFailedException("刷新令牌已失效");
        }
    }

    /** 消费一次指定用途的短信验证码，供敏感资料操作复用相同校验规则。 */
    public void consumeVerificationCode(String phone, String rawCode, String purpose) {
        consumeCode(phone, rawCode, normalizePurpose(purpose));
    }

    /** 使用户全部刷新会话失效，敏感资料变更后强制重新登录。 */
    public void revokeAllSessions(Long userId) {
        sessionMapper.revokeAll(userId, LocalDateTime.now());
    }

    private void consumeCode(String phone, String rawCode, String purpose) {
        SmsVerification verification = smsMapper.findLatest(phone, purpose);
        if (verification == null) throw new LoginFailedException("验证码错误或已失效");
        LocalDateTime now = LocalDateTime.now();
        if (verification.getUsedAt() != null || verification.getExpiresAt() == null || !verification.getExpiresAt().isAfter(now)
                || !MessageDigest.isEqual(verification.getCodeHash().getBytes(StandardCharsets.UTF_8),
                sha256(rawCode).getBytes(StandardCharsets.UTF_8))) {
            throw new LoginFailedException("验证码错误或已失效");
        }
        if (smsMapper.markUsed(verification.getId(), now) != 1) throw new LoginFailedException("验证码已使用");
    }

    private UserSessionVO createSession(User user, String deviceId) {
        String rawRefresh = randomToken();
        LocalDateTime now = LocalDateTime.now();
        sessionMapper.insert(UserSession.builder().userId(user.getId()).refreshTokenHash(sha256(rawRefresh))
                .deviceId(deviceId).expiresAt(now.plusHours(REFRESH_TTL_HOURS)).createTime(now).build());
        String access = JwtUtil.createJWT(jwtSecret, ACCESS_TTL_MILLIS,
                Map.of(JwtClaimsConstant.USER_ID, user.getId()));
        UserSessionVO.AuthenticatedUser safeUser = new UserSessionVO.AuthenticatedUser(
                user.getId(), user.getName(), user.getPhone(), user.getAvatar());
        return new UserSessionVO(safeUser, access, rawRefresh);
    }

    private UserSession requireActiveSessionForUpdate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new LoginFailedException("刷新令牌不能为空");
        UserSession session = sessionMapper.findByRefreshTokenHashForUpdate(sha256(rawToken));
        LocalDateTime now = LocalDateTime.now();
        if (session == null || session.getRevokedAt() != null || session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(now)) throw new LoginFailedException("刷新令牌已失效");
        return session;
    }

    private UserSession requireSessionOwner(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new LoginFailedException("刷新令牌不能为空");
        UserSession session = sessionMapper.findByRefreshTokenHash(sha256(rawToken));
        if (session == null) throw new LoginFailedException("刷新令牌已失效");
        return session;
    }

    private void audit(Long userId, String type, AuthClientContext client, String detail) {
        auditMapper.insert(UserSecurityAudit.builder().userId(userId).eventType(type).detailJson(detail)
                .ipAddress(client.ipAddress()).userAgent(client.userAgent()).createTime(LocalDateTime.now()).build());
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String input) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String normalizePurpose(String purpose) {
        if (purpose == null || purpose.isBlank()) return REGISTER_PURPOSE;
        String normalized = purpose.trim().toLowerCase();
        if (!REGISTER_PURPOSE.equals(normalized)
                && !CHANGE_OLD_PHONE_PURPOSE.equals(normalized)
                && !CHANGE_NEW_PHONE_PURPOSE.equals(normalized)
                && !CHANGE_PASSWORD_PURPOSE.equals(normalized)) {
            throw new LoginFailedException("不支持的验证码用途");
        }
        return normalized;
    }
}
