package com.sky.controller.user;

import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.properties.JwtProperties;
import com.sky.result.Result;
import com.sky.service.UserService;
import com.sky.utils.JwtUtil;
import com.sky.vo.UserLoginVO;
import com.sky.vo.UserProfileVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户管理控制器（用户端）
 * 提供微信登录、用户信息查询等功能
 */
@RestController
@RequestMapping("/user/user")
@Slf4j
@Tag(name = "用户相关接口")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 微信登录
     * 基于微信openid实现自动注册和登录，成功后生成JWT令牌
     *
     * @param userLoginDTO 登录信息（含微信code）
     * @return 登录响应（含token）
     */
    @PostMapping("/login")
    @Operation(summary = "微信登录")
    public Result<UserLoginVO> login(@Valid @RequestBody UserLoginDTO userLoginDTO) {
        log.info("发起微信登录");

        // 微信登录
        User user = userService.wxLogin(userLoginDTO);

        // 为微信用户生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims);

        UserLoginVO userLoginVO = UserLoginVO.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .name(user.getName())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .token(token)
                .build();

        return Result.success(userLoginVO);
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 用户信息
     */
    @GetMapping("/profile")
    @Operation(summary = "获取当前用户信息")
    public Result<UserProfileVO> profile() {
        User user = userService.getById(BaseContext.getCurrentId());
        return Result.success(UserProfileVO.builder()
                .id(user.getId())
                .name(user.getName())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .build());
    }
}