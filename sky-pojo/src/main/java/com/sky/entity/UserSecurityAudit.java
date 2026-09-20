package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 用户敏感操作安全审计。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSecurityAudit {
    private Long id;
    private Long userId;
    private String eventType;
    private String detailJson;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createTime;
}
