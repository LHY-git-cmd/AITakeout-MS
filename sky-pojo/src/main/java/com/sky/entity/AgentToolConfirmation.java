package com.sky.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** AI写工具一次性确认凭证。 */
@Data
@Builder
public class AgentToolConfirmation {
    private Long id;
    private String confirmationId;
    private String taskId;
    private String toolCallId;
    private Long employeeId;
    private String actorRole;
    private String operation;
    private String argumentsJson;
    private String argumentHash;
    private String resourceVersion;
    private String summary;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime executedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
