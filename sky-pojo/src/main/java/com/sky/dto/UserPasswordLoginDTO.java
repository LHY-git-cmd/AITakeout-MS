package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户密码登录数据传输对象")
public class UserPasswordLoginDTO implements Serializable {
    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{9}$")
    @Schema(description = "手机号")
    private String phone;
    @NotBlank
    @Schema(description = "密码")
    private String password;
}