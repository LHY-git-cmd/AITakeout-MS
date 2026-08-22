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
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user/user")
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Web 演示登录接口")
public class DevWebLoginController {

    private final UserService userService;
    private final JwtProperties jwtProperties;

    @PostMapping("/login/web")
    @Operation(summary = "Web 端手机号演示登录")
    public Result<UserLoginVO> login(@RequestBody WebUserLoginDTO webUserLoginDTO) {
        String maskedPhone = webUserLoginDTO == null ? null : maskPhone(webUserLoginDTO.getPhone());
        log.info("Web 演示登录：{}", maskedPhone);

        User user = userService.webLogin(webUserLoginDTO);
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

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
