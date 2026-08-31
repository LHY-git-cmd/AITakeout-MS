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
    private Long id;
    private String eventId;
    private String taskId;
    private Integer seqNo;
    private String eventType;    // message/tool_call/tool_result/progress/done/error
    private String data;         // JSON字符串
    private LocalDateTime createTime;
}