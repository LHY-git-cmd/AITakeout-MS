package com.sky.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@Data
public class EmployeeDTO implements Serializable {

    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 32, message = "用户名长度不能超过32个字符")
    private String username;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 32, message = "姓名长度不能超过32个字符")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Pattern(regexp = "^[01]$", message = "性别参数不正确")
    private String sex;

    @Size(max = 18, message = "身份证号长度不能超过18个字符")
    private String idNumber;

}
