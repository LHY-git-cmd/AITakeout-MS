package com.sky.service.payment;

import com.sky.service.payment.model.PaymentModels.GatewayResult;
import com.sky.service.payment.model.PaymentModels.PaymentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 可延迟、可注入终态的生产级模拟支付渠道。 */
@Component
@ConditionalOnProperty(prefix = "sky.payment", name = "channel", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {
    private final long delayMillis;
    private final Map<String, Trade> byPaymentNo = new ConcurrentHashMap<>();
    private final Map<String, Trade> byTradeNo = new ConcurrentHashMap<>();

    public MockPaymentGateway(@Value("${sky.payment.mock.delay-ms:800}") long delayMillis) {
        this.delayMillis = Math.max(0L, delayMillis);
    }

    @Override
    public String channel() {
        return "MOCK";
    }

    @Override
    public GatewayResult process(String paymentNo) {
        if (paymentNo == null || paymentNo.isBlank()) throw new IllegalArgumentException("支付号不能为空");
        Trade trade = byPaymentNo.computeIfAbsent(paymentNo, key -> {
            Instant readyAt = Instant.now().plusMillis(delayMillis);
            String compactPayment = key.substring("PAY-".length()).replace("-", "");
            String tradeNo = "M" + Long.toString(readyAt.toEpochMilli(), 36) + "-" + compactPayment;
            Trade created = new Trade(key, tradeNo, readyAt);
            byTradeNo.put(created.gatewayTradeNo, created);
            return created;
        });
        return trade.result();
    }

    @Override
    public GatewayResult query(String gatewayTradeNo) {
        Trade trade = byTradeNo.computeIfAbsent(gatewayTradeNo, this::restore);
        trade.completeOnDeadline();
        return trade.result();
    }

    @Override
    public void close(String gatewayTradeNo) {
        Trade trade = byTradeNo.computeIfAbsent(gatewayTradeNo, this::restore);
        trade.finish(PaymentStatus.CLOSED, "PAYMENT_TIMEOUT");
    }

    public void completeSuccessfully(String paymentNo) {
        terminal(paymentNo, PaymentStatus.SUCCEEDED, null);
    }

    public void completeFailed(String paymentNo, String failureCode) {
        terminal(paymentNo, PaymentStatus.FAILED,
                failureCode == null || failureCode.isBlank() ? "MOCK_FAILED" : failureCode);
    }

    public void timeout(String paymentNo) {
        terminal(paymentNo, PaymentStatus.CLOSED, "PAYMENT_TIMEOUT");
    }

    private void terminal(String paymentNo, PaymentStatus status, String failureCode) {
        Trade trade = byPaymentNo.get(paymentNo);
        if (trade == null) throw new IllegalArgumentException("模拟支付单不存在");
        trade.finish(status, failureCode);
    }

    private Trade restore(String gatewayTradeNo) {
        try {
            if (gatewayTradeNo == null || !gatewayTradeNo.startsWith("M")) throw new IllegalArgumentException();
            int separator = gatewayTradeNo.indexOf('-');
            long readyAt = Long.parseLong(gatewayTradeNo.substring(1, separator), 36);
            String compact = gatewayTradeNo.substring(separator + 1);
            if (compact.length() != 32) throw new IllegalArgumentException();
            String paymentNo = "PAY-" + compact.substring(0, 8) + "-" + compact.substring(8, 12) + "-"
                    + compact.substring(12, 16) + "-" + compact.substring(16, 20) + "-" + compact.substring(20);
            Trade restored = new Trade(paymentNo, gatewayTradeNo, Instant.ofEpochMilli(readyAt));
            byPaymentNo.putIfAbsent(paymentNo, restored);
            return restored;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("模拟渠道交易不存在", ex);
        }
    }

    private static final class Trade {
        private final String paymentNo;
        private final String gatewayTradeNo;
        private final Instant readyAt;
        private PaymentStatus status = PaymentStatus.PROCESSING;
        private String eventId;
        private String failureCode;

        private Trade(String paymentNo, String gatewayTradeNo, Instant readyAt) {
            this.paymentNo = paymentNo;
            this.gatewayTradeNo = gatewayTradeNo;
            this.readyAt = readyAt;
        }

        private synchronized void completeOnDeadline() {
            if (status == PaymentStatus.PROCESSING && !Instant.now().isBefore(readyAt)) {
                finish(PaymentStatus.SUCCEEDED, null);
            }
        }

        private synchronized void finish(PaymentStatus target, String code) {
            if (status != PaymentStatus.PROCESSING) return;
            status = target;
            failureCode = code;
            eventId = "MOCK-EVENT-" + UUID.nameUUIDFromBytes(
                    (gatewayTradeNo + ":" + target).getBytes(StandardCharsets.UTF_8));
        }

        private synchronized GatewayResult result() {
            return new GatewayResult(paymentNo, gatewayTradeNo, eventId, status, failureCode);
        }
    }
}
