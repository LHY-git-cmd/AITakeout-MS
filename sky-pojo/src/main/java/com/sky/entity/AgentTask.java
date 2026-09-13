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
    private Long id;
    private String taskId;
    private String sessionId;
    private Long userId;
    private String actorRole;    // 创建任务时的角色快照，仅用于Python工具筛选和审计
    private String query;
    private Integer status;       // 0排队 1执行中 2完成 3失败 4取消
    private Integer progress;    // 0-100
    private String model;
    private String requestHash;
    private String assistantMessageId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
