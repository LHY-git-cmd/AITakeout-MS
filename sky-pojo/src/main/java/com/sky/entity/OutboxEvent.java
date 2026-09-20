package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 可靠通知发件箱实体。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {
    private Long id;
    private String eventId;
    private String businessKey;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payloadJson;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime publishedAt;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
