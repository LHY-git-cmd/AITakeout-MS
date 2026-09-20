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
    private Long id;                // 唯一标识符
    private Long userId;            // 用户ID
    private String eventType;       // 事件类型
    private String detailJson;      // 详情 (JSON格式)
    private String ipAddress;       // IP地址
    private String userAgent;       // 用户代理
    private LocalDateTime createTime; // 创建时间
}