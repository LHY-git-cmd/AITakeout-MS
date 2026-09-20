package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 订单、支付和退款的时间轴事件。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimelineEvent {
    private Long id;                    // 唯一标识符
    private String eventNo;             // 事件单号
    private Long orderId;               // 订单ID
    private String eventType;           // 事件类型
    private String businessNo;          // 业务单号
    private String displayMessage;      // 显示信息
    private String operatorType;        // 操作员类型
    private Long operatorId;            // 操作员ID
    private String payloadJson;         // 附带的JSON数据
    private LocalDateTime eventTime;    // 事件时间
    private LocalDateTime createTime;   // 创建时间
}