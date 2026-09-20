package com.sky.service.order;

import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.AfterSaleRequestMapper;
import com.sky.mapper.MockAccountMapper;
import com.sky.mapper.OrderMapper;
import com.sky.service.account.LedgerTransferService;
import com.sky.service.account.model.AccountModels.TransferCommand;
import com.sky.service.notification.NotificationService;
import com.sky.service.notification.model.NotificationModels.NotificationCommand;
import com.sky.service.notification.model.NotificationModels.NotificationType;
import com.sky.service.order.model.OrderTransition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/** 集中校验订单状态转换，并在同一事务内写时间轴及完成结算。 */
@Service
@RequiredArgsConstructor
public class OrderStateMachine {
    private static final Map<Integer, String> STATUS_MESSAGES = Map.of(
            Orders.PENDING_PAYMENT, "等待付款", Orders.TO_BE_CONFIRMED, "支付成功，等待商家接单",
            Orders.CONFIRMED, "商家已接单", Orders.DELIVERY_IN_PROGRESS, "订单开始配送",
            Orders.COMPLETED, "订单已送达", Orders.CANCELLED, "订单已取消");

    private final OrderMapper orderMapper;
    private final AfterSaleRequestMapper afterSaleMapper;
    private final MockAccountMapper accountMapper;
    private final LedgerTransferService ledgerTransferService;
    private final OrderTimelineService timelineService;
    private final NotificationService notificationService;

    @Transactional
    public Orders transition(OrderTransition command) {
        Orders order = orderMapper.getByIdForUpdate(command.orderId());
        if (order == null) throw new OrderBusinessException("订单不存在");
        if (!Integer.valueOf(command.expectedStatus()).equals(order.getStatus())) {
            throw new OrderBusinessException("订单状态已变化，请刷新后重试");
        }
        requireAllowed(command.expectedStatus(), command.targetStatus());
        if ((command.targetStatus() == Orders.DELIVERY_IN_PROGRESS || command.targetStatus() == Orders.COMPLETED)
                && afterSaleMapper.findActiveByOrderId(order.getId()) != null) {
            throw new OrderBusinessException("取消申请处理中，暂不能继续履约");
        }
        LocalDateTime now = LocalDateTime.now();
        Orders update = Orders.builder().id(order.getId()).status(command.targetStatus()).build();
        if (command.targetStatus() == Orders.CANCELLED) {
            update.setCancelReason(command.reason());
            update.setCancelTime(now);
        }
        if (command.targetStatus() == Orders.COMPLETED) update.setDeliveryTime(now);
        if (command.targetStatus() == Orders.COMPLETED) settleToMerchant(order);
        if (orderMapper.updateByExpectedStatus(update, command.expectedStatus()) != 1) {
            throw new OrderBusinessException("订单状态已变化，请刷新后重试");
        }
        String message = STATUS_MESSAGES.getOrDefault(command.targetStatus(), "订单状态已更新");
        timelineService.append(order.getId(), "ORDER_STATUS_" + command.targetStatus(), order.getNumber(),
                message, command.operatorType(), command.operatorId());
        notificationService.record(new NotificationCommand("ORDER_STATUS:" + order.getId() + ":" + command.targetStatus(),
                order.getUserId(), NotificationType.ORDER, "订单状态更新", message, order.getId()));
        order.setStatus(command.targetStatus());
        if (command.targetStatus() == Orders.COMPLETED) order.setDeliveryTime(now);
        return order;
    }

    private void settleToMerchant(Orders order) {
        if (!Orders.PAID.equals(order.getPayStatus())) throw new OrderBusinessException("未支付订单不能完成");
        var pending = accountMapper.findByTypeAndOwner("PLATFORM_PENDING", 0L);
        var merchant = accountMapper.findByTypeAndOwner("MERCHANT", 0L);
        if (pending == null || merchant == null) throw new IllegalStateException("结算账户不存在");
        long amountCent = order.getAmountCent() == null ? order.getAmount().movePointRight(2).longValueExact() : order.getAmountCent();
        ledgerTransferService.transfer(new TransferCommand("ORDER_SETTLEMENT:" + order.getId(), pending.getId(),
                merchant.getId(), amountCent, "ORDER_SETTLEMENT"));
    }

    private static void requireAllowed(int source, int target) {
        boolean allowed = (source == Orders.TO_BE_CONFIRMED && target == Orders.CONFIRMED)
                || (source == Orders.CONFIRMED && target == Orders.DELIVERY_IN_PROGRESS)
                || (source == Orders.DELIVERY_IN_PROGRESS && target == Orders.COMPLETED)
                || (target == Orders.CANCELLED && source != Orders.COMPLETED && source != Orders.CANCELLED);
        if (!allowed) throw new OrderBusinessException("不允许的订单状态转换");
    }
}
