package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "订单试算请求数据传输对象")
public class OrderPreviewDTO implements Serializable {
    @NotNull(message = "收货地址不能为空")
    @Schema(description = "收货地址ID")
    private Long addressBookId;

    @Schema(description = "配送模式，默认为 'IMMEDIATE' (立即送达)", defaultValue = "IMMEDIATE")
    private String deliveryMode = "IMMEDIATE";

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "预约配送时间段的起始时间，格式 yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deliverySlotStart;
}