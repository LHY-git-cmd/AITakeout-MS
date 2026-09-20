package com.sky.service.order.model;

/** 订单状态转换命令，显式携带期望状态和操作者。 */
public record OrderTransition(long orderId, int expectedStatus, int targetStatus,
                              String operatorType, Long operatorId, String reason) {
}
