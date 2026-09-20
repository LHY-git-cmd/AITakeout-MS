package com.sky.service.payment;

import com.sky.entity.Orders;
import com.sky.entity.PaymentTransaction;
import com.sky.entity.RefundTransaction;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.MockAccountMapper;
import com.sky.mapper.AfterSaleRequestMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.PaymentTransactionMapper;
import com.sky.mapper.RefundTransactionMapper;
import com.sky.service.account.LedgerTransferService;
import com.sky.service.account.model.AccountModels.FreezeCommand;
import com.sky.service.account.model.AccountModels.TransferCommand;
import com.sky.service.notification.NotificationService;
import com.sky.service.notification.model.NotificationModels.NotificationCommand;
import com.sky.service.notification.model.NotificationModels.NotificationType;
import com.sky.service.order.OrderTimelineService;
import com.sky.service.payment.model.RefundModels.RefundStatus;
import com.sky.service.payment.model.RefundModels.RefundView;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/** 整单退款应用服务，保证冻结、重试和返还均可幂等恢复。 */
@Service
@RequiredArgsConstructor
public class RefundApplicationService {
    private final RefundTransactionMapper refundMapper;
    private final AfterSaleRequestMapper afterSaleMapper;
    private final PaymentTransactionMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final MockAccountMapper accountMapper;
    private final LedgerTransferService ledgerTransferService;
    private final RefundGateway refundGateway;
    private final OrderTimelineService timelineService;
    private final NotificationService notificationService;

    @Transactional
    public RefundView createFullRefund(long orderId, String businessKey) {
        if (businessKey == null || businessKey.isBlank()) throw new IllegalArgumentException("退款业务键不能为空");
        RefundTransaction replay = refundMapper.findByBusinessKey(businessKey);
        if (replay != null) return view(replay);
        Orders order = orderMapper.getByIdForUpdate(orderId);
        if (order == null || !Orders.PAID.equals(order.getPayStatus())) throw new OrderBusinessException("订单当前不可退款");
        PaymentTransaction payment = paymentMapper.findLatestByOrderAndStatus(orderId, "SUCCEEDED");
        if (payment == null) throw new OrderBusinessException("未找到成功支付单");
        RefundTransaction existing = refundMapper.findLatestByOrder(orderId);
        if (existing != null) return view(existing);
        var source = accountMapper.findByTypeAndOwner(Orders.COMPLETED.equals(order.getStatus()) ? "MERCHANT" : "PLATFORM_PENDING", 0L);
        var target = accountMapper.findByTypeAndOwner("USER", order.getUserId());
        if (source == null || target == null) throw new IllegalStateException("退款账户不存在");
        LocalDateTime now = LocalDateTime.now();
        RefundTransaction refund = RefundTransaction.builder().refundNo("REF-" + UUID.randomUUID())
                .businessKey(businessKey).orderId(orderId).paymentId(payment.getId()).userId(order.getUserId())
                .sourceAccountId(source.getId()).targetAccountId(target.getId()).amountCent(payment.getAmountCent())
                .status("CREATED").attemptCount(0).version(0).createTime(now).updateTime(now).build();
        try {
            refundMapper.insert(refund);
        } catch (DuplicateKeyException exception) {
            RefundTransaction duplicate = refundMapper.findByBusinessKey(businessKey);
            if (duplicate != null) return view(duplicate);
            throw exception;
        }
        var freeze = ledgerTransferService.freeze(new FreezeCommand("REFUND_FREEZE:" + refund.getRefundNo(),
                source.getId(), refund.getAmountCent(), "REFUND_FREEZE"));
        if (refundMapper.updateState(refund.getId(), "CREATED", "PROCESSING", 1, freeze.transferId(), null,
                null, null, null, null, null, now) != 1) throw new IllegalStateException("退款状态并发冲突");
        timelineService.append(orderId, "REFUND_PROCESSING", refund.getRefundNo(), "退款处理中", "SYSTEM", null);
        return execute(refund.getRefundNo());
    }

