package com.sky.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class WebUserRegisterDTO implements Serializable {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 32, message = "姓名不能超过32个字符")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Pattern(regexp = "^$|^[01]$", message = "性别参数不正确")
    private String sex;

    @Pattern(regexp = "^$|^\\d{17}[\\dXx]$", message = "身份证号格式不正确")
    private String idNumber;

    @Size(max = 500, message = "头像地址不能超过500个字符")
    private String avatar;
}
