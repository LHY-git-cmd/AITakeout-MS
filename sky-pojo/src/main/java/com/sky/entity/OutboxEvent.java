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
    private Long id;                    // 唯一标识符
    private String eventId;             // 事件ID
    private String businessKey;         // 业务键
    private String aggregateType;       // 聚合类型
    private String aggregateId;         // 聚合ID
    private String eventType;           // 事件类型
    private String payloadJson;         // 附带的JSON数据
    private String status;              // 状态
    private Integer attemptCount;       // 尝试次数
    private LocalDateTime nextAttemptAt; // 下次尝试时间
    private LocalDateTime publishedAt;  // 发布时间
    private String lastError;           // 最后一次错误信息
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime updateTime;   // 更新时间
}