package com.sky.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户公共知识不可变发布版本。 */
@Data
public class UserAgentKnowledgeRelease {
    private Long id;                    // 主键
    private String releaseId;           // 发布ID
    private String kbId;                // 知识库ID
    private Integer releaseVersion;     // 发布版本
    private String status;              // 状态
    private LocalDateTime effectiveFrom;  // 生效时间
    private LocalDateTime effectiveUntil; // 失效时间
    private Long createdBy;             // 创建人
    private Long approvedBy;            // 审核人
    private Long publishedBy;           // 发布人
    private LocalDateTime createdAt;     // 创建时间
    private LocalDateTime approvedAt;    // 审核时间
    private LocalDateTime publishedAt;   // 发布时间
    private LocalDateTime updatedAt;     // 更新时间
}