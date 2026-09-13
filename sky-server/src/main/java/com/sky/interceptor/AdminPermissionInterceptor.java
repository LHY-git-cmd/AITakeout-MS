package com.sky.interceptor;

import com.sky.annotation.RequireAdminPermission;
import com.sky.context.BaseContext;
import com.sky.enumeration.AdminRole;
import com.sky.service.security.AdminAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 对声明了管理端权限的控制器方法执行统一校验。 */
@Component
@RequiredArgsConstructor
public class AdminPermissionInterceptor implements HandlerInterceptor {

    private final AdminAuthorizationService authorizationService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequireAdminPermission required = AnnotatedElementUtils.findMergedAnnotation(
                method.getMethod(), RequireAdminPermission.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(
                    method.getBeanType(), RequireAdminPermission.class);
        }
        if (required == null) {
            return true;
        }
        authorizationService.require(
                AdminRole.fromDatabase(BaseContext.getCurrentRole()), required.value());
        return true;
    }
}
