package com.sky.service.order;

import com.sky.entity.OrderTimelineEvent;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.OrderTimelineEventMapper;
import com.sky.vo.OrderTimelineVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 订单时间轴的统一追加和用户归属查询服务。 */
@Service
@RequiredArgsConstructor
public class OrderTimelineService {
    private final OrderTimelineEventMapper timelineMapper;
    private final OrderMapper orderMapper;

    public void append(long orderId, String type, String businessNo, String message,
                       String operatorType, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        timelineMapper.insert(OrderTimelineEvent.builder().eventNo("EVT-" + UUID.randomUUID())
                .orderId(orderId).eventType(type).businessNo(businessNo).displayMessage(message)
                .operatorType(operatorType).operatorId(operatorId).eventTime(now).createTime(now).build());
    }

    public List<OrderTimelineVO> timeline(long userId, long orderId) {
        Orders order = orderMapper.getById(orderId);
        if (order == null || !Long.valueOf(userId).equals(order.getUserId())) {
            throw new OrderBusinessException("订单不存在或无权访问");
        }
        return timelineMapper.findByOrderId(orderId).stream()
                .map(event -> OrderTimelineVO.builder().id(event.getId()).eventType(event.getEventType())
                        .message(event.getDisplayMessage()).operatorType(event.getOperatorType())
                        .eventTime(event.getEventTime()).build()).toList();
    }
}
