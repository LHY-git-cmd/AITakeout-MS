package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单定时处理任务
 * 用于处理需要系统自动处理的订单场景，如超时未支付订单自动取消、派送超时订单自动完成等
 */
@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理超时未支付订单
     * 每分钟执行一次（cron: 秒 分 时 日 月 星期）
     * 查询所有下单超过15分钟且仍处于"待付款"状态的订单，自动将其取消
     */
    @Scheduled(cron = "0 * * * * ?")
    @Transactional
    public void processTimeoutOrder() {
        // 获取当前时间，计算超时阈值（当前时间减去15分钟）
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime timeoutThreshold = now.minusMinutes(15);

        // 查询所有状态为"待付款"且下单时间早于超时阈值的订单
        List<Orders> timeoutOrders = orderMapper.getByStatusAndOrderTimeLT(
                Orders.PENDING_PAYMENT, timeoutThreshold);

        // 遍历超时订单，逐一更新订单状态为"已取消"
        for (Orders order : timeoutOrders) {
            Orders updateOrder = Orders.builder()
                    .id(order.getId())
                    .status(Orders.CANCELLED)
                    .cancelReason("订单超时，自动取消")
                    .cancelTime(now)
                    .build();
            orderMapper.update(updateOrder);
        }

        // 记录日志
        if (!timeoutOrders.isEmpty()) {
            log.info("定时任务已取消超时未支付订单：count={}", timeoutOrders.size());
        }
    }

    /**
     * 处理派送超时订单
     * 每天凌晨1点执行
     * 查询所有下单超过1小时且仍处于"派送中"状态的订单，自动将其完成
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void processDeliveryOrder() {
        // 获取当前时间，计算派送超时阈值（当前时间减去1小时）
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deliveryThreshold = now.minusHours(1);

        // 查询所有状态为"派送中"且下单时间早于派送超时阈值的订单
        List<Orders> deliveryOrders = orderMapper.getByStatusAndOrderTimeLT(
                Orders.DELIVERY_IN_PROGRESS, deliveryThreshold);

        // 遍历派送超时订单，逐一更新订单状态为"已完成"
        for (Orders order : deliveryOrders) {
            Orders updateOrder = Orders.builder()
                    .id(order.getId())
                    .status(Orders.COMPLETED)
                    .deliveryTime(now)
                    .build();
            orderMapper.update(updateOrder);
        }



        // 记录日志
        if (!deliveryOrders.isEmpty()) {
            log.info("定时任务已自动完成派送中订单：count={}", deliveryOrders.size());
        }
    }
}