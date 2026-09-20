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
    private Long id;                    // 唯一标识符
    private String requestNo;           // 售后申请单号
    private Long orderId;               // 关联的订单ID
    private Long userId;                // 用户ID
    private String requestType;         // 申请类型（CANCEL/REFUND）
    private String reason;              // 申请原因
    private String status;              // 申请状态
    private Integer previousOrderStatus;    // 申请前的订单状态
    private Long reviewedBy;            // 审核员ID
    private String reviewReason;        // 审核意见
    private LocalDateTime reviewedAt;   // 审核时间
    private String refundNo;            // 退款单号
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime updateTime;   // 更新时间
}