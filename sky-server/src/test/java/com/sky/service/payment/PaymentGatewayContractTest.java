package com.sky.service.payment;

import com.sky.service.payment.model.PaymentModels.GatewayResult;
import com.sky.service.payment.model.PaymentModels.PaymentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 统一支付网关契约测试，确保模拟渠道可被真实渠道平替。 */
class PaymentGatewayContractTest {

    @Test
    void processStartsAsynchronouslyAndCanCompleteIdempotently() {
        MockPaymentGateway gateway = new MockPaymentGateway(60_000L);

        GatewayResult processing = gateway.process("PAY-1");
        gateway.completeSuccessfully("PAY-1");
        GatewayResult first = gateway.query(processing.gatewayTradeNo());
        gateway.completeSuccessfully("PAY-1");
        GatewayResult duplicate = gateway.query(processing.gatewayTradeNo());

        assertThat(processing.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(first.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(duplicate.eventId()).isEqualTo(first.eventId());
    }

    @Test
    void closedMockTradeCannotBecomeSuccessfulLater() {
        MockPaymentGateway gateway = new MockPaymentGateway(60_000L);
        GatewayResult processing = gateway.process("PAY-2");

        gateway.timeout("PAY-2");
        gateway.completeSuccessfully("PAY-2");

        assertThat(gateway.query(processing.gatewayTradeNo()).status()).isEqualTo(PaymentStatus.CLOSED);
    }

    @Test
    void gatewayTradeCanBeQueriedAfterAdapterRestart() {
        MockPaymentGateway beforeRestart = new MockPaymentGateway(0L);
        GatewayResult processing = beforeRestart.process("PAY-12345678-1234-1234-1234-123456789012");

        MockPaymentGateway afterRestart = new MockPaymentGateway(0L);
        GatewayResult recovered = afterRestart.query(processing.gatewayTradeNo());

        assertThat(recovered.paymentNo()).isEqualTo(processing.paymentNo());
        assertThat(recovered.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(recovered.eventId()).isNotBlank();
    }
}
