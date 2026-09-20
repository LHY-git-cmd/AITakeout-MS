package com.sky.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.io.Serializable;
import java.time.LocalDateTime;

@Builder
@Schema(description = "订单时间轴视图对象")
public record OrderTimelineVO(
        @Schema(description = "主键ID") Long id,
        @Schema(description = "事件类型") String eventType,
        @Schema(description = "消息") String message,
        @Schema(description = "操作员类型") String operatorType,
        @Schema(description = "事件时间") LocalDateTime eventTime) implements Serializable {
}