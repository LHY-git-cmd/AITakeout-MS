package com.sky.vo;

import com.sky.entity.OrderDetail;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "订单工具视图对象")
public record OrderToolVO(
        @Schema(description = "订单ID") Long id,
        @Schema(description = "订单号") String number,
        @Schema(description = "状态") Integer status,
        @Schema(description = "下单时间") LocalDateTime orderTime,
        @Schema(description = "结账时间") LocalDateTime checkoutTime,
        @Schema(description = "支付方式") Integer payMethod,
        @Schema(description = "支付状态") Integer payStatus,
        @Schema(description = "金额") BigDecimal amount,
        @Schema(description = "备注") String remark,
        @Schema(description = "脱敏后的用户名") String maskedUserName,
        @Schema(description = "脱敏后的手机号") String maskedPhone,
        @Schema(description = "脱敏后的地址") String maskedAddress,
        @Schema(description = "脱敏后的收货人") String maskedConsignee,
        @Schema(description = "取消原因") String cancelReason,
        @Schema(description = "拒绝原因") String rejectionReason,
        @Schema(description = "预计送达时间") LocalDateTime estimatedDeliveryTime,
        @Schema(description = "送达时间") LocalDateTime deliveryTime,
        @Schema(description = "订单详情") List<OrderDetail> items) implements Serializable {
}