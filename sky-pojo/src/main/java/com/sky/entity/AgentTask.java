package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

// AgentTask.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTask implements Serializable {
    private Long id;                        // 唯一标识符
    private String taskId;                  // 任务ID
    private String sessionId;               // 会话ID
    private Long userId;                    // 用户ID
    private String actorRole;               // 角色快照
    private String query;                   // 查询语句
    private Integer status;                 // 任务状态 (0:排队, 1:执行中, 2:完成, 3:失败, 4:取消)
    private Integer progress;               // 进度 (0-100)
    private String model;                   // 模型名称
    private String requestHash;             // 请求哈希值
    private String assistantMessageId;      // 助手消息ID
    private LocalDateTime startedAt;        // 开始时间
    private LocalDateTime finishedAt;       // 结束时间
    private String errorMsg;                // 错误信息
    private LocalDateTime createTime;       // 创建时间
    private LocalDateTime updateTime;       // 更新时间
}