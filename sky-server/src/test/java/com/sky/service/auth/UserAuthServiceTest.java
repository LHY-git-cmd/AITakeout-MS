package com.sky.service.auth;

import com.sky.entity.User;
import com.sky.entity.SmsVerification;
import com.sky.entity.UserSecurityAudit;
import com.sky.entity.UserSession;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.SmsVerificationMapper;
import com.sky.mapper.UserSecurityAuditMapper;
import com.sky.mapper.UserSessionMapper;
import com.sky.dto.UserRegisterDTO;
import com.sky.dto.UserPasswordLoginDTO;
import com.sky.service.account.AccountService;
import com.sky.auth.AuthClientContext;
import com.sky.vo.UserSessionVO;
import com.sky.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/** Formal phone account authentication contract tests. */
@ExtendWith(MockitoExtension.class)
class UserAuthServiceTest {
    @Mock UserMapper userMapper;
    @Mock UserSessionMapper sessionMapper;
    @Mock SmsVerificationMapper smsMapper;
    @Mock UserSecurityAuditMapper auditMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AccountService accountService;
    @Mock SmsGateway smsGateway;

    @Test
    void registerUsesChosenPasswordAndCreatesInitialAccount() {
        User user = User.builder().id(9L).phone("13800138000").build();
        when(userMapper.getByPhone("13800138000")).thenReturn(null);
        when(passwordEncoder.encode("StrongPass8")).thenReturn("encoded");
        when(smsMapper.findLatest("13800138000", "register")).thenReturn(SmsVerification.builder()
                .id(3L).codeHash(sha256("246810")).expiresAt(java.time.LocalDateTime.now().plusMinutes(5)).build());
        when(smsMapper.markUsed(eq(3L), any())).thenReturn(1);
        UserAuthService service = new UserAuthService(userMapper, sessionMapper, smsMapper, auditMapper,
                passwordEncoder, accountService, smsGateway);
        org.mockito.Mockito.doAnswer(inv -> { ((User) inv.getArgument(0)).setId(9L); return null; })
                .when(userMapper).insert(org.mockito.ArgumentMatchers.any(User.class));

        UserSessionVO result = service.register(new UserRegisterDTO("13800138000", "246810", "StrongPass8", "小苍"),
                new AuthClientContext("127.0.0.1", "test", "device-1"));

        assertThat(result.accessToken()).isNotBlank();
        verify(passwordEncoder).encode("StrongPass8");
        verify(accountService).openUserAccount(result.user().id(), 50_000L);
    }

    @Test
    void accessTokenAndRefreshTokenAreIssuedAsSeparateValues() {
        User user = User.builder().id(1L).phone("13800138000").password("encoded").build();
        when(userMapper.getByPhoneForUpdate(user.getPhone())).thenReturn(user);
        when(passwordEncoder.matches("StrongPass8", "encoded")).thenReturn(true);
        UserAuthService service = new UserAuthService(userMapper, sessionMapper, smsMapper, auditMapper,
                passwordEncoder, accountService, smsGateway);
        UserSessionVO result = service.login(new UserPasswordLoginDTO(user.getPhone(), "StrongPass8"),
                new AuthClientContext("127.0.0.1", "test", "device-1"));
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
    }

    @Test
    void expiredVerificationCodeCannotRegister() {
        when(userMapper.getByPhone("13800138000")).thenReturn(null);
        when(smsMapper.findLatest("13800138000", "register")).thenReturn(SmsVerification.builder()
                .id(2L).codeHash(sha256("246810")).expiresAt(java.time.LocalDateTime.now().minusSeconds(1)).build());
        assertThatThrownBy(() -> service().register(register(), client()))
                .isInstanceOf(LoginFailedException.class).hasMessage("验证码错误或已失效");
        verify(userMapper, never()).insert(any());
    }

    @Test
    void usedVerificationCodeCannotRegisterAgain() {
        when(userMapper.getByPhone("13800138000")).thenReturn(null);
        when(smsMapper.findLatest("13800138000", "register")).thenReturn(SmsVerification.builder()
                .id(2L).codeHash(sha256("246810")).expiresAt(java.time.LocalDateTime.now().plusMinutes(1))
                .usedAt(java.time.LocalDateTime.now()).build());
        assertThatThrownBy(() -> service().register(register(), client()))
                .isInstanceOf(LoginFailedException.class);
    }

    @Test
    void duplicatePhoneIsRejectedBeforeConsumingCode() {
        when(userMapper.getByPhone("13800138000")).thenReturn(User.builder().id(1L).build());
        assertThatThrownBy(() -> service().register(register(), client()))
                .isInstanceOf(LoginFailedException.class).hasMessage("该手机号已注册");
        verify(smsMapper, never()).markUsed(any(), any());
    }

