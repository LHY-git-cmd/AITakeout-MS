package com.sky.task;

import com.sky.service.payment.PaymentApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 周期性对账模拟渠道结果，并关闭超过支付时限的支付单。 */
@Component
@ConditionalOnProperty(prefix = "sky.payment", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PaymentExpiryTask {
    private final PaymentApplicationService paymentService;

    @Scheduled(fixedDelayString = "${sky.payment.reconcile-delay-ms:1000}")
    public void reconcileAndExpire() {
        // 先固化超时终态，避免已过期支付被迟到的渠道成功结果结算。
        int expired = paymentService.expireBatch(100);
        paymentService.reconcileBatch(100);
        if (expired > 0) log.info("已关闭超时支付单：count={}", expired);
    }
}
