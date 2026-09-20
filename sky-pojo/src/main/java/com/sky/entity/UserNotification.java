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
    private Long id;                // 唯一标识符
    private String eventId;         // 事件ID
    private String businessKey;     // 业务键
    private Long userId;            // 用户ID
    private String type;            // 通知类型
    private String title;           // 标题
    private String content;         // 内容
    private Long orderId;           // 订单ID
    private LocalDateTime readAt;   // 已读时间
    private LocalDateTime createTime; // 创建时间
}