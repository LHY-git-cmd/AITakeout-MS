package com.sky.service.payment.model;

import java.time.LocalDateTime;

/** 支付应用层统一模型，隔离具体渠道的响应格式。 */
public final class PaymentModels {
    private PaymentModels() {
    }

    public enum PaymentStatus {
        CREATED, PROCESSING, SUCCEEDED, FAILED, CLOSED
    }

    public record PaymentView(String paymentNo, long orderId, PaymentStatus status,
                              long amountCent, LocalDateTime expiresAt, String failureCode) {
    }

    public record GatewayResult(String paymentNo, String gatewayTradeNo, String eventId,
                                PaymentStatus status, String failureCode) {
    }
}
