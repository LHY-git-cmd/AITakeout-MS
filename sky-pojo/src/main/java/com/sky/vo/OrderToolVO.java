package com.sky.vo;

import com.sky.entity.OrderDetail;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 提供给AI工具的订单字段白名单。 */
public record OrderToolVO(
        Long id,
        String number,
        Integer status,
        LocalDateTime orderTime,
        LocalDateTime checkoutTime,
        Integer payMethod,
        Integer payStatus,
        BigDecimal amount,
        String remark,
        String maskedUserName,
        String maskedPhone,
        String maskedAddress,
        String maskedConsignee,
        String cancelReason,
        String rejectionReason,
        LocalDateTime estimatedDeliveryTime,
        LocalDateTime deliveryTime,
        List<OrderDetail> items) {
}
