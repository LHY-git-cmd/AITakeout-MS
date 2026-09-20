package com.sky.service.aftersale;

import com.sky.dto.AfterSaleApplyDTO;
import com.sky.dto.AfterSaleReviewDTO;
import com.sky.entity.AfterSaleRequest;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.AfterSaleRequestMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.RefundTransactionMapper;
import com.sky.service.notification.NotificationService;
import com.sky.service.notification.model.NotificationModels.NotificationCommand;
import com.sky.service.notification.model.NotificationModels.NotificationType;
import com.sky.service.order.OrderTimelineService;
import com.sky.service.payment.RefundApplicationService;
import com.sky.service.payment.model.RefundModels.RefundView;
import com.sky.vo.AfterSaleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 取消与售后规则编排服务。 */
@Service
@RequiredArgsConstructor
public class AfterSaleService {
    private final AfterSaleRequestMapper requestMapper;
    private final OrderMapper orderMapper;
    private final RefundTransactionMapper refundMapper;
    private final RefundApplicationService refundService;
    private final OrderTimelineService timelineService;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional
    public AfterSaleVO apply(long userId, long orderId, AfterSaleApplyDTO dto, String idempotencyKey) {
        Orders order = orderMapper.getByIdForUpdate(orderId);
        if (order == null || !Long.valueOf(userId).equals(order.getUserId())) throw new OrderBusinessException("订单不存在或无权访问");
        validateApply(order, dto);
        AfterSaleRequest existing = requestMapper.findActiveByOrderId(orderId);
        if (existing != null) return view(existing);
        LocalDateTime now = LocalDateTime.now(clock);
        String requestType = Orders.COMPLETED.equals(order.getStatus()) || Orders.DELIVERY_IN_PROGRESS.equals(order.getStatus())
                ? "AFTER_SALE" : "CANCELLATION";
        String status = Orders.PENDING_PAYMENT.equals(order.getStatus()) ? "COMPLETED"
                : Orders.TO_BE_CONFIRMED.equals(order.getStatus()) ? "APPROVED" : "PENDING";
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? UUID.randomUUID().toString() : idempotencyKey.trim();
        AfterSaleRequest request = AfterSaleRequest.builder().requestNo("AS-" + UUID.nameUUIDFromBytes((userId + ":" + orderId + ":" + key).getBytes()))
                .orderId(orderId).userId(userId).requestType(requestType).reason(dto.getReason().trim())
                .status(status).previousOrderStatus(order.getStatus()).createTime(now).updateTime(now).build();
        try {
            requestMapper.insert(request);
        } catch (DuplicateKeyException exception) {
            AfterSaleRequest replay = requestMapper.findActiveByOrderId(orderId);
            if (replay != null) return view(replay);
            throw exception;
        }
        timelineService.append(orderId, "AFTER_SALE_APPLIED", request.getRequestNo(),
                "已提交" + ("CANCELLATION".equals(requestType) ? "取消" : "售后") + "申请", "USER", userId);
        if (Orders.PENDING_PAYMENT.equals(order.getStatus())) {
            cancelOrder(order, dto.getReason());
            notifyStatus(request, "订单已取消", "待付款订单已取消，无需退款", "COMPLETED");
        } else if (Orders.TO_BE_CONFIRMED.equals(order.getStatus())) {
            cancelOrder(order, dto.getReason());
            completeRefund(request, refundService.createFullRefund(orderId, "AFTER_SALE:" + request.getRequestNo()));
        } else {
            notifyStatus(request, "申请已提交", "商家将在审核后通知处理结果", "PENDING");
        }
        return view(requestMapper.findById(request.getId()));
    }

