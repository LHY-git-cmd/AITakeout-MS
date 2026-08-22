package com.sky.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * C端用户登录
 */
@Data
public class UserLoginDTO implements Serializable {

    @NotBlank(message = "登录凭证不能为空")
    @Size(max = 128, message = "登录凭证长度不正确")
    private String code;

}
