package com.sky.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 订单取消DTO
 * 用于管理端取消订单请求参数
 */
@Data
public class OrdersCancelDTO implements Serializable {

    // 订单ID
    @NotNull(message = "订单ID不能为空")
    private Long id;

    // 订单取消原因
    @NotBlank(message = "取消原因不能为空")
    @Size(max = 255, message = "取消原因长度不能超过255个字符")
    private String cancelReason;

}
