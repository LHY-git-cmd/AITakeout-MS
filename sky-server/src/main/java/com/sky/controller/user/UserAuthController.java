package com.sky.controller.user;

import com.sky.auth.AuthClientContext;
import com.sky.dto.UserPasswordLoginDTO;
import com.sky.dto.UserRegisterDTO;
import com.sky.result.Result;
import com.sky.service.auth.UserAuthService;
import com.sky.vo.UserSessionVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** 用户正式手机号账号认证接口，使用Cookie绑定Refresh Token实现会话管理。 */
@Validated
@RestController
@RequestMapping("/user/auth")
@RequiredArgsConstructor
public class UserAuthController {
    static final String REFRESH_COOKIE = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/user/auth";
    private static final Duration REFRESH_COOKIE_TTL = Duration.ofHours(2);
    private final UserAuthService authService;
    @Value("${sky.auth.refresh-cookie-secure:true}")
    private boolean refreshCookieSecure;

    /**
     * 发送短信验证码（用于注册或登录）
     *
     * @param request 包含手机号和验证码用途的请求体
     * @return 操作结果
     */
    @PostMapping("/sms/send")
    public Result<Void> sendSms(@Valid @RequestBody SmsRequest request) {
        authService.sendSms(request.phone(), request.purpose());
        return Result.success();
    }

    /**
     * 用户注册，成功后返回会话信息并通过Cookie设置Refresh Token
     *
     * @param dto       注册信息（手机号、密码、短信验证码等）
     * @param request   HTTP请求（用于获取客户端IP等信息）
     * @param response  HTTP响应（用于写入Refresh Token Cookie）
     * @param deviceId  设备ID（可选，用于多设备会话管理）
     * @return 用户会话信息（含AccessToken）
     */
    @PostMapping("/register")
    public Result<UserSessionVO> register(@Valid @RequestBody UserRegisterDTO dto,
                                          HttpServletRequest request, HttpServletResponse response,
                                          @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        UserSessionVO session = authService.register(dto, client(request, deviceId));
        setRefreshCookie(response, session.refreshToken(), REFRESH_COOKIE_TTL);
        return Result.success(session);
    }

    /**
     * 用户密码登录，成功后返回会话信息并通过Cookie设置Refresh Token
     *
     * @param dto       登录信息（手机号+密码或手机号+短信验证码）
     * @param request   HTTP请求（用于获取客户端IP等信息）
     * @param response  HTTP响应（用于写入Refresh Token Cookie）
     * @param deviceId  设备ID（可选，用于多设备会话管理）
     * @return 用户会话信息（含AccessToken）
     */
    @PostMapping("/login")
    public Result<UserSessionVO> login(@Valid @RequestBody UserPasswordLoginDTO dto,
                                       HttpServletRequest request, HttpServletResponse response,
                                       @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        UserSessionVO session = authService.login(dto, client(request, deviceId));
        setRefreshCookie(response, session.refreshToken(), REFRESH_COOKIE_TTL);
        return Result.success(session);
    }

    /**
     * 使用Cookie中的Refresh Token刷新会话，获取新的AccessToken
     *
     * @param refreshToken 从Cookie中读取的Refresh Token
     * @param response     HTTP响应（用于更新Cookie中的Refresh Token）
     * @return 新的用户会话信息
     */
    @PostMapping("/refresh")
    public Result<UserSessionVO> refresh(@CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
                                         HttpServletResponse response) {
        UserSessionVO session = authService.refresh(refreshToken);
        setRefreshCookie(response, session.refreshToken(), REFRESH_COOKIE_TTL);
        return Result.success(session);
    }

    /**
     * 当前设备登出（使当前Refresh Token失效）
     *
     * @param refreshToken 从Cookie中读取的Refresh Token
     * @param response     HTTP响应（用于清除Cookie）
     * @return 操作结果
     */
    @PostMapping("/logout")
    public Result<Void> logout(@CookieValue(REFRESH_COOKIE) String refreshToken, HttpServletResponse response) {
        authService.logout(refreshToken, false);
        setRefreshCookie(response, "", Duration.ZERO);
        return Result.success();
    }

    /**
     * 全部设备登出（使该用户所有Refresh Token失效，需重新登录）
     *
     * @param refreshToken 从Cookie中读取的Refresh Token
     * @param response     HTTP响应（用于清除Cookie）
     * @return 操作结果
     */
    @PostMapping("/logout-all")
    public Result<Void> logoutAll(@CookieValue(REFRESH_COOKIE) String refreshToken, HttpServletResponse response) {
        authService.logout(refreshToken, true);
        setRefreshCookie(response, "", Duration.ZERO);
        return Result.success();
    }

    /**
     * 构建客户端上下文（IP、User-Agent、设备ID），用于安全审计和多设备管理
     *
     * @param request  HTTP请求
     * @param deviceId 设备ID（可选）
     * @return 客户端上下文
     */
    private static AuthClientContext client(HttpServletRequest request, String deviceId) {
        return new AuthClientContext(request.getRemoteAddr(), request.getHeader("User-Agent"), deviceId);
    }

    /**
     * 将Refresh Token写入HttpOnly Secure Cookie
     *
     * @param response HTTP响应
     * @param value    Refresh Token值（登出时为空字符串）
     * @param maxAge   Cookie有效期
     */
    private void setRefreshCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true).secure(refreshCookieSecure)
                .sameSite("Lax").path(REFRESH_COOKIE_PATH).maxAge(maxAge).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** 短信请求数据传输对象。 */
    public record SmsRequest(
            @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确") String phone,
            String purpose) { }
}