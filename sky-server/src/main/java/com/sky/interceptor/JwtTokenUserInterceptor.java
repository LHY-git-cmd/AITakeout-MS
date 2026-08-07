package com.sky.interceptor;

import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    /**
     *校验jwt 
     * @param request current HTTP request
     * @param response current HTTP response
     * @param handler chosen handler to execute, for type and/or instance evaluation
     * @return
     * @throws Exception
     */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        //1，从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getUserTokenName());

        //1.1、判断令牌是否为空
        if (token == null || token.isEmpty()) {
            log.warn("用户端jwt校验：请求头中未携带令牌");
            response.setStatus(401);
            return false;
        }

        //2、校验令牌
        try {
            log.info("用户端jwt校验:{}", token);
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            Long userId = claims.get(JwtClaimsConstant.USER_ID, Long.class);
            log.info("当前用户id：{}", userId);
            BaseContext.setCurrentId(userId);
            //通过放行
            return true;
        } catch (Exception ex) {
            log.error("用户端jwt校验失败", ex);
            response.setStatus(401);
            return false;
        }
    }
}