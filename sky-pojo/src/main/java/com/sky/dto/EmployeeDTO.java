package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@Data
@Schema(description = "员工数据传输对象")
public class EmployeeDTO implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 32, message = "用户名长度不能超过32个字符")
    @Schema(description = "用户名")
    private String username;

    /** 仅创建员工时使用，不随响应返回，也不进入 DTO 日志字符串。 */
    @ToString.Exclude
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(description = "创建员工时设置的独立初始密码，12至64位可打印ASCII字符")
    private String initialPassword;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 32, message = "姓名长度不能超过32个字符")
    @Schema(description = "姓名")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String phone;

    @Pattern(regexp = "^[01]$", message = "性别参数不正确")
    @Schema(description = "性别 (0:女, 1:男)")
    private String sex;

    @Size(max = 18, message = "身份证号长度不能超过18个字符")
    @Schema(description = "身份证号")
    private String idNumber;

}
