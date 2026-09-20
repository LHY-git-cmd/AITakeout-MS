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
    private Long id;
    private String eventNo;
    private Long orderId;
    private String eventType;
    private String businessNo;
    private String displayMessage;
    private String operatorType;
    private Long operatorId;
    private String payloadJson;
    private LocalDateTime eventTime;
    private LocalDateTime createTime;
}
