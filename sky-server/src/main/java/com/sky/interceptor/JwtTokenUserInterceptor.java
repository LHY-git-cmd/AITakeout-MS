package com.sky.interceptor;

import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 用户端jwt令牌校验的拦截器
 * 拦截 /user/** 路径下的请求，校验jwt令牌并将用户id存入上下文
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtTokenUserInterceptor implements HandlerInterceptor {
    private final JwtProperties jwtProperties;

    /**
     * 校验jwt令牌
     *
     * @param request  当前HTTP请求
     * @param response 当前HTTP响应
     * @param handler  选定的处理器
     * @return 如果通过则返回true，否则返回false
     * @throws Exception 任何在执行过程中抛出的异常
     */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断当前拦截到的是Controller的方法还是其他资源
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        //1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getUserTokenName());

        //1.1、判断令牌是否为空
        if (token == null || token.isEmpty()) {
            log.warn("用户端jwt校验：请求头中未携带令牌");
            response.setStatus(401);
            return false;
        }

        //2、校验令牌
        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            Long userId = claims.get(JwtClaimsConstant.USER_ID, Long.class);
            log.info("当前用户id：{}", userId);
            BaseContext.setCurrentId(userId);
            //3、通过，放行
            return true;
        } catch (Exception ex) {
            log.error("用户端jwt校验失败", ex);
            response.setStatus(401);
            return false;
        }
    }

    /**
     * 请求完成后清理当前上下文的用户id，防止内存泄漏
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.removeCurrentId();
    }
}