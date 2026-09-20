package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 取消或售后申请持久化实体。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleRequest {
    private Long id;
    private String requestNo;
    private Long orderId;
    private Long userId;
    private String requestType;
    private String reason;
    private String status;
    private Integer previousOrderStatus;
    private Long reviewedBy;
    private String reviewReason;
    private LocalDateTime reviewedAt;
    private String refundNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
