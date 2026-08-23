package com.sky.controller.user;

import com.sky.constant.JwtClaimsConstant;
import com.sky.dto.WebUserLoginDTO;
import com.sky.entity.User;
import com.sky.properties.JwtProperties;
import com.sky.result.Result;
import com.sky.service.UserService;
import com.sky.utils.JwtUtil;
import com.sky.vo.UserLoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Web端演示登录控制器
 * 仅在dev环境激活，提供基于手机号的演示登录接口
 */
@RestController
@RequestMapping("/user/user")
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Web 演示登录接口")
public class DevWebLoginController {

    private final UserService userService;
    private final JwtProperties jwtProperties;

    /**
     * Web端手机号演示登录
     *
     * @param webUserLoginDTO 登录信息（含手机号）
     * @return 登录响应（含token）
     */
    @PostMapping("/login/web")
    @Operation(summary = "Web 端手机号演示登录")
    public Result<UserLoginVO> login(@Valid @RequestBody WebUserLoginDTO webUserLoginDTO) {
        // 日志脱敏，记录手机号前三位与后四位
        String maskedPhone = webUserLoginDTO == null ? null : maskPhone(webUserLoginDTO.getPhone());
        log.info("Web 演示登录：{}", maskedPhone);

        // Web端手机号登录
        User user = userService.webLogin(webUserLoginDTO);

        // 为登录用户生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims);

        return Result.success(UserLoginVO.builder()
                .id(user.getId())
                .name(user.getName())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .token(token)
                .build());
    }

    /**
     * 手机号脱敏处理
     *
     * @param phone 原始手机号
     * @return 脱敏后的手机号
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}