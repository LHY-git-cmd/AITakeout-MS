package com.sky.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 公共知识发布、下线、回滚审计。 */
@Data
public class UserAgentKnowledgePublishAudit {
    private Long id;
    private String releaseId;
    private String action;
    private Long operatorId;
    private String fromStatus;
    private String toStatus;
    private String detailJson;
    private LocalDateTime createdAt;
}
