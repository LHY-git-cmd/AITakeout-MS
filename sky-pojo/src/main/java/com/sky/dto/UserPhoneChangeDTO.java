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
@Schema(description = "用户换绑手机号数据传输对象")
public class UserPhoneChangeDTO implements Serializable {
    @NotBlank(message = "原手机号验证码不能为空")
    @Size(min = 6, max = 6, message = "原手机号验证码格式不正确")
    @Schema(description = "原手机号验证码")
    private String oldPhoneCode;

    @NotBlank(message = "新手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "新手机号格式不正确")
    @Schema(description = "新手机号")
    private String newPhone;

    @NotBlank(message = "新手机号验证码不能为空")
    @Size(min = 6, max = 6, message = "新手机号验证码格式不正确")
    @Schema(description = "新手机号验证码")
    private String newPhoneCode;
}