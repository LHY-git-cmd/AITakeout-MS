package com.sky.service.profile;

import com.sky.auth.AuthClientContext;
import com.sky.dto.UserPasswordChangeDTO;
import com.sky.dto.UserPhoneChangeDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.service.auth.UserAuthService;
import com.sky.service.auth.UserSecurityAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 用户资料与敏感安全设置服务测试。 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {
    @Mock UserMapper userMapper;
    @Mock UserAuthService authService;
    @Mock UserSecurityAuditService auditService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AvatarStorage avatarStorage;

    private UserProfileService service;
    private final AuthClientContext client = new AuthClientContext("127.0.0.1", "test", "device-1");

    @BeforeEach
    void setUp() {
        service = new UserProfileService(userMapper, authService, auditService, passwordEncoder, avatarStorage);
    }

    @Test
    void avatarUsesDecodedContentAndRandomUserObjectPath() throws Exception {
        when(userMapper.getById(7L))
                .thenReturn(User.builder().id(7L).name("小苍").build())
                .thenReturn(User.builder().id(7L).name("小苍").avatar("https://cdn.example/avatar.png").build());
        when(avatarStorage.upload(any(), any())).thenReturn("https://cdn.example/avatar.png");
        when(userMapper.updateAvatar(7L, "https://cdn.example/avatar.png")).thenReturn(1);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.txt", "text/plain", png());

        var result = service.updateAvatar(7L, file, client);

        var path = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(avatarStorage).upload(any(), path.capture());
        assertThat(path.getValue()).matches("user-avatar/7/[0-9a-f-]+\\.png");
        assertThat(result.getAvatar()).isEqualTo("https://cdn.example/avatar.png");
    }

    @Test
    void nonImageContentCannotBeUploadedEvenWithImageMimeType() {
        when(userMapper.getById(7L)).thenReturn(User.builder().id(7L).build());
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png", "not-image".getBytes());

        assertThatThrownBy(() -> service.updateAvatar(7L, file, client))
                .isInstanceOf(LoginFailedException.class).hasMessageContaining("支持");
        verify(avatarStorage, never()).upload(any(), any());
    }

    @Test
    void phoneChangeConsumesBothCodesAndRevokesEverySession() {
        when(userMapper.getByIdForUpdate(7L)).thenReturn(User.builder().id(7L).phone("13800138000").build());
        when(userMapper.getByPhone("13900139000")).thenReturn(null);
        when(userMapper.updatePhone(7L, "13900139000")).thenReturn(1);

        service.changePhone(7L, new UserPhoneChangeDTO("111111", "13900139000", "222222"), client);

        verify(authService).consumeVerificationCode("13800138000", "111111",
                UserAuthService.CHANGE_OLD_PHONE_PURPOSE);
        verify(authService).consumeVerificationCode("13900139000", "222222",
                UserAuthService.CHANGE_NEW_PHONE_PURPOSE);
        verify(authService).revokeAllSessions(7L);
    }

    @Test
    void passwordCanUseCurrentPasswordAndRevokesEverySession() {
        when(userMapper.getByIdForUpdate(7L)).thenReturn(User.builder().id(7L).password("old-hash").build());
        when(passwordEncoder.matches("OldPass88", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPass99")).thenReturn("new-hash");
        when(userMapper.updatePassword(7L, "new-hash")).thenReturn(1);

        service.changePassword(7L, new UserPasswordChangeDTO("OldPass88", null, "NewPass99"), client);

        verify(authService, never()).consumeVerificationCode(any(), any(), any());
        verify(authService).revokeAllSessions(7L);
        verify(userMapper).updatePassword(7L, "new-hash");
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
