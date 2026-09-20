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
    private Long id;                // 唯一标识符
    private Long userId;            // 用户ID
    private String idempotencyKey;  // 幂等键
    private Long orderId;           // 订单ID
    private String requestHash;     // 请求哈希值
    private LocalDateTime createTime; // 创建时间
}