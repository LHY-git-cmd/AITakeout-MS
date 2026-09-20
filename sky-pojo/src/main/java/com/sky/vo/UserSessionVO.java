package com.sky.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

@Schema(description = "用户会话视图对象")
public record UserSessionVO(
        @Schema(description = "认证用户信息") AuthenticatedUser user,
        @Schema(description = "访问令牌") String accessToken,
        @JsonIgnore String refreshToken) implements Serializable {

    @Schema(description = "认证用户视图对象")
    public record AuthenticatedUser(
            @Schema(description = "用户ID") Long id,
            @Schema(description = "姓名") String name,
            @Schema(description = "手机号") String phone,
            @Schema(description = "头像") String avatar) implements Serializable { }
}