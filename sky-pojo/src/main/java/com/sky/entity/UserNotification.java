package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 用户通知实体，WebSocket 断线时仍可补拉。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNotification {
    private Long id;
    private String eventId;
    private String businessKey;
    private Long userId;
    private String type;
    private String title;
    private String content;
    private Long orderId;
    private LocalDateTime readAt;
    private LocalDateTime createTime;
}
