package com.sky.vo;

import lombok.Builder;

import java.time.LocalDateTime;

/** 退款进度视图，失败状态不会伪装为已退款。 */
@Builder
public record RefundVO(String refundNo, Long orderId, Long amountCent, String status,
                       Integer attemptCount, String failureCode, String failureMessage,
                       LocalDateTime succeededAt, LocalDateTime nextRetryAt) {
}
