package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 整单退款交易实体。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundTransaction {
    private Long id;                    // 唯一标识符
    private String refundNo;            // 退款单号
    private String businessKey;         // 业务键
    private Long orderId;               // 订单ID
    private Long paymentId;             // 支付ID
    private Long userId;                // 用户ID
    private Long sourceAccountId;       // 源账户ID
    private Long targetAccountId;       // 目标账户ID
    private Long amountCent;            // 金额（分）
    private String status;              // 状态
    private Integer attemptCount;       // 尝试次数
    private Long freezeTransferId;      // 冻结转账ID
    private Long releaseTransferId;     // 解冻转账ID
    private String gatewayRefundNo;     // 支付网关退款单号
    private String failureCode;         // 失败代码
    private String failureMessage;      // 失败信息
    private LocalDateTime succeededAt;  // 成功时间
    private LocalDateTime nextRetryAt;  // 下次重试时间
    private Integer version;            // 版本号
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime updateTime;   // 更新时间
}