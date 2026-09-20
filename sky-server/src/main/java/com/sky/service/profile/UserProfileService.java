package com.sky.service.profile;

import com.sky.auth.AuthClientContext;
import com.sky.dto.UserPasswordChangeDTO;
import com.sky.dto.UserPhoneChangeDTO;
import com.sky.dto.UserProfileUpdateDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.service.auth.UserAuthService;
import com.sky.service.auth.UserSecurityAuditService;
import com.sky.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** 用户资料与安全设置服务。 */
@Service
@RequiredArgsConstructor
public class UserProfileService {
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final int MAX_AVATAR_EDGE = 4096;
    private static final Set<String> AVATAR_FORMATS = Set.of("jpeg", "png", "webp");

    private final UserMapper userMapper;
    private final UserAuthService authService;
    private final UserSecurityAuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final AvatarStorage avatarStorage;

    @Transactional
    public UserProfileVO updateProfile(Long userId, UserProfileUpdateDTO dto, AuthClientContext client) {
        requireUser(userId);
        String name = dto.getName().trim();
        if (name.isEmpty()) throw new LoginFailedException("昵称不能为空");
        if (userMapper.updateName(userId, name) != 1) throw new LoginFailedException("用户不存在");
        auditService.record(userId, "PROFILE_UPDATE", client, null);
        return profile(userId);
    }

    @Transactional
    public UserProfileVO updateAvatar(Long userId, MultipartFile file, AuthClientContext client) {
        requireUser(userId);
        AvatarFile avatar = validateAvatar(file);
        String objectName = "user-avatar/" + userId + "/" + UUID.randomUUID() + "." + avatar.extension();
        String url = avatarStorage.upload(avatar.content(), objectName);
        if (url == null || url.isBlank()) throw new LoginFailedException("头像上传失败，请稍后重试");
        if (userMapper.updateAvatar(userId, url) != 1) throw new LoginFailedException("用户不存在");
        auditService.record(userId, "AVATAR_UPDATE", client, null);
        return profile(userId);
    }

    @Transactional
    public void changePhone(Long userId, UserPhoneChangeDTO dto, AuthClientContext client) {
        User user = requireUserForUpdate(userId);
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new LoginFailedException("当前账号尚未绑定手机号");
        }
        if (user.getPhone().equals(dto.getNewPhone())) throw new LoginFailedException("新手机号不能与原手机号相同");
        User occupied = userMapper.getByPhone(dto.getNewPhone());
        if (occupied != null && !userId.equals(occupied.getId())) throw new LoginFailedException("该手机号已注册");

        authService.consumeVerificationCode(user.getPhone(), dto.getOldPhoneCode(),
                UserAuthService.CHANGE_OLD_PHONE_PURPOSE);
        authService.consumeVerificationCode(dto.getNewPhone(), dto.getNewPhoneCode(),
                UserAuthService.CHANGE_NEW_PHONE_PURPOSE);
        try {
            if (userMapper.updatePhone(userId, dto.getNewPhone()) != 1) throw new LoginFailedException("用户不存在");
        } catch (DuplicateKeyException ex) {
            throw new LoginFailedException("该手机号已注册");
        }
        authService.revokeAllSessions(userId);
        auditService.record(userId, "PHONE_CHANGE", client, null);
    }

    @Transactional
    public void changePassword(Long userId, UserPasswordChangeDTO dto, AuthClientContext client) {
        User user = requireUserForUpdate(userId);
        boolean currentPasswordValid = dto.getCurrentPassword() != null && !dto.getCurrentPassword().isBlank()
                && user.getPassword() != null
                && passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword());
        if (!currentPasswordValid) {
            if (dto.getCode() == null || dto.getCode().isBlank()) {
                throw new LoginFailedException("当前密码错误");
            }
            if (user.getPhone() == null || user.getPhone().isBlank()) {
                throw new LoginFailedException("当前账号尚未绑定手机号");
            }
            authService.consumeVerificationCode(user.getPhone(), dto.getCode(),
                    UserAuthService.CHANGE_PASSWORD_PURPOSE);
        }
        if (userMapper.updatePassword(userId, passwordEncoder.encode(dto.getNewPassword())) != 1) {
            throw new LoginFailedException("用户不存在");
        }
        authService.revokeAllSessions(userId);
        auditService.record(userId, "PASSWORD_CHANGE", client, null);
    }

    private UserProfileVO profile(Long userId) {
        User user = requireUser(userId);
        return UserProfileVO.builder().id(user.getId()).name(user.getName())
                .phone(user.getPhone()).avatar(user.getAvatar()).build();
    }

    private User requireUser(Long userId) {
        User user = userMapper.getById(userId);
        if (user == null) throw new LoginFailedException("用户不存在");
        return user;
    }

    private User requireUserForUpdate(Long userId) {
        User user = userMapper.getByIdForUpdate(userId);
        if (user == null) throw new LoginFailedException("用户不存在");
        return user;
    }

    private AvatarFile validateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new LoginFailedException("请选择头像图片");
        if (file.getSize() > MAX_AVATAR_BYTES) throw new LoginFailedException("头像不能超过2 MiB");
        try {
            byte[] content = file.getBytes();
            String format = imageFormat(content);
            if (!AVATAR_FORMATS.contains(format)) throw new LoginFailedException("头像仅支持 JPEG、PNG 或 WebP");
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) throw new LoginFailedException("头像文件内容无效");
            if (image.getWidth() > MAX_AVATAR_EDGE || image.getHeight() > MAX_AVATAR_EDGE) {
                throw new LoginFailedException("头像最长边不能超过4096像素");
            }
            return new AvatarFile(content, "jpeg".equals(format) ? "jpg" : format);
        } catch (IOException ex) {
            throw new LoginFailedException("头像文件读取失败");
        }
    }

    private String imageFormat(byte[] content) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) return "";
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return "";
            ImageReader reader = readers.next();
            try {
                return reader.getFormatName().toLowerCase(Locale.ROOT);
            } finally {
                reader.dispose();
            }
        }
    }

    private record AvatarFile(byte[] content, String extension) { }
}
