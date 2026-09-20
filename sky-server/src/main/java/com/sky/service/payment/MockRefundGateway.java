package com.sky.service.payment;

import com.sky.service.payment.model.RefundModels.RefundGatewayResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 可注入失败且按退款号幂等的模拟退款渠道。 */
@Component
@ConditionalOnProperty(prefix = "sky.refund", name = "channel", havingValue = "mock", matchIfMissing = true)
public class MockRefundGateway implements RefundGateway {
    private final Map<String, RefundGatewayResult> results = new ConcurrentHashMap<>();
    private volatile String nextFailureCode;

    @Override
    public RefundGatewayResult refund(String refundNo, String paymentNo, long amountCent) {
        return results.computeIfAbsent(refundNo, key -> {
            String failure = nextFailureCode;
            nextFailureCode = null;
            if (failure != null) return new RefundGatewayResult(false, null, failure, "模拟退款失败");
            return new RefundGatewayResult(true, "MR-" + refundNo.substring(Math.max(0, refundNo.length() - 20)), null, null);
        });
    }

    public void failNextRefund(String failureCode) {
        nextFailureCode = failureCode == null || failureCode.isBlank() ? "MOCK_REFUND_FAILED" : failureCode;
    }

    public void clearResult(String refundNo) {
        results.remove(refundNo);
    }
}