    public RefundView query(long userId, String refundNo) {
        RefundTransaction refund = refundMapper.findByRefundNo(refundNo);
        if (refund == null || refund.getUserId() != userId) throw new OrderBusinessException("退款单不存在或无权访问");
        return view(refund);
    }

    @Transactional
    public RefundView retry(String refundNo) {
        RefundTransaction refund = refundMapper.findByRefundNoForUpdate(refundNo);
        if (refund == null) throw new OrderBusinessException("退款单不存在");
        if ("SUCCEEDED".equals(refund.getStatus())) return view(refund);
        if (!"FAILED".equals(refund.getStatus())) throw new OrderBusinessException("退款当前不可重试");
        LocalDateTime now = LocalDateTime.now();
        if (refundMapper.updateState(refund.getId(), "FAILED", "PROCESSING", refund.getAttemptCount() + 1,
                null, null, null, null, null, null, null, now) != 1) throw new IllegalStateException("退款重试并发冲突");
        if (refundGateway instanceof MockRefundGateway mock) mock.clearResult(refundNo);
        return execute(refundNo);
    }

    @Transactional
    protected RefundView execute(String refundNo) {
        RefundTransaction refund = refundMapper.findByRefundNoForUpdate(refundNo);
        if (refund == null || !"PROCESSING".equals(refund.getStatus())) return view(refund);
        PaymentTransaction payment = paymentMapper.findLatestByOrderAndStatus(refund.getOrderId(), "SUCCEEDED");
        var result = refundGateway.refund(refundNo, payment.getPaymentNo(), refund.getAmountCent());
        LocalDateTime now = LocalDateTime.now();
        if (!result.succeeded()) {
            LocalDateTime retryAt = now.plusMinutes(1L << Math.min(Math.max(refund.getAttemptCount() - 1, 0), 4));
            refundMapper.updateState(refund.getId(), "PROCESSING", "FAILED", refund.getAttemptCount(), null, null,
                    result.gatewayRefundNo(), result.failureCode(), result.failureMessage(), null, retryAt, now);
            afterSaleMapper.updateByRefundNo(refundNo, "REFUND_FAILED", now);
            timelineService.append(refund.getOrderId(), "REFUND_FAILED", refundNo, "退款失败，系统将继续处理", "SYSTEM", null);
            notify(refund, "退款失败", "退款暂未到账，系统将自动重试", "FAILED");
            return view(refundMapper.findByRefundNo(refundNo));
        }
        var transfer = ledgerTransferService.transferFrozen(new TransferCommand("REFUND_RELEASE:" + refundNo,
                refund.getSourceAccountId(), refund.getTargetAccountId(), refund.getAmountCent(), "ORDER_REFUND"));
        refundMapper.updateState(refund.getId(), "PROCESSING", "SUCCEEDED", refund.getAttemptCount(), null,
                transfer.transferId(), result.gatewayRefundNo(), null, null, now, null, now);
        afterSaleMapper.updateByRefundNo(refundNo, "COMPLETED", now);
        orderMapper.update(Orders.builder().id(refund.getOrderId()).payStatus(Orders.REFUND).build());
        timelineService.append(refund.getOrderId(), "REFUND_SUCCEEDED", refundNo, "退款已原路返回模拟余额", "SYSTEM", null);
        notify(refund, "退款成功", "退款已退回模拟余额", "SUCCEEDED");
        return view(refundMapper.findByRefundNo(refundNo));
    }

    private void notify(RefundTransaction refund, String title, String content, String suffix) {
        notificationService.record(new NotificationCommand("REFUND:" + refund.getRefundNo() + ":" + suffix,
                refund.getUserId(), NotificationType.REFUND, title, content, refund.getOrderId()));
    }

    private static RefundView view(RefundTransaction value) {
        if (value == null) throw new OrderBusinessException("退款单不存在");
        return new RefundView(value.getRefundNo(), value.getOrderId(), value.getAmountCent(),
                RefundStatus.valueOf(value.getStatus()), value.getAttemptCount(), value.getFailureCode(),
                value.getFailureMessage(), value.getSucceededAt(), value.getNextRetryAt());
    }
}
