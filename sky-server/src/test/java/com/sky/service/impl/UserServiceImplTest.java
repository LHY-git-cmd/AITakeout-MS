package com.sky.service.impl;

import com.sky.constant.PasswordConstant;
import com.sky.dto.WebUserLoginDTO;
import com.sky.dto.WebUserRegisterDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WebLoginProperties;
import com.sky.properties.WeChatProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String PHONE = "13800138000";
    private static final String ENCODED_PASSWORD = "$2a$10$encoded";

    @Mock
    private WeChatProperties weChatProperties;

    @Mock
    private WebLoginProperties webLoginProperties;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void enableWebLogin() {
        when(webLoginProperties.isEnabled()).thenReturn(true);
    }

    @Test
    void shouldReturnExistingUserForCorrectPassword() {
        User existing = User.builder().id(7L).phone(PHONE).password(ENCODED_PASSWORD).build();
        when(userMapper.getByPhone(PHONE)).thenReturn(existing);
        when(passwordEncoder.matches(PasswordConstant.DEFAULT_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

        User result = userService.webLogin(loginDto(PasswordConstant.DEFAULT_PASSWORD));

        assertSame(existing, result);
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void shouldRejectWrongPassword() {
        User existing = User.builder().id(7L).phone(PHONE).password(ENCODED_PASSWORD).build();
        when(userMapper.getByPhone(PHONE)).thenReturn(existing);
        when(passwordEncoder.matches("wrong", ENCODED_PASSWORD)).thenReturn(false);

        LoginFailedException exception = assertThrows(LoginFailedException.class,
                () -> userService.webLogin(loginDto("wrong")));

        assertEquals("手机号或密码错误", exception.getMessage());
    }

    @Test
    void shouldRejectUnknownPhoneWithoutCreatingUser() {
        when(userMapper.getByPhone(PHONE)).thenReturn(null);

        LoginFailedException exception = assertThrows(LoginFailedException.class,
                () -> userService.webLogin(loginDto(PasswordConstant.DEFAULT_PASSWORD)));

        assertEquals("手机号或密码错误", exception.getMessage());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void shouldRegisterUserWithDefaultEncodedPassword() {
        when(userMapper.getByPhone(PHONE)).thenReturn(null);
        when(passwordEncoder.encode(PasswordConstant.DEFAULT_PASSWORD)).thenReturn(ENCODED_PASSWORD);

        User result = userService.webRegister(registerDto());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();
        assertSame(result, saved);
        assertEquals("测试用户", saved.getName());
        assertEquals(PHONE, saved.getPhone());
        assertEquals(ENCODED_PASSWORD, saved.getPassword());
        assertEquals("1", saved.getSex());
        assertNull(saved.getIdNumber());
        assertNull(saved.getAvatar());
        assertNotNull(saved.getCreateTime());
        verify(passwordEncoder).encode(PasswordConstant.DEFAULT_PASSWORD);
    }

    @Test
    void shouldRejectDuplicatePhone() {
        when(userMapper.getByPhone(PHONE)).thenReturn(User.builder().id(7L).phone(PHONE).build());

        LoginFailedException exception = assertThrows(LoginFailedException.class,
                () -> userService.webRegister(registerDto()));

        assertEquals("该手机号已注册", exception.getMessage());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void shouldRejectWebLoginAndRegistrationWhenDisabled() {
        when(webLoginProperties.isEnabled()).thenReturn(false);

        LoginFailedException loginException = assertThrows(LoginFailedException.class,
                () -> userService.webLogin(loginDto(PasswordConstant.DEFAULT_PASSWORD)));
        LoginFailedException registerException = assertThrows(LoginFailedException.class,
                () -> userService.webRegister(registerDto()));

        assertEquals("Web 演示登录未启用", loginException.getMessage());
        assertEquals("Web 演示登录未启用", registerException.getMessage());
    }

    private WebUserLoginDTO loginDto(String password) {
        WebUserLoginDTO dto = new WebUserLoginDTO();
        dto.setPhone(PHONE);
        dto.setPassword(password);
        return dto;
    }

    private WebUserRegisterDTO registerDto() {
        WebUserRegisterDTO dto = new WebUserRegisterDTO();
        dto.setName(" 测试用户 ");
        dto.setPhone(PHONE);
        dto.setSex("1");
        dto.setIdNumber("");
        dto.setAvatar(" ");
        return dto;
    }
}
