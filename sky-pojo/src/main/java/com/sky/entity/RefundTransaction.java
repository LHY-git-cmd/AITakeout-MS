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
    private Long id;
    private String refundNo;
    private String businessKey;
    private Long orderId;
    private Long paymentId;
    private Long userId;
    private Long sourceAccountId;
    private Long targetAccountId;
    private Long amountCent;
    private String status;
    private Integer attemptCount;
    private Long freezeTransferId;
    private Long releaseTransferId;
    private String gatewayRefundNo;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime succeededAt;
    private LocalDateTime nextRetryAt;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
