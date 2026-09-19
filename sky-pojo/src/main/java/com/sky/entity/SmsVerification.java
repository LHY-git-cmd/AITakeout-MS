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
    private Long id;
    private String phone;
    private String codeHash;
    private String purpose;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private Integer attemptCount;
    private LocalDateTime createTime;
}