    @Test
    void fifthPasswordFailurePersistsFifteenMinuteLock() {
        User user = User.builder().id(1L).phone("13800138000").password("encoded").build();
        when(userMapper.getByPhoneForUpdate(user.getPhone())).thenReturn(user);
        when(passwordEncoder.matches("wrong-pass", "encoded")).thenReturn(false);
        when(auditMapper.countLoginFailures(eq(1L), any())).thenReturn(5);

        assertThatThrownBy(() -> service().login(new UserPasswordLoginDTO(user.getPhone(), "wrong-pass"), client()))
                .isInstanceOf(LoginFailedException.class);
        org.mockito.ArgumentCaptor<UserSecurityAudit> captor = org.mockito.ArgumentCaptor.forClass(UserSecurityAudit.class);
        verify(auditMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(UserSecurityAudit::getEventType)
                .containsExactly("LOGIN_FAILURE", "LOGIN_LOCKED");
    }

    @Test
    void activeLockRejectsLoginWithoutCheckingPassword() {
        User user = User.builder().id(1L).phone("13800138000").password("encoded").build();
        when(userMapper.getByPhoneForUpdate(user.getPhone())).thenReturn(user);
        when(auditMapper.findLatest(1L, "LOGIN_LOCKED")).thenReturn(UserSecurityAudit.builder()
                .createTime(java.time.LocalDateTime.now().minusMinutes(14)).build());
        assertThatThrownBy(() -> service().login(new UserPasswordLoginDTO(user.getPhone(), "StrongPass8"), client()))
                .isInstanceOf(LoginFailedException.class).hasMessageContaining("15分钟");
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void refreshRotatesTokenAndStoresOnlyDigests() {
        UserSession old = UserSession.builder().id(4L).userId(1L).deviceId("device-1")
                .expiresAt(java.time.LocalDateTime.now().plusDays(1)).build();
        when(sessionMapper.findByRefreshTokenHashForUpdate(sha256("old-raw"))).thenReturn(old);
        when(sessionMapper.findByRefreshTokenHash(sha256("old-raw"))).thenReturn(old);
        when(userMapper.getByIdForUpdate(1L)).thenReturn(User.builder().id(1L).build());
        when(sessionMapper.revoke(eq(4L), any())).thenReturn(1);
        when(userMapper.getById(1L)).thenReturn(User.builder().id(1L).phone("13800138000").build());

        UserSessionVO result = service().refresh("old-raw");

        org.mockito.ArgumentCaptor<UserSession> captor = org.mockito.ArgumentCaptor.forClass(UserSession.class);
        verify(sessionMapper).insert(captor.capture());
        assertThat(captor.getValue().getRefreshTokenHash()).isEqualTo(sha256(result.refreshToken()));
        assertThat(captor.getValue().getRefreshTokenHash()).doesNotContain(result.refreshToken());
    }

    @Test
    void logoutAllRevokesEverySessionForTokenOwner() {
        UserSession current = UserSession.builder().id(4L).userId(1L)
                .expiresAt(java.time.LocalDateTime.now().plusDays(1)).build();
        when(sessionMapper.findByRefreshTokenHash(sha256("raw"))).thenReturn(current);
        when(userMapper.getByIdForUpdate(1L)).thenReturn(User.builder().id(1L).build());
        when(sessionMapper.revokeAll(eq(1L), any())).thenReturn(1);
        service().logout("raw", true);
        verify(sessionMapper).revokeAll(eq(1L), any());
        verify(sessionMapper, never()).revoke(eq(4L), any());
    }

    @Test
    void currentDeviceLogoutRevokesEveryRotatedSessionForThatDevice() {
        UserSession current = UserSession.builder().id(4L).userId(1L).deviceId("device-1")
                .expiresAt(java.time.LocalDateTime.now().plusDays(1)).build();
        when(sessionMapper.findByRefreshTokenHash(sha256("raw"))).thenReturn(current);
        when(userMapper.getByIdForUpdate(1L)).thenReturn(User.builder().id(1L).build());
        when(sessionMapper.revokeUserDevice(eq(1L), eq("device-1"), any())).thenReturn(2);

        service().logout("raw", false);

        verify(sessionMapper).revokeUserDevice(eq(1L), eq("device-1"), any());
    }

    @Test
    void gatewayFailureReleasesOnlyItsExactCooldownReservation() {
        when(smsMapper.insertCooldown(eq("13800138000"), eq("register"), any(), any())).thenReturn(1);
        doThrow(new IllegalStateException("gateway unavailable"))
                .when(smsGateway).sendCode("13800138000", "register");

        assertThatThrownBy(() -> service().sendSms("13800138000", "register"))
                .isInstanceOf(IllegalStateException.class);

        org.mockito.ArgumentCaptor<String> reservation = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(smsMapper).insertCooldown(eq("13800138000"), eq("register"), reservation.capture(), any());
        verify(smsMapper).releaseCooldown("13800138000", "register", reservation.getValue());
    }

    @Test
    void verificationPersistenceFailureReleasesExactCooldownReservation() {
        when(smsMapper.insertCooldown(eq("13800138000"), eq("register"), any(), any())).thenReturn(1);
        when(smsGateway.sendCode("13800138000", "register")).thenReturn("246810");
        doThrow(new IllegalStateException("database unavailable")).when(smsMapper).insert(any());

        assertThatThrownBy(() -> service().sendSms("13800138000", "register"))
                .isInstanceOf(IllegalStateException.class);

        org.mockito.ArgumentCaptor<String> reservation = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(smsMapper).insertCooldown(eq("13800138000"), eq("register"), reservation.capture(), any());
        verify(smsMapper).releaseCooldown("13800138000", "register", reservation.getValue());
    }

    private UserAuthService service() {
        return new UserAuthService(userMapper, sessionMapper, smsMapper, auditMapper,
                passwordEncoder, accountService, smsGateway);
    }

    private UserRegisterDTO register() {
        return new UserRegisterDTO("13800138000", "246810", "StrongPass8", "小苍");
    }

    private AuthClientContext client() {
        return new AuthClientContext("127.0.0.1", "test", "device-1");
    }

    private static String sha256(String input) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new AssertionError(ex);
        }
    }
}
