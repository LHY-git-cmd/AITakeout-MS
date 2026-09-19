package com.sky.task;

import com.sky.service.payment.PaymentApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;

/** 验证支付调度严格先关闭超时支付，再处理渠道对账结果。 */
@ExtendWith(MockitoExtension.class)
class PaymentExpiryTaskTest {
    @Mock
    private PaymentApplicationService paymentService;

    @Test
    void expiresPaymentsBeforeReconcilingGatewayResults() {
        PaymentExpiryTask task = new PaymentExpiryTask(paymentService);

        task.reconcileAndExpire();

        InOrder ordered = inOrder(paymentService);
        ordered.verify(paymentService).expireBatch(100);
        ordered.verify(paymentService).reconcileBatch(100);
    }
}
