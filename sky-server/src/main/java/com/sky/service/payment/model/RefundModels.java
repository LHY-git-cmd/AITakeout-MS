package com.sky.service.payment.model;

import java.time.LocalDateTime;

/** 退款渠道和应用层模型。 */
public final class RefundModels {
    private RefundModels() {
    }

    public enum RefundStatus { CREATED, PROCESSING, SUCCEEDED, FAILED }

    public record RefundGatewayResult(boolean succeeded, String gatewayRefundNo,
                                      String failureCode, String failureMessage) {
    }

    public record RefundView(String refundNo, long orderId, long amountCent, RefundStatus status,
                             int attemptCount, String failureCode, String failureMessage,
                             LocalDateTime succeededAt, LocalDateTime nextRetryAt) {
    }
}
