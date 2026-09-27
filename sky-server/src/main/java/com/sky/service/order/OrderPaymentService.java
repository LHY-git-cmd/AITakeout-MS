package com.sky.service.order;

import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.entity.Orders;
import com.sky.entity.User;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.PaymentTransactionMapper;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.service.payment.PaymentApplicationService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单支付服务
 * 负责订单支付、支付回调处理、退款等支付相关操作
 * 支持模拟支付（开发环境）和微信支付两种模式
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderPaymentService {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final WeChatPayUtil weChatPayUtil;
    private final WeChatProperties weChatProperties;
    private final OrderNotificationService notificationService;
    private final PaymentTransactionMapper paymentTransactionMapper;
    private final PaymentApplicationService paymentApplicationService;
    private final com.sky.service.payment.RefundApplicationService refundApplicationService;

    /** 支付处理中禁止取消，避免取消与渠道成功结果竞争。 */
    public void assertCancelable(Long orderId) {
        if (paymentTransactionMapper.findLatestByOrderAndStatus(orderId, "PROCESSING") != null) {
            throw new OrderBusinessException("支付处理中，暂不能取消订单");
        }
    }

    /**
     * 订单支付
     * 支持模拟支付和真实微信支付两种模式
     *
     * @param request 支付请求信息
     * @param userId  用户ID
     * @return 支付视图对象（包含支付参数）
     * @throws Exception 支付异常
     */
    @Transactional
    public OrderPaymentVO payment(OrdersPaymentDTO request, Long userId) throws Exception {
        Orders order = getPayableOrder(request, userId);
        // 开发环境模拟支付
        if (Boolean.TRUE.equals(weChatProperties.getMockPay())) {
            var payment = paymentApplicationService.create(userId, order.getId(), "LEGACY:" + order.getNumber());
            log.info("模拟支付已受理：userId={}, orderNumber={}, paymentNo={}",
                    userId, order.getNumber(), payment.paymentNo());
            return OrderPaymentVO.builder().mockPay(true)
                    .timeStamp(String.valueOf(System.currentTimeMillis() / 1000)).nonceStr("mock")
                    .signType("MOCK").packageStr(payment.paymentNo()).paySign("processing").build();
        }
        // 真实微信支付
        User user = userMapper.getById(userId);
        if (user == null || user.getOpenid() == null) {
            throw new OrderBusinessException("当前用户不存在或未绑定微信账号");
        }
        JSONObject result = weChatPayUtil.pay(order.getNumber(), order.getAmount(),
                "饱饱点餐订单", user.getOpenid());
        if ("ORDERPAID".equals(result.getString("code"))) {
            throw new OrderBusinessException("该订单已支付");
        }
        OrderPaymentVO vo = result.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(result.getString("package"));
        return vo;
    }

    /**
     * 支付成功回调处理
     * 使用乐观并发控制保证幂等性，避免重复处理
     *
     * @param orderNumber 订单号
     */
    @Transactional
    public void paySuccess(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) throw new OrderBusinessException("订单号不能为空");
        Orders existing = orderMapper.getByNumber(orderNumber);
        if (existing == null) throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        // 幂等：已支付则直接返回
        if (Orders.PAID.equals(existing.getPayStatus())) return;
        Orders update = Orders.builder().id(existing.getId()).status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID).checkoutTime(LocalDateTime.now()).build();
        // 仅当订单仍为待支付状态时更新，保证并发安全
        int updated = orderMapper.updatePaymentByExpectedStatus(
                update, Orders.PENDING_PAYMENT, Orders.UN_PAID);
        if (updated == 0) {
            Orders current = orderMapper.getById(existing.getId());
            if (current != null && Orders.PAID.equals(current.getPayStatus())) return;
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        notificationService.sendNewOrderAfterCommit(existing);
        notificationService.sendStatusAfterCommit(existing, Orders.TO_BE_CONFIRMED, "支付成功，等待商家接单");
    }

    /**
     * 准备退款状态变更
     *
     * @param order  订单实体
     * @param update 待更新的订单实体
     */
    public void prepareRefund(Orders order, Orders update) {
        // 退款成功前不提前修改支付状态，避免用户误看到“已退款”。
    }

    /**
     * 执行退款操作
     *
     * @param order 订单实体
     * @throws Exception 退款异常
     */
    public void refundIfNecessary(Orders order) throws Exception {
        if (Orders.PAID.equals(order.getPayStatus())) {
            refundApplicationService.createFullRefund(order.getId(), "LEGACY_CANCEL:" + order.getId());
        }
    }

    /**
     * 校验待支付订单
     *
     * @param request 支付请求
     * @param userId  用户ID
     * @return 校验通过的订单实体
     */
    private Orders getPayableOrder(OrdersPaymentDTO request, Long userId) {
        if (request == null || request.getOrderNumber() == null) throw new OrderBusinessException("订单号不能为空");
        Orders order = orderMapper.getByNumber(request.getOrderNumber());
        if (order == null) throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        if (!userId.equals(order.getUserId())) throw new OrderBusinessException("无权支付该订单");
        if (Orders.PAID.equals(order.getPayStatus())) throw new OrderBusinessException("该订单已支付");
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        if (order.getAmount() == null || order.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderBusinessException("订单金额异常");
        }
        return order;
    }

}
