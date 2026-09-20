package com.sky.task;

import com.sky.mapper.RefundTransactionMapper;
import com.sky.service.payment.RefundApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 定时重试失败退款，始终沿用原退款单和原冻结资金。 */
@Component
@ConditionalOnProperty(prefix = "sky.refund", name = "scheduling-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RefundRetryTask {
    private final RefundTransactionMapper refundMapper;
    private final RefundApplicationService refundService;

    @Scheduled(fixedDelayString = "${sky.refund.retry-delay-ms:60000}")
    public void retry() {
        refundMapper.findRetryable(LocalDateTime.now(), 50).forEach(refund -> {
            try {
                refundService.retry(refund.getRefundNo());
            } catch (RuntimeException exception) {
                log.error("退款自动重试失败：refundNo={}", refund.getRefundNo(), exception);
            }
        });
    }
}
