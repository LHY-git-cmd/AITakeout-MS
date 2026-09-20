package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 用户刷新会话。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {
    private Long id;                    // 唯一标识符
    private Long userId;                // 用户ID
    private String refreshTokenHash;    // 刷新令牌哈希值
    private String deviceId;            // 设备ID
    private LocalDateTime expiresAt;    // 过期时间
    private LocalDateTime revokedAt;    // 撤销时间
    private LocalDateTime createTime;   // 创建时间
}