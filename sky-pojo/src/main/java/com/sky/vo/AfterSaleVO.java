package com.sky.vo;

import lombok.Builder;

import java.time.LocalDateTime;

/** 面向用户和管理端的售后申请视图。 */
@Builder
public record AfterSaleVO(Long id, String requestNo, Long orderId, Long userId,
                          String requestType, String reason, String status,
                          String reviewReason, String refundNo, Long refundAmountCent,
                          String refundStatus, LocalDateTime createTime,
                          LocalDateTime reviewedAt) {
}
