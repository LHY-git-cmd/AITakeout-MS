package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

// AgentEvent.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEvent implements Serializable {
    private Long id;                // 唯一标识符
    private String eventId;         // 事件ID
    private String taskId;          // 任务ID
    private Integer seqNo;          // 序列号
    private String eventType;       // 事件类型 (message/tool_call/tool_result/progress/done/error)
    private String data;            // 事件数据 (JSON字符串)
    private LocalDateTime createTime; // 创建时间
}