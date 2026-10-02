package com.sky.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户Agent场景与公共知识发布版本的绑定。 */
@Data
public class UserAgentKnowledgeBinding {
    private Long id;                    // 主键
    private String bindingId;           // 绑定ID
    private String scene;               // 场景
    private String releaseId;           // 发布ID
    private String status;              // 状态
    private LocalDateTime effectiveFrom;  // 生效时间
    private LocalDateTime effectiveUntil; // 失效时间
    private Long createdBy;             // 创建人
    private LocalDateTime createdAt;     // 创建时间
}