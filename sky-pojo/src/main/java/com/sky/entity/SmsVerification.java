package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 短信验证码记录。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsVerification {
    private Long id;                    // 唯一标识符
    private String phone;               // 手机号
    private String codeHash;            // 验证码哈希值
    private String purpose;             // 用途
    private LocalDateTime expiresAt;    // 过期时间
    private LocalDateTime usedAt;       // 使用时间
    private Integer attemptCount;       // 尝试次数
    private LocalDateTime createTime;   // 创建时间
}