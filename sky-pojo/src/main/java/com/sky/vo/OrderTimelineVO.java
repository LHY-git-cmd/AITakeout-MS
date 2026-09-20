package com.sky.vo;

import lombok.Builder;

import java.time.LocalDateTime;

/** 用户可见的订单时间轴节点。 */
@Builder
public record OrderTimelineVO(Long id, String eventType, String message,
                              String operatorType, LocalDateTime eventTime) {
}
