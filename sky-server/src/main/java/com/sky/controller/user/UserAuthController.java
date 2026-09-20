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
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Formal phone account endpoints with cookie-bound refresh tokens. */
@Validated
@RestController
@RequestMapping("/user/auth")
@RequiredArgsConstructor
public class UserAuthController {
    static final String REFRESH_COOKIE = "refresh_token";
    private final UserAuthService authService;

    @PostMapping("/sms/send")
    public Result<Void> sendSms(@Valid @RequestBody SmsRequest request) {
        authService.sendSms(request.phone(), request.purpose());
        return Result.success();
    }

    @PostMapping("/register")
    public Result<UserSessionVO> register(@Valid @RequestBody UserRegisterDTO dto,
                                          HttpServletRequest request, HttpServletResponse response,
                                          @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        UserSessionVO session = authService.register(dto, client(request, deviceId));
        setRefreshCookie(response, session.refreshToken(), Duration.ofDays(30));
        return Result.success(session);
    }

    @PostMapping("/login")
    public Result<UserSessionVO> login(@Valid @RequestBody UserPasswordLoginDTO dto,
                                       HttpServletRequest request, HttpServletResponse response,
                                       @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        UserSessionVO session = authService.login(dto, client(request, deviceId));
        setRefreshCookie(response, session.refreshToken(), Duration.ofDays(30));
        return Result.success(session);
    }

    @PostMapping("/refresh")
    public Result<UserSessionVO> refresh(@CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken,
                                         HttpServletResponse response) {
        UserSessionVO session = authService.refresh(refreshToken);
        setRefreshCookie(response, session.refreshToken(), Duration.ofDays(30));
        return Result.success(session);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@CookieValue(REFRESH_COOKIE) String refreshToken, HttpServletResponse response) {
        authService.logout(refreshToken, false);
        setRefreshCookie(response, "", Duration.ZERO);
        return Result.success();
    }

    @PostMapping("/logout-all")
    public Result<Void> logoutAll(@CookieValue(REFRESH_COOKIE) String refreshToken, HttpServletResponse response) {
        authService.logout(refreshToken, true);
        setRefreshCookie(response, "", Duration.ZERO);
        return Result.success();
    }

    private static AuthClientContext client(HttpServletRequest request, String deviceId) {
        return new AuthClientContext(request.getRemoteAddr(), request.getHeader("User-Agent"), deviceId);
    }

    private static void setRefreshCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true).secure(true)
                .sameSite("Lax").path("/user/auth").maxAge(maxAge).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public record SmsRequest(
            @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确") String phone,
            String purpose) { }
}
