package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

// AgentMessage.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessage implements Serializable {
    private Long id;
    private String messageId;
    private String sessionId;
    private String taskId;
    private Integer role;         // 1user 2assistant 3system
    private String content;
    private String contentType;   // text/markdown/tool_call
    private Integer tokenCount;
    private Integer seqNo;
    private LocalDateTime createTime;
}