package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户注册数据传输对象")
public class UserRegisterDTO implements Serializable {
    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{9}$")
    @Schema(description = "手机号")
    private String phone;
    @NotBlank
    @Size(min = 6, max = 6)
    @Schema(description = "验证码")
    private String code;
    @NotBlank
    @Size(min = 8, max = 72)
    @Schema(description = "密码")
    private String password;
    @NotBlank
    @Size(max = 32)
    @Schema(description = "昵称")
    private String name;
}