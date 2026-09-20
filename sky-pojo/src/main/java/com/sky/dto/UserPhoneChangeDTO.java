package com.sky.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 用户换绑手机号请求，需要同时验证原手机号和新手机号。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPhoneChangeDTO implements Serializable {
    @NotBlank(message = "原手机号验证码不能为空")
    @Size(min = 6, max = 6, message = "原手机号验证码格式不正确")
    private String oldPhoneCode;

    @NotBlank(message = "新手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "新手机号格式不正确")
    private String newPhone;

    @NotBlank(message = "新手机号验证码不能为空")
    @Size(min = 6, max = 6, message = "新手机号验证码格式不正确")
    private String newPhoneCode;
}
