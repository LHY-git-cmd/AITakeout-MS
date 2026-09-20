package com.sky.service.payment;

import com.sky.service.payment.model.PaymentModels.GatewayResult;

/** 支付渠道适配器；业务层只依赖统一结果，不接触渠道原始报文。 */
public interface PaymentGateway {
    String channel();

    GatewayResult process(String paymentNo);

    GatewayResult query(String gatewayTradeNo);

    void close(String gatewayTradeNo);
}
