package com.sky.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/** 修改当前员工自己的密码；身份从登录上下文取得，不接受客户端传入账号 ID。 */
@Data
public class EmployeePasswordDTO {
    @NotBlank(message = "原密码不能为空")
    @Size(max = 64, message = "原密码过长")
    @ToString.Exclude
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "[\\x21-\\x7E]{12,64}", message = "新密码须为12至64位字母、数字或符号，不含空格")
    @ToString.Exclude
    private String newPassword;
}
