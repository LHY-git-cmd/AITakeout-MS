package com.sky.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 公共知识发布、下线、回滚审计。 */
@Data
public class UserAgentKnowledgePublishAudit {
    private Long id;                    // 主键
    private String releaseId;           // 发布ID
    private String action;              // 操作
    private Long operatorId;            // 操作人ID
    private String fromStatus;          // 原状态
    private String toStatus;            // 新状态
    private String detailJson;          // 详情
    private LocalDateTime createdAt;     // 创建时间
}