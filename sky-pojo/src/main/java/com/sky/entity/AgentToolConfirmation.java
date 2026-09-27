package com.sky.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** AI写工具一次性确认凭证。 */
@Data
@Builder
public class AgentToolConfirmation {
    private Long id;                    // 唯一标识符
    private String confirmationId;      // 确认凭证ID
    private String taskId;              // 任务ID
    private String toolCallId;          // 工具调用ID
    private Long employeeId;            // 历史管理员ID，兼容旧数据
    private String actorType;           // 主体类型：ADMIN/USER/SYSTEM
    private Long actorId;               // 主体ID
    private String actorRole;           // 角色
    private String operation;           // 操作
    private String argumentsJson;       // 参数 (JSON格式)
    private String argumentHash;        // 参数哈希值
    private String resourceVersion;     // 资源版本号
    private String summary;             // 操作摘要
    private String status;              // 状态
    private LocalDateTime expiresAt;    // 过期时间
    private LocalDateTime confirmedAt;  // 确认时间
    private LocalDateTime executedAt;   // 执行时间
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime updateTime;   // 更新时间
}
