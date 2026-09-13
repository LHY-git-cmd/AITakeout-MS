package com.sky.interceptor;

import com.sky.context.BaseContext;
import com.sky.properties.JwtProperties;
import com.sky.service.security.AdminAuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class JwtInterceptorContextTest {

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void adminInterceptorShouldClearRequestContext() {
        BaseContext.setCurrentId(1L);
        BaseContext.setCurrentRole("ADMIN");
        new JwtTokenAdminInterceptor(mock(JwtProperties.class), mock(AdminAuthorizationService.class))
                .afterCompletion(mock(jakarta.servlet.http.HttpServletRequest.class),
                mock(jakarta.servlet.http.HttpServletResponse.class), new Object(), null);
        assertNull(BaseContext.getCurrentId());
        assertNull(BaseContext.getCurrentRole());
    }

    @Test
    void userInterceptorShouldClearRequestContext() {
        BaseContext.setCurrentId(2L);
        new JwtTokenUserInterceptor(mock(JwtProperties.class)).afterCompletion(mock(jakarta.servlet.http.HttpServletRequest.class),
                mock(jakarta.servlet.http.HttpServletResponse.class), new Object(), null);
        assertNull(BaseContext.getCurrentId());
    }
}
