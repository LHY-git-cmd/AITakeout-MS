package com.sky.service.payment;

import com.sky.entity.OrderTimelineEvent;
import com.sky.entity.Orders;
import com.sky.entity.PaymentTransaction;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.OrderTimelineEventMapper;
import com.sky.mapper.PaymentTransactionMapper;
import com.sky.service.payment.model.PaymentModels.GatewayResult;
import com.sky.service.payment.model.PaymentModels.PaymentStatus;
import com.sky.service.payment.model.PaymentModels.PaymentView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/** 支付创建、归属查询、渠道结果对账和超时关闭的应用服务。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentApplicationService {
    private static final int MAX_IDEMPOTENCY_LENGTH = 80;
    private final PaymentTransactionMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final OrderTimelineEventMapper timelineMapper;
    private final PaymentGateway gateway;
    private final PaymentSettlementService settlementService;
    private final PaymentStateService stateService;

    @Transactional
    public PaymentView create(long userId, long orderId, String idempotencyKey) {
        validateCreate(userId, orderId, idempotencyKey);
        String scopedKey = userId + ":" + orderId + ":" + idempotencyKey.trim();
        PaymentTransaction replay = paymentMapper.findByIdempotencyKey(scopedKey);
        if (replay != null) return view(replay);

        Orders order = orderMapper.getByIdForUpdate(orderId);
        if (order == null) throw new OrderBusinessException("订单不存在");
        if (!Long.valueOf(userId).equals(order.getUserId())) throw new OrderBusinessException("无权支付该订单");
        PaymentTransaction succeeded = paymentMapper.findLatestByOrderAndStatus(orderId, "SUCCEEDED");
        if (succeeded != null) return view(succeeded);
        PaymentTransaction processing = paymentMapper.findLatestByOrderAndStatus(orderId, "PROCESSING");
        if (processing != null) return view(processing);
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus()) || !Orders.UN_PAID.equals(order.getPayStatus())) {
            throw new OrderBusinessException("当前订单不可支付");
        }
        long amountCent;
        try {
            amountCent = order.getAmount().movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (RuntimeException ex) {
            throw new OrderBusinessException("订单金额异常");
        }
        if (amountCent <= 0) throw new OrderBusinessException("订单金额异常");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = order.getOrderTime() == null ? now.plusMinutes(15) : order.getOrderTime().plusMinutes(15);
        if (!expiresAt.isAfter(now)) throw new OrderBusinessException("订单支付已超时");
        PaymentTransaction payment = PaymentTransaction.builder().paymentNo("PAY-" + UUID.randomUUID())
                .orderId(orderId).userId(userId).channel(gateway.channel()).idempotencyKey(scopedKey)
                .amountCent(amountCent).status("CREATED").expiresAt(expiresAt).version(0)
                .createTime(now).updateTime(now).build();
        try {
            paymentMapper.insert(payment);
        } catch (DuplicateKeyException ex) {
            PaymentTransaction existing = paymentMapper.findByIdempotencyKey(scopedKey);
            if (existing != null) return view(existing);
            throw ex;
        }
        GatewayResult result = gateway.process(payment.getPaymentNo());
        if (result.status() != PaymentStatus.PROCESSING) throw new IllegalStateException("支付渠道未进入处理中");
        if (paymentMapper.updateState(payment.getId(), "CREATED", "PROCESSING", result.gatewayTradeNo(),
                result.eventId(), result.failureCode(), null, LocalDateTime.now()) != 1) {
            throw new IllegalStateException("支付创建并发冲突");
        }
        return view(paymentMapper.findByPaymentNo(payment.getPaymentNo()));
    }

    public PaymentView query(long userId, String paymentNo) {
        PaymentTransaction payment = paymentMapper.findByPaymentNo(paymentNo);
        if (payment == null || payment.getUserId() == null || payment.getUserId() != userId) {
            throw new OrderBusinessException("支付单不存在或无权访问");
        }
        return view(payment);
    }

    public void reconcile(String paymentNo) {
        PaymentTransaction payment = paymentMapper.findByPaymentNo(paymentNo);
        if (payment == null || !"PROCESSING".equals(payment.getStatus())) return;
        GatewayResult result = gateway.query(payment.getGatewayTradeNo());
        if (result.status() == PaymentStatus.SUCCEEDED) {
            try {
                settlementService.settle(paymentNo, result);
            } catch (IllegalStateException ex) {
                stateService.fail(paymentNo, result, failureCode(ex));
            }
        } else if (result.status() == PaymentStatus.FAILED) {
            stateService.fail(paymentNo, result,
                    result.failureCode() == null ? "GATEWAY_FAILED" : result.failureCode());
        }
    }

    public void reconcileBatch(int batchSize) {
        paymentMapper.findProcessing(requireBatchSize(batchSize)).forEach(payment -> {
            try {
                reconcile(payment.getPaymentNo());
            } catch (RuntimeException ex) {
                log.error("支付渠道对账失败：paymentNo={}", payment.getPaymentNo(), ex);
            }
        });
    }

    @Transactional
    public int expireBatch(int batchSize) {
        LocalDateTime now = LocalDateTime.now();
        int expired = 0;
        for (PaymentTransaction payment : paymentMapper.findExpiredForUpdate(now, requireBatchSize(batchSize))) {
            if (paymentMapper.updateState(payment.getId(), payment.getStatus(), "CLOSED", payment.getGatewayTradeNo(),
                    payment.getCallbackEventId(), "PAYMENT_TIMEOUT", null, now) != 1) continue;
            Orders order = orderMapper.getByIdForUpdate(payment.getOrderId());
            if (order != null && Orders.PENDING_PAYMENT.equals(order.getStatus())) {
                Orders cancel = Orders.builder().id(order.getId()).status(Orders.CANCELLED)
                        .cancelReason("订单超时，自动取消").cancelTime(now).build();
                orderMapper.updateByExpectedStatus(cancel, Orders.PENDING_PAYMENT);
            }
            timelineMapper.insert(event(payment, "PAYMENT_CLOSED", "支付超时，订单已关闭", now));
            if (payment.getGatewayTradeNo() != null) gateway.close(payment.getGatewayTradeNo());
            expired++;
        }
        return expired;
    }

    private static String failureCode(IllegalStateException ex) {
        return "余额不足".equals(ex.getMessage()) ? "INSUFFICIENT_BALANCE" : "SETTLEMENT_FAILED";
    }

    private static int requireBatchSize(int batchSize) {
        if (batchSize < 1 || batchSize > 500) throw new IllegalArgumentException("批量大小无效");
        return batchSize;
    }

    private static void validateCreate(long userId, long orderId, String key) {
        if (userId <= 0 || orderId <= 0) throw new IllegalArgumentException("支付主体无效");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("幂等键不能为空");
        if (key.trim().length() > MAX_IDEMPOTENCY_LENGTH) throw new IllegalArgumentException("幂等键过长");
    }

    private static PaymentView view(PaymentTransaction payment) {
        return new PaymentView(payment.getPaymentNo(), payment.getOrderId(), PaymentStatus.valueOf(payment.getStatus()),
                payment.getAmountCent(), payment.getExpiresAt(), payment.getFailureCode());
    }

    private static OrderTimelineEvent event(PaymentTransaction payment, String type,
                                            String message, LocalDateTime now) {
        return OrderTimelineEvent.builder().eventNo(type + ":" + payment.getPaymentNo())
                .orderId(payment.getOrderId()).eventType(type).businessNo(payment.getPaymentNo())
                .displayMessage(message).operatorType("SYSTEM").eventTime(now).createTime(now).build();
    }
}
