package com.sky.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 公共知识发布审核记录。 */
@Data
public class UserAgentKnowledgeReview {
    private Long id;
    private String releaseId;
    private Long reviewerId;
    private String decision;
    private String comment;
    private LocalDateTime createdAt;
}
