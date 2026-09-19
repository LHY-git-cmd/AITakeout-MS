package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 下单幂等记录。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSubmission {
    private Long id;
    private Long userId;
    private String idempotencyKey;
    private Long orderId;
    private String requestHash;
    private LocalDateTime createTime;
}
