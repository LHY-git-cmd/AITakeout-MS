package com.sky.service.payment;

import com.sky.entity.PaymentTransaction;
import com.sky.mapper.PaymentTransactionMapper;
import com.sky.service.payment.model.PaymentModels.GatewayResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 在结算事务失败后以独立事务记录稳定的支付失败终态。 */
@Service
@RequiredArgsConstructor
public class PaymentStateService {
    private final PaymentTransactionMapper paymentMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String paymentNo, GatewayResult result, String failureCode) {
        PaymentTransaction payment = paymentMapper.findByPaymentNoForUpdate(paymentNo);
        if (payment == null || !"PROCESSING".equals(payment.getStatus())) return;
        LocalDateTime now = LocalDateTime.now();
        paymentMapper.updateState(payment.getId(), "PROCESSING", "FAILED", result.gatewayTradeNo(),
                result.eventId(), failureCode, null, now);
    }
}
