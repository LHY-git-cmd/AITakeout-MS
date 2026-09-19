package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.service.order.DeliveryRangeService;
import com.sky.service.order.OrderNotificationService;
import com.sky.service.order.OrderPaymentService;
import com.sky.service.order.OrderQueryService;
import com.sky.service.checkout.CheckoutQuoteService;
import com.sky.service.checkout.CheckoutModels.QuoteSnapshot;
import com.sky.service.checkout.OrderSubmissionService;
import com.sky.service.checkout.PreviewTokenService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 订单服务实现类
 * 编排订单相关业务流程，将下单、支付、查询、通知等职责委托给专门的服务类
 * 使用构造器注入，消除循环依赖风险
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final ShoppingCartMapper shoppingCartMapper;
    private final OrderQueryService orderQueryService;
    private final OrderPaymentService orderPaymentService;
    private final DeliveryRangeService deliveryRangeService;
    private final OrderNotificationService notificationService;
    private final CheckoutQuoteService checkoutQuoteService;
    private final PreviewTokenService previewTokenService;
    private final OrderSubmissionService orderSubmissionService;

    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO request, String idempotencyKey) {
        if (request == null || request.getPreviewToken() == null || request.getPreviewToken().isBlank()) {
            throw new OrderBusinessException("请先完成订单试算");
        }
        Long userId = BaseContext.getCurrentId();
        String requestHash = submissionHash(request);
        OrderSubmissionService.Reservation reservation = orderSubmissionService.reserve(userId, idempotencyKey, requestHash);
        if (!reservation.created()) {
            Long existingOrderId = reservation.submission().getOrderId();
            if (existingOrderId == null) throw new OrderBusinessException("订单正在提交，请稍后重试");
            Orders existing = orderMapper.getById(existingOrderId);
            if (existing == null) throw new OrderBusinessException("幂等订单不存在");
            return toSubmitView(existing);
        }

        OrderPreviewDTO previewRequest = new OrderPreviewDTO();
        previewRequest.setAddressBookId(request.getAddressBookId());
        previewRequest.setDeliveryMode(request.getDeliveryMode());
        previewRequest.setDeliverySlotStart(request.getDeliverySlotStart());
        QuoteSnapshot quote = checkoutQuoteService.quote(userId, previewRequest, false);
        String mode = "SCHEDULED".equalsIgnoreCase(request.getDeliveryMode()) ? "SCHEDULED" : "IMMEDIATE";
        previewTokenService.verify(request.getPreviewToken(), userId, quote.cartDigest(), request.getAddressBookId(),
                mode, quote.slotStart());

        AddressBook address = quote.address();
        var amount = BigDecimal.valueOf(quote.preview().getAmountCent(), 2);
        Orders order = new Orders();
        // 权威提交仅复制非金额表单字段，避免旧客户端金额字段污染订单快照。
        order.setAddressBookId(request.getAddressBookId());
        order.setPayMethod(request.getPayMethod());
        order.setRemark(request.getRemark());
        order.setTablewareNumber(request.getTablewareNumber() == null ? 0 : request.getTablewareNumber());
        order.setTablewareStatus(request.getTablewareStatus());
        order.setNumber(UUID.randomUUID().toString().replace("-", ""));
        order.setUserId(userId);
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setPayStatus(Orders.UN_PAID);
        order.setOrderTime(LocalDateTime.now());
        order.setExpiresAt(order.getOrderTime().plusMinutes(15));
        order.setPhone(address.getPhone());
        order.setAddress(deliveryRangeService.fullAddress(address));
        order.setConsignee(address.getConsignee());
        order.setAmount(amount);
        order.setGoodsAmountCent(quote.preview().getGoodsAmountCent());
        order.setPackAmountCent(quote.preview().getPackAmountCent());
        order.setDeliveryFeeCent(quote.preview().getDeliveryFeeCent());
        order.setDiscountAmountCent(quote.preview().getDiscountAmountCent());
        order.setAmountCent(quote.preview().getAmountCent());
        order.setPackAmount(Math.toIntExact(quote.preview().getPackAmountCent() / 100));
        order.setDeliveryDistanceMeters(quote.preview().getDistanceMeters());
        order.setMapProvider(address.getMapProvider());
        order.setDeliveryMode(mode);
        order.setDeliverySlotStart(quote.slotStart());
        order.setDeliverySlotEnd(quote.slotEnd());
        order.setEstimatedDeliveryTime(quote.preview().getEstimatedDeliveryTime());
        order.setDeliveryStatus("IMMEDIATE".equals(mode) ? 1 : 0);
        order.setAddressLatitude(address.getLatitude());
        order.setAddressLongitude(address.getLongitude());
        order.setPricingRuleVersion(quote.preview().getPricingRuleVersion());
        order.setVersion(0);
        orderMapper.insert(order);

        List<OrderDetail> details = new ArrayList<>();
        for (ShoppingCart cart : quote.cartSnapshot()) {
            OrderDetail detail = new OrderDetail();
            BeanUtils.copyProperties(cart, detail, "id");
            detail.setOrderId(order.getId());
            details.add(detail);
        }
        orderDetailMapper.insertBatch(details);
        orderSubmissionService.attachOrder(reservation.submission().getId(), order.getId());
        shoppingCartMapper.deleteByUserId(userId);
        return toSubmitView(order);
    }

    private OrderSubmitVO toSubmitView(Orders order) {
        return OrderSubmitVO.builder().id(order.getId()).orderNumber(order.getNumber())
                .orderAmount(order.getAmount()).orderTime(order.getOrderTime()).build();
    }

    private String submissionHash(OrdersSubmitDTO request) {
        try {
            String value = request.getPreviewToken() + "|" + request.getAddressBookId() + "|" + request.getDeliveryMode() + "|"
                    + request.getDeliverySlotStart() + "|" + request.getRemark() + "|" + request.getTablewareStatus()
                    + "|" + request.getTablewareNumber();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    /**
     * 订单支付
     *
     * @param request 支付请求
     * @return 支付预下单结果
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO request) throws Exception {
        return orderPaymentService.payment(request, BaseContext.getCurrentId());
    }

    /**
     * 支付成功回调处理
     *
     * @param outTradeNo 商户订单号
     */
    @Override
    public void paySuccess(String outTradeNo) {
        orderPaymentService.paySuccess(outTradeNo);
    }

    /**
     * 用户端订单分页查询
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @param status   订单状态
     * @return 分页结果
     */
    @Override
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        OrdersPageQueryDTO query = new OrdersPageQueryDTO();
        query.setPage(pageNum);
        query.setPageSize(pageSize);
        query.setUserId(BaseContext.getCurrentId());
        query.setStatus(status);
        return orderQueryService.page(query, true);
    }

    /**
     * 管理端订单详情查询
     *
     * @param id 订单ID
     * @return 订单详情
     */
    @Override
    public OrderVO details(Long id) {
        return orderQueryService.details(orderQueryService.getExisting(id));
    }

    /**
     * 用户端订单详情查询（校验归属）
     *
     * @param id 订单ID
     * @return 订单详情
     */
    @Override
    public OrderVO detailsForUser(Long id) {
        Orders order = orderQueryService.getExisting(id);
        checkOrderOwner(order);
        return orderQueryService.details(order);
    }

    /**
     * 用户取消订单
     * 仅待支付和待接单状态可取消
     *
     * @param id 订单ID
     */
    @Override
    @Transactional
    public void userCancelById(Long id) throws Exception {
        // 与支付创建共用订单行锁，消除“检查完成后才进入支付处理中”的并发窗口。
        Orders order = id == null ? null : orderMapper.getByIdForUpdate(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        checkOrderOwner(order);
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus())
                && !Orders.TO_BE_CONFIRMED.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        if (Orders.PENDING_PAYMENT.equals(order.getStatus())) {
            orderPaymentService.assertCancelable(order.getId());
        }
        Orders update = Orders.builder().id(order.getId()).status(Orders.CANCELLED)
                .cancelReason("用户取消").cancelTime(LocalDateTime.now()).build();
        cancelOrder(order, update, order.getStatus(), "订单已取消");
    }

    /**
     * 再来一单
     * 将历史订单中的菜品重新加入购物车
     *
     * @param id 历史订单ID
     */
    @Override
    @Transactional
    public void repetition(Long id) {
        Orders order = orderQueryService.getExisting(id);
        checkOrderOwner(order);
        List<OrderDetail> details = orderDetailMapper.getByOrderId(id);
        if (CollectionUtils.isEmpty(details)) {
            throw new OrderBusinessException("订单中没有可重新购买的商品");
        }
        Long userId = BaseContext.getCurrentId();
        LocalDateTime createTime = LocalDateTime.now();
        for (OrderDetail detail : details) {
            ShoppingCart query = ShoppingCart.builder().userId(userId).dishId(detail.getDishId())
                    .setmealId(detail.getSetmealId()).dishFlavor(detail.getDishFlavor()).build();
            ShoppingCart existing = shoppingCartMapper.getOne(query);
            if (existing != null) {
                existing.setNumber(existing.getNumber() + detail.getNumber());
                shoppingCartMapper.updateNumber(existing);
            } else {
                ShoppingCart cart = new ShoppingCart();
                BeanUtils.copyProperties(detail, cart, "id");
                cart.setUserId(userId);
                cart.setCreateTime(createTime);
                shoppingCartMapper.insert(cart);
            }
        }
    }

    /**
     * 管理端订单条件搜索
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO query) {
        return orderQueryService.page(query, false);
    }

    /**
     * 各状态订单数量统计
     *
     * @return 统计结果
     */
    @Override
    public OrderStatisticsVO statistics() {
        OrderStatisticsVO result = new OrderStatisticsVO();
        result.setToBeConfirmed(orderMapper.countStatus(Orders.TO_BE_CONFIRMED));
        result.setConfirmed(orderMapper.countStatus(Orders.CONFIRMED));
        result.setDeliveryInProgress(orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS));
        return result;
    }

    /**
     * 接单（待接单 → 已接单）
     *
     * @param request 接单请求
     */
    @Override
    public void confirm(OrdersConfirmDTO request) {
        Orders order = orderQueryService.getExisting(request.getId());
        requireStatus(order, Orders.TO_BE_CONFIRMED);
        updateStatusOrThrow(Orders.builder().id(order.getId()).status(Orders.CONFIRMED).build(),
                Orders.TO_BE_CONFIRMED);
        notificationService.sendStatusAfterCommit(order, Orders.CONFIRMED, "商家已接单");
    }

    /**
     * 拒单（待接单 → 已取消，已支付需退款）
     *
     * @param request 拒单请求
     */
    @Override
    @Transactional
    public void rejection(OrdersRejectionDTO request) throws Exception {
        if (request == null || request.getRejectionReason() == null
                || request.getRejectionReason().trim().isEmpty()) {
            throw new OrderBusinessException("拒单原因不能为空");
        }
        Orders order = orderQueryService.getExisting(request.getId());
        requireStatus(order, Orders.TO_BE_CONFIRMED);
        Orders update = Orders.builder().id(order.getId()).status(Orders.CANCELLED)
                .rejectionReason(request.getRejectionReason()).cancelTime(LocalDateTime.now()).build();
        cancelOrder(order, update, Orders.TO_BE_CONFIRMED, "商家已拒单");
    }

    /**
     * 管理端取消订单
     *
     * @param request 取消请求
     */
    @Override
    @Transactional
    public void cancel(OrdersCancelDTO request) throws Exception {
        if (request == null || request.getCancelReason() == null || request.getCancelReason().trim().isEmpty()) {
            throw new OrderBusinessException("取消原因不能为空");
        }
        Orders order = orderQueryService.getExisting(request.getId());
        if (Orders.CANCELLED.equals(order.getStatus()) || Orders.COMPLETED.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders update = Orders.builder().id(order.getId()).status(Orders.CANCELLED)
                .cancelReason(request.getCancelReason()).cancelTime(LocalDateTime.now()).build();
        cancelOrder(order, update, order.getStatus(), "商家已取消订单");
    }

    /**
     * 派送订单（已接单 → 派送中）
     *
     * @param id 订单ID
     */
    @Override
    public void delivery(Long id) {
        Orders order = orderQueryService.getExisting(id);
        requireStatus(order, Orders.CONFIRMED);
        updateStatusOrThrow(Orders.builder().id(order.getId()).status(Orders.DELIVERY_IN_PROGRESS).build(),
                Orders.CONFIRMED);
        notificationService.sendStatusAfterCommit(order, Orders.DELIVERY_IN_PROGRESS, "订单开始配送");
    }

    /**
     * 完成订单（派送中 → 已完成）
     *
     * @param id 订单ID
     */
    @Override
    public void complete(Long id) {
        Orders order = orderQueryService.getExisting(id);
        requireStatus(order, Orders.DELIVERY_IN_PROGRESS);
        Orders update = Orders.builder().id(order.getId()).status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now()).build();
        updateStatusOrThrow(update, Orders.DELIVERY_IN_PROGRESS);
        notificationService.sendStatusAfterCommit(order, Orders.COMPLETED, "订单已送达");
    }

    /**
     * 客户催单
     * 向管理端推送催单提醒
     *
     * @param id 订单ID
     */
    @Override
    public void reminder(Long id) {
        Orders order = orderQueryService.getExisting(id);
        checkOrderOwner(order);
        if (!Orders.TO_BE_CONFIRMED.equals(order.getStatus())) {
            throw new OrderBusinessException("当前订单状态不能催单");
        }
        notificationService.sendReminder(order);
        log.info("已推送客户催单提醒：userId={}, orderId={}, orderNumber={}",
                BaseContext.getCurrentId(), order.getId(), order.getNumber());
    }

    /**
     * 取消订单通用逻辑：预退款、更新状态、实际退款、发送通知
     *
     * @param order          订单
     * @param update         更新内容
     * @param expectedStatus 预期当前状态
     * @param notification   通知内容
     */
    private void cancelOrder(Orders order, Orders update, Integer expectedStatus, String notification)
            throws Exception {
        orderPaymentService.prepareRefund(order, update);
        updateStatusOrThrow(update, expectedStatus);
        orderPaymentService.refundIfNecessary(order);
        notificationService.sendStatusAfterCommit(order, Orders.CANCELLED, notification);
    }

    /**
     * 校验订单状态
     *
     * @param order          订单
     * @param expectedStatus 预期状态
     */
    private void requireStatus(Orders order, Integer expectedStatus) {
        if (!expectedStatus.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 校验订单归属当前用户
     *
     * @param order 订单
     */
    private void checkOrderOwner(Orders order) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null || !userId.equals(order.getUserId())) {
            throw new OrderBusinessException("无权访问该订单");
        }
    }

    /**
     * 乐观更新订单状态，失败则抛出异常
     *
     * @param update         更新内容
     * @param expectedStatus 预期当前状态
     */
    private void updateStatusOrThrow(Orders update, Integer expectedStatus) {
        if (orderMapper.updateByExpectedStatus(update, expectedStatus) != 1) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }
}
