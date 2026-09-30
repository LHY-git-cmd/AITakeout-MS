package com.sky.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户公共知识不可变发布版本。 */
@Data
public class UserAgentKnowledgeRelease {
    private Long id;
    private String releaseId;
    private String kbId;
    private Integer releaseVersion;
    private String status;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;
    private Long createdBy;
    private Long approvedBy;
    private Long publishedBy;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
}
