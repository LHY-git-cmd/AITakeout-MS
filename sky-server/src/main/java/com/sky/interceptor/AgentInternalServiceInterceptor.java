package com.sky.interceptor;

import com.sky.properties.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 校验Python Agent访问Java内部接口时使用的服务间凭证。 */
@Component
@RequiredArgsConstructor
public class AgentInternalServiceInterceptor implements HandlerInterceptor {

    public static final String TOKEN_HEADER = "X-Agent-Service-Token";
    private final AgentProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String expected = properties.getInternalServiceToken();
        if (expected == null || expected.length() < 32) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "Agent内部服务凭证未配置");
            return false;
        }
        String actual = request.getHeader(TOKEN_HEADER);
        if (actual == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Agent内部服务认证失败");
            return false;
        }
        return true;
    }
}
