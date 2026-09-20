package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.io.Serializable;
import java.time.LocalDateTime;

@Builder
@Schema(description = "退款视图对象")
public record RefundVO(
        @Schema(description = "退款编号") String refundNo,
        @Schema(description = "订单ID") Long orderId,
        @Schema(description = "退款金额（分）") Long amountCent,
        @Schema(description = "状态") String status,
        @Schema(description = "尝试次数") Integer attemptCount,
        @Schema(description = "失败代码") String failureCode,
        @Schema(description = "失败消息") String failureMessage,
        @Schema(description = "成功时间") LocalDateTime succeededAt,
        @Schema(description = "下次重试时间") LocalDateTime nextRetryAt) implements Serializable {
}