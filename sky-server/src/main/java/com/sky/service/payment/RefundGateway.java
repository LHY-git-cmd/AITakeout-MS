package com.sky.service.payment;

import com.sky.service.payment.model.RefundModels.RefundGatewayResult;

/** 退款渠道适配器，真实微信退款可在不改业务流程的情况下替换实现。 */
public interface RefundGateway {
    RefundGatewayResult refund(String refundNo, String paymentNo, long amountCent);
}
