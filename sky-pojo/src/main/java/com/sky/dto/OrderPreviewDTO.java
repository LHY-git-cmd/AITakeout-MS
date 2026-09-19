package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/** 订单试算请求，金额不由客户端提交。 */
@Data
public class OrderPreviewDTO {
    @NotNull(message = "收货地址不能为空")
    private Long addressBookId;
    private String deliveryMode = "IMMEDIATE";
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deliverySlotStart;
}
