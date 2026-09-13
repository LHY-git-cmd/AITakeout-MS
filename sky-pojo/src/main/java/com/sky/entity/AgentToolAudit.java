package com.sky.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** AI工具调用审计记录。 */
@Data
@Builder
public class AgentToolAudit {
    private Long id;
    private String requestId;
    private String taskId;
    private String toolCallId;
    private Long employeeId;
    private String actorRole;
    private String operation;
    private String requiredPermission;
    private String argumentHash;
    private String status;
    private String errorCode;
    private Long durationMs;
    private String traceId;
    private LocalDateTime createTime;
}
