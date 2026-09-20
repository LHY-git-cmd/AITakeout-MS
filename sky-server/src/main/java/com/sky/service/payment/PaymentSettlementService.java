package com.sky.service.payment;

import com.sky.entity.OrderTimelineEvent;
import com.sky.entity.Orders;
import com.sky.entity.PaymentTransaction;
import com.sky.mapper.MockAccountMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.OrderTimelineEventMapper;
import com.sky.mapper.PaymentTransactionMapper;
import com.sky.service.account.LedgerTransferService;
import com.sky.service.account.model.AccountModels.TransferCommand;
import com.sky.service.order.OrderNotificationService;
import com.sky.service.payment.model.PaymentModels.GatewayResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 将渠道成功结果原子结算到账本、支付单、订单和时间轴。 */
@Service
@RequiredArgsConstructor
public class PaymentSettlementService {
    private final PaymentTransactionMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final MockAccountMapper accountMapper;
    private final LedgerTransferService ledgerTransferService;
    private final OrderTimelineEventMapper timelineMapper;
    private final OrderNotificationService notificationService;

    @Transactional
    public PaymentTransaction settle(String paymentNo, GatewayResult result) {
        PaymentTransaction payment = paymentMapper.findByPaymentNoForUpdate(paymentNo);
        if (payment == null) throw new IllegalStateException("支付单不存在");
        if ("SUCCEEDED".equals(payment.getStatus()) || "CLOSED".equals(payment.getStatus())) return payment;
        if (!"PROCESSING".equals(payment.getStatus())) return payment;

        Orders order = orderMapper.getByIdForUpdate(payment.getOrderId());
        if (order == null || !Orders.PENDING_PAYMENT.equals(order.getStatus())
                || !Orders.UN_PAID.equals(order.getPayStatus())) {
            throw new IllegalStateException("订单已不可支付");
        }
        var userAccount = accountMapper.findByTypeAndOwner("USER", payment.getUserId());
        var pendingAccount = accountMapper.findByTypeAndOwner("PLATFORM_PENDING", 0L);
        if (userAccount == null || pendingAccount == null) throw new IllegalStateException("支付账户不存在");

        ledgerTransferService.transfer(new TransferCommand("PAYMENT:" + paymentNo,
                userAccount.getId(), pendingAccount.getId(), payment.getAmountCent(), "ORDER_PAYMENT"));
        LocalDateTime now = LocalDateTime.now();
        Orders update = Orders.builder().id(order.getId()).status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID).checkoutTime(now).build();
        if (orderMapper.updatePaymentByExpectedStatus(update, Orders.PENDING_PAYMENT, Orders.UN_PAID) != 1) {
            throw new IllegalStateException("订单支付状态并发冲突");
        }
        if (paymentMapper.updateState(payment.getId(), "PROCESSING", "SUCCEEDED", result.gatewayTradeNo(),
                result.eventId(), null, now, now) != 1) {
            throw new IllegalStateException("支付状态并发冲突");
        }
        timelineMapper.insert(event(order.getId(), paymentNo, "PAYMENT_SUCCEEDED", "支付成功", now));
        notificationService.sendNewOrderAfterCommit(order);
        notificationService.sendStatusAfterCommit(order, Orders.TO_BE_CONFIRMED, "支付成功，等待商家接单");
        return paymentMapper.findByPaymentNo(paymentNo);
    }

    private static OrderTimelineEvent event(long orderId, String paymentNo, String type,
                                            String message, LocalDateTime now) {
        return OrderTimelineEvent.builder().eventNo(type + ":" + paymentNo).orderId(orderId)
                .eventType(type).businessNo(paymentNo).displayMessage(message).operatorType("SYSTEM")
                .eventTime(now).createTime(now).build();
    }
}
