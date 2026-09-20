package com.sky.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** AI工具调用审计记录。 */
@Data
@Builder
public class AgentToolAudit {
    private Long id;                    // 唯一标识符
    private String requestId;           // 请求ID
    private String taskId;              // 任务ID
    private String toolCallId;          // 工具调用ID
    private Long employeeId;            // 员工ID
    private String actorRole;           // 角色
    private String operation;           // 操作
    private String requiredPermission;  // 所需权限
    private String argumentHash;        // 参数哈希值
    private String status;              // 状态
    private String errorCode;           // 错误码
    private Long durationMs;            // 持续时间（毫秒）
    private String traceId;             // 追踪ID
    private LocalDateTime createTime;   // 创建时间
}