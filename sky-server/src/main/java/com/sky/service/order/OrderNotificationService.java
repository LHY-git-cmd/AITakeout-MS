package com.sky.service.order;

import com.alibaba.fastjson.JSON;
import com.sky.entity.Orders;
import com.sky.websocket.WebSocketServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单通知服务
 * 负责通过WebSocket推送订单相关消息，包括来单提醒、状态变更通知、催单提醒等
 * 支持事务提交后再发送通知，保证数据一致性
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderNotificationService {

    private final WebSocketServer webSocketServer;

    /**
     * 事务提交后发送新订单提醒给所有管理端客户端
     *
     * @param order 订单实体
     */
    public void sendNewOrderAfterCommit(Orders order) {
        runAfterCommit(() -> {
            Map<String, Object> message = new HashMap<>();
            message.put("type", 1); // 来单提醒
            message.put("orderId", order.getId());
            message.put("content", "订单号：" + order.getNumber());
            webSocketServer.sendToAllClient(JSON.toJSONString(message));
            log.info("已推送来单提醒：orderId={}, orderNumber={}", order.getId(), order.getNumber());
        });
    }

    /**
     * 事务提交后发送订单状态变更通知给指定用户
     *
     * @param order   订单实体
     * @param status  订单状态
     * @param content 通知内容
     */
    public void sendStatusAfterCommit(Orders order, Integer status, String content) {
        runAfterCommit(() -> {
            webSocketServer.sendOrderStatusToUser(order.getUserId(), order.getId(), status, content);
            log.info("已推送订单状态：userId={}, orderId={}, status={}",
                    order.getUserId(), order.getId(), status);
        });
    }

    /**
     * 发送催单提醒给所有管理端客户端
     * 催单消息在事务外直接发送
     *
     * @param order 订单实体
     */
    public void sendReminder(Orders order) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", 2); // 催单提醒
        message.put("orderId", order.getId());
        message.put("content", "订单号：" + order.getNumber());
        webSocketServer.sendToAllClient(JSON.toJSONString(message));
    }

    /**
     * 在事务提交后执行指定操作
     * 如果当前没有活跃事务，则立即执行
     *
     * @param action 要执行的操作
     */
    private void runAfterCommit(Runnable action) {
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