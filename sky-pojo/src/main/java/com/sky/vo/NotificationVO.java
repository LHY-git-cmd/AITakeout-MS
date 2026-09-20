package com.sky.vo;

import lombok.Builder;

import java.time.LocalDateTime;

/** 通知中心列表项。 */
@Builder
public record NotificationVO(Long id, String eventId, String type, String title,
                             String content, Long orderId, boolean read,
                             LocalDateTime createTime) {
}