    @Transactional
    public AfterSaleVO review(long operatorId, long requestId, AfterSaleReviewDTO dto) {
        AfterSaleRequest request = requestMapper.findByIdForUpdate(requestId);
        if (request == null) throw new OrderBusinessException("售后申请不存在");
        if (!"PENDING".equals(request.getStatus())) return view(request);
        if (!Boolean.TRUE.equals(dto.getApproved()) && (dto.getReason() == null || dto.getReason().isBlank())) {
            throw new OrderBusinessException("拒绝原因不能为空");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        request.setStatus(Boolean.TRUE.equals(dto.getApproved()) ? "APPROVED" : "REJECTED");
        request.setReviewedBy(operatorId);
        request.setReviewReason(dto.getReason() == null ? null : dto.getReason().trim());
        request.setReviewedAt(now);
        request.setUpdateTime(now);
        if (requestMapper.updateReview(request) != 1) throw new OrderBusinessException("售后申请已被处理");
        if (!Boolean.TRUE.equals(dto.getApproved())) {
            timelineService.append(request.getOrderId(), "AFTER_SALE_REJECTED", request.getRequestNo(), "售后申请未通过", "ADMIN", operatorId);
            notifyStatus(request, "售后申请未通过", request.getReviewReason(), "REJECTED");
            return view(requestMapper.findById(requestId));
        }
        Orders order = orderMapper.getByIdForUpdate(request.getOrderId());
        if (!Orders.COMPLETED.equals(order.getStatus())) cancelOrder(order, "售后审核通过");
        timelineService.append(request.getOrderId(), "AFTER_SALE_APPROVED", request.getRequestNo(), "售后申请已通过", "ADMIN", operatorId);
        completeRefund(request, refundService.createFullRefund(request.getOrderId(), "AFTER_SALE:" + request.getRequestNo()));
        return view(requestMapper.findById(requestId));
    }

    public AfterSaleVO getForUser(long userId, long orderId) {
        AfterSaleRequest request = requestMapper.findLatestByOrderAndUser(orderId, userId);
        if (request == null) throw new OrderBusinessException("售后申请不存在");
        return view(request);
    }

    public List<AfterSaleVO> listForUser(long userId, Long beforeId, int limit) {
        return requestMapper.listByUser(userId, beforeId, safeLimit(limit)).stream().map(this::view).toList();
    }

    public List<AfterSaleVO> listForAdmin(String status, Long beforeId, int limit) {
        return requestMapper.listForAdmin(status, beforeId, safeLimit(limit)).stream().map(this::view).toList();
    }

    public AfterSaleVO getForAdmin(long id) {
        AfterSaleRequest request = requestMapper.findById(id);
        if (request == null) throw new OrderBusinessException("售后申请不存在");
        return view(request);
    }

    private void validateApply(Orders order, AfterSaleApplyDTO dto) {
        if (dto == null || dto.getReason() == null || dto.getReason().isBlank()) throw new OrderBusinessException("申请原因不能为空");
        if (!List.of(Orders.PENDING_PAYMENT, Orders.TO_BE_CONFIRMED, Orders.CONFIRMED,
                Orders.DELIVERY_IN_PROGRESS, Orders.COMPLETED).contains(order.getStatus())) {
            throw new OrderBusinessException("当前订单状态不能申请取消或售后");
        }
        if (Orders.COMPLETED.equals(order.getStatus())) {
            if (order.getDeliveryTime() == null || !LocalDateTime.now(clock).isBefore(order.getDeliveryTime().plusHours(24))) {
                throw new OrderBusinessException("售后申请已超过24小时");
            }
        }
    }

    private void cancelOrder(Orders order, String reason) {
        if (Orders.CANCELLED.equals(order.getStatus())) return;
        Orders update = Orders.builder().id(order.getId()).status(Orders.CANCELLED)
                .cancelReason(reason).cancelTime(LocalDateTime.now(clock)).build();
        if (orderMapper.updateByExpectedStatus(update, order.getStatus()) != 1) throw new OrderBusinessException("订单状态已变化，请刷新后重试");
        timelineService.append(order.getId(), "ORDER_CANCELLED", order.getNumber(), "订单已取消", "SYSTEM", null);
    }

    private void completeRefund(AfterSaleRequest request, RefundView refund) {
        String status = refund.status().name().equals("SUCCEEDED") ? "COMPLETED" : "REFUND_FAILED";
        requestMapper.updateRefund(request.getId(), "APPROVED", status, refund.refundNo(), LocalDateTime.now(clock));
        notifyStatus(request, status.equals("COMPLETED") ? "退款成功" : "退款处理中",
                status.equals("COMPLETED") ? "退款已返回模拟余额" : "退款暂未到账，系统将继续处理", status);
    }

    private void notifyStatus(AfterSaleRequest request, String title, String content, String suffix) {
        notificationService.record(new NotificationCommand("AFTER_SALE:" + request.getRequestNo() + ":" + suffix,
                request.getUserId(), NotificationType.AFTER_SALE, title, content, request.getOrderId()));
    }

    private AfterSaleVO view(AfterSaleRequest request) {
        var refund = request.getRefundNo() == null ? null : refundMapper.findByRefundNo(request.getRefundNo());
        return AfterSaleVO.builder().id(request.getId()).requestNo(request.getRequestNo()).orderId(request.getOrderId())
                .userId(request.getUserId()).requestType(request.getRequestType()).reason(request.getReason())
                .status(request.getStatus()).reviewReason(request.getReviewReason()).refundNo(request.getRefundNo())
                .refundAmountCent(refund == null ? null : refund.getAmountCent()).refundStatus(refund == null ? null : refund.getStatus())
                .createTime(request.getCreateTime()).reviewedAt(request.getReviewedAt()).build();
    }

    private static int safeLimit(int limit) { return Math.max(1, Math.min(limit, 50)); }
}
