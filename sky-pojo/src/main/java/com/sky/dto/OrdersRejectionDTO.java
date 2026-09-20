package com.sky.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@Data
@Schema(description = "订单拒单数据传输对象")
public class OrdersRejectionDTO implements Serializable {

    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID")
    private Long id;

    @NotBlank(message = "拒单原因不能为空")
    @Size(max = 255, message = "拒单原因长度不能超过255个字符")
    @Schema(description = "订单拒绝原因")
    private String rejectionReason;

}