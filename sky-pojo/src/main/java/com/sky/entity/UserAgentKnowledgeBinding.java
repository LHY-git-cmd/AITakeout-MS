package com.sky.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户Agent场景与公共知识发布版本的绑定。 */
@Data
public class UserAgentKnowledgeBinding {
    private Long id;
    private String bindingId;
    private String scene;
    private String releaseId;
    private String status;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;
    private Long createdBy;
    private LocalDateTime createdAt;
}
