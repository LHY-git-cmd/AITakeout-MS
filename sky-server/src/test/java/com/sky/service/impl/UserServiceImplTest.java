package com.sky.service.impl;

import com.sky.dto.WebUserLoginDTO;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String PHONE = "13800138000";
    private static final String CODE = "123456";

    @Mock
    private WeChatProperties weChatProperties;

    @Mock
    private WebLoginProperties webLoginProperties;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void enableWebLogin() {
        when(webLoginProperties.isEnabled()).thenReturn(true);
    }

    @Test
    void shouldReturnExistingUserForValidWebLogin() {
        User existing = User.builder().id(7L).phone(PHONE).name("演示用户").build();
        when(webLoginProperties.getVerificationCode()).thenReturn(CODE);
        when(userMapper.getByPhone(PHONE)).thenReturn(existing);

        User result = userService.webLogin(loginDto(PHONE, CODE));

        assertSame(existing, result);
        verify(userMapper, never()).insert(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void shouldCreateUserForFirstWebLogin() {
        when(webLoginProperties.getVerificationCode()).thenReturn(CODE);
        when(userMapper.getByPhone(PHONE)).thenReturn(null);

        User result = userService.webLogin(loginDto(PHONE, CODE));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertSame(result, captor.getValue());
        assertEquals(PHONE, result.getPhone());
        assertEquals("演示用户", result.getName());
        assertNotNull(result.getCreateTime());
    }

    @Test
    void shouldRejectWrongVerificationCode() {
        when(webLoginProperties.getVerificationCode()).thenReturn(CODE);

        LoginFailedException exception = assertThrows(
                LoginFailedException.class,
                () -> userService.webLogin(loginDto(PHONE, "000000")));

        assertEquals("验证码错误", exception.getMessage());
        verify(userMapper, never()).getByPhone(PHONE);
    }

    @Test
    void shouldRejectWebLoginWhenDisabled() {
        when(webLoginProperties.isEnabled()).thenReturn(false);

        LoginFailedException exception = assertThrows(
                LoginFailedException.class,
                () -> userService.webLogin(loginDto(PHONE, CODE)));

        assertEquals("Web 演示登录未启用", exception.getMessage());
    }

    private WebUserLoginDTO loginDto(String phone, String code) {
        WebUserLoginDTO dto = new WebUserLoginDTO();
        dto.setPhone(phone);
        dto.setCode(code);
        return dto;
    }
}
