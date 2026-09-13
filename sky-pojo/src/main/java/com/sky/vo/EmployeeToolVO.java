package com.sky.vo;

/** 提供给AI工具的员工字段白名单。 */
public record EmployeeToolVO(
        Long id,
        String username,
        String name,
        String maskedPhone,
        String sex,
        String maskedIdNumber,
        Integer status,
        String role) {
}
