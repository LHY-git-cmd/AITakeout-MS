package com.sky.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 公共知识发布审核记录。 */
@Data
public class UserAgentKnowledgeReview {
    private Long id;                // 主键
    private String releaseId;       // 发布ID
    private Long reviewerId;        // 审核人ID
    private String decision;        // 审核决定
    private String comment;         // 审核意见
    private LocalDateTime createdAt; // 创建时间
}