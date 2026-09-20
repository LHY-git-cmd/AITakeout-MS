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
    private Long id;                // 唯一标识符
    private String messageId;       // 消息ID
    private String sessionId;       // 会话ID
    private String taskId;          // 任务ID
    private Integer role;           // 角色 (1:user, 2:assistant, 3:system)
    private String content;         // 消息内容
    private String contentType;     // 内容类型 (text/markdown/tool_call)
    private Integer tokenCount;     // Token数量
    private Integer seqNo;          // 序列号
    private LocalDateTime createTime; // 创建时间
}