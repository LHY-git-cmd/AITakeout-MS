package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(description = "员工工具视图对象")
public record EmployeeToolVO(
        @Schema(description = "员工ID") Long id,
        @Schema(description = "用户名") String username,
        @Schema(description = "姓名") String name,
        @Schema(description = "脱敏后的手机号") String maskedPhone,
        @Schema(description = "性别") String sex,
        @Schema(description = "脱敏后的身份证号") String maskedIdNumber,
        @Schema(description = "状态 (0:禁用, 1:启用)") Integer status,
        @Schema(description = "角色") String role) implements Serializable {
}