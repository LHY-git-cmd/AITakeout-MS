package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 支付交易记录。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {
    private Long id;
    private String paymentNo;
    private Long orderId;
    private Long userId;
    private String channel;
    private String idempotencyKey;
    private Long amountCent;
    private String status;
    private String gatewayTradeNo;
    private String callbackEventId;
    private LocalDateTime expiresAt;
    private LocalDateTime succeededAt;
    private String failureCode;
    private Integer version;
    private Long successOrderId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
