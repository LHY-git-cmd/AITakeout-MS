package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.io.Serializable;
import java.time.LocalDateTime;

@Builder
@Schema(description = "售后申请视图对象")
public record AfterSaleVO(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "请求编号") String requestNo,
        @Schema(description = "订单ID") Long orderId,
        @Schema(description = "用户ID") Long userId,
        @Schema(description = "请求类型") String requestType,
        @Schema(description = "原因") String reason,
        @Schema(description = "状态") String status,
        @Schema(description = "审核原因") String reviewReason,
        @Schema(description = "退款编号") String refundNo,
        @Schema(description = "退款金额（分）") Long refundAmountCent,
        @Schema(description = "退款状态") String refundStatus,
        @Schema(description = "创建时间") LocalDateTime createTime,
        @Schema(description = "审核时间") LocalDateTime reviewedAt
) implements Serializable {
}