package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.properties.OrderTaskProperties;
import com.sky.websocket.WebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单定时处理任务
 * 用于处理需要系统自动处理的订单场景，如超时未支付订单自动取消、派送超时订单自动完成等
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderTask {
    private final OrderMapper orderMapper;
    private final WebSocketServer webSocketServer;
    private final OrderTaskProperties orderTaskProperties;

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
        List<Orders> transitionedOrders = orderMapper.getBatchForUpdate(
                Orders.PENDING_PAYMENT, timeoutThreshold, orderTaskProperties.getBatchSize());
        if (transitionedOrders.isEmpty()) {
            return;
        }
        Orders updateOrder = Orders.builder()
                .status(Orders.CANCELLED)
                .cancelReason("订单超时，自动取消")
                .cancelTime(now)
                .build();
        updateBatchOrThrow(updateOrder, transitionedOrders, Orders.PENDING_PAYMENT);

        // 记录日志
        if (!transitionedOrders.isEmpty()) {
            sendStatusAfterCommit(transitionedOrders, Orders.CANCELLED, "订单超时，已自动取消");
            log.info("定时任务已取消超时未支付订单：count={}", transitionedOrders.size());
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
        List<Orders> transitionedOrders = orderMapper.getBatchForUpdate(
                Orders.DELIVERY_IN_PROGRESS, deliveryThreshold, orderTaskProperties.getBatchSize());
        if (transitionedOrders.isEmpty()) {
            return;
        }
        Orders updateOrder = Orders.builder()
                .status(Orders.COMPLETED)
                .deliveryTime(now)
                .build();
        updateBatchOrThrow(updateOrder, transitionedOrders, Orders.DELIVERY_IN_PROGRESS);



        // 记录日志
        if (!transitionedOrders.isEmpty()) {
            sendStatusAfterCommit(transitionedOrders, Orders.COMPLETED, "订单已自动完成");
            log.info("定时任务已自动完成派送中订单：count={}", transitionedOrders.size());
        }
    }

    /**
     * 批量更新订单状态，失败则抛出异常
     *
     * @param updateOrder    更新内容
     * @param orders         订单列表
     * @param expectedStatus 预期当前状态（乐观并发控制）
     */
    private void updateBatchOrThrow(Orders updateOrder, List<Orders> orders, Integer expectedStatus) {
        List<Long> ids = orders.stream().map(Orders::getId).collect(Collectors.toList());
        int updated = orderMapper.updateBatchByExpectedStatus(updateOrder, ids, expectedStatus);
        if (updated != ids.size()) {
            throw new IllegalStateException("订单批量状态更新数量不一致");
        }
    }

    /**
     * 事务提交后发送订单状态变更通知给所有相关用户
     *
     * @param orders  订单列表
     * @param status  订单状态
     * @param content 通知内容
     */
    private void sendStatusAfterCommit(List<Orders> orders, Integer status, String content) {
        Runnable action = () -> orders.forEach(order -> webSocketServer.sendOrderStatusToUser(
                order.getUserId(), order.getId(), status, content));
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}