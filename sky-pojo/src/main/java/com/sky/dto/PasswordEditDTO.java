package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "密码修改数据传输对象")
public class PasswordEditDTO implements Serializable {

    @Schema(description = "员工ID")
    private Long empId;

    @Schema(description = "旧密码")
    private String oldPassword;

    @Schema(description = "新密码")
    private String newPassword;

}