package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "管理端模拟账户调账请求数据传输对象")
public class AdminAccountAdjustmentDTO implements Serializable {
    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户ID")
    private Long userId;

    @NotNull(message = "调整金额不能为空")
    @Schema(description = "调整金额，单位为整数分")
    private Long deltaCent;

    @NotBlank(message = "调账原因不能为空")
    @Size(max = 255, message = "调账原因长度不能超过255个字符")
    @Schema(description = "调账原因")
    private String reason;

    @NotBlank(message = "幂等键不能为空")
    @Size(max = 80, message = "幂等键长度不能超过80个字符")
    @Schema(description = "幂等键，用于保证操作的唯一性")
    private String idempotencyKey;
}