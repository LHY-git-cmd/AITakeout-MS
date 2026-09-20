package com.sky.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/** 管理端模拟账户调账请求，金额单位为整数分。 */
@Data
public class AdminAccountAdjustmentDTO implements Serializable {
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    @NotNull(message = "调整金额不能为空")
    private Long deltaCent;
    @NotBlank(message = "调账原因不能为空")
    @Size(max = 255, message = "调账原因长度不能超过255个字符")
    private String reason;
    @NotBlank(message = "幂等键不能为空")
    @Size(max = 80, message = "幂等键长度不能超过80个字符")
    private String idempotencyKey;
}
