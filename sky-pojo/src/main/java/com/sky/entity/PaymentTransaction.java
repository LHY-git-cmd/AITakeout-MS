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
    private Long id;                    // 唯一标识符
    private String paymentNo;           // 支付单号
    private Long orderId;               // 订单ID
    private Long userId;                // 用户ID
    private String channel;             // 支付渠道
    private String idempotencyKey;      // 幂等键
    private Long amountCent;            // 金额（分）
    private String status;              // 状态
    private String gatewayTradeNo;      // 支付网关交易号
    private String callbackEventId;     // 回调事件ID
    private LocalDateTime expiresAt;    // 过期时间
    private LocalDateTime succeededAt;  // 成功时间
    private String failureCode;         // 失败代码
    private Integer version;            // 版本号
    private Long successOrderId;        // 成功支付的订单ID
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime updateTime;   // 更新时间
}