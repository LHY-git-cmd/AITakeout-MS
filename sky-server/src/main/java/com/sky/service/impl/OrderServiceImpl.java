package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.properties.WeChatProperties;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单
 */
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Value("${sky.shop.address:}")
    private String shopAddress;

    @Value("${sky.baidu.ak:}")
    private String baiduAk;

    @Value("${sky.delivery.max-distance:5000}")
    private Integer maxDeliveryDistance;

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WeChatProperties weChatProperties;
    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        Long userId = BaseContext.getCurrentId();

        if (ordersSubmitDTO == null || ordersSubmitDTO.getAddressBookId() == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //异常情况的处理（收货地址为空、购物车为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null || !userId.equals(addressBook.getUserId())) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        checkOutOfRange(buildFullAddress(addressBook));

        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);

        //查询当前用户的购物车数据
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        int packAmount = shoppingCartList.stream()
                .mapToInt(cart -> cart.getNumber() == null ? 0 : cart.getNumber())
                .sum();
        BigDecimal goodsAmount = shoppingCartList.stream()
                .map(cart -> cart.getAmount().multiply(BigDecimal.valueOf(cart.getNumber())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal orderAmount = goodsAmount
                .add(BigDecimal.valueOf(packAmount))
                .add(BigDecimal.valueOf(6));

        //构造订单数据
        Orders order = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,order);
        order.setPackAmount(packAmount);
        order.setAmount(orderAmount);
        order.setPhone(addressBook.getPhone());
        order.setAddress(buildFullAddress(addressBook));
        order.setConsignee(addressBook.getConsignee());
        order.setNumber(String.valueOf(System.currentTimeMillis()));
        order.setUserId(userId);
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setPayStatus(Orders.UN_PAID);
        order.setOrderTime(LocalDateTime.now());

        //向订单表插入1条数据
        orderMapper.insert(order);

        //订单明细数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(order.getId());
            orderDetailList.add(orderDetail);
        }

        //向明细表插入n条数据
        orderDetailMapper.insertBatch(orderDetailList);

        //清理购物车中的数据
        shoppingCartMapper.deleteByUserId(userId);

        //封装返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime())
                .build();

        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @Transactional
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();

        if (Boolean.TRUE.equals(weChatProperties.getMockPay())) {
            return mockPayment(ordersPaymentDTO, userId);
        }

        if (ordersPaymentDTO == null || ordersPaymentDTO.getOrderNumber() == null) {
            throw new OrderBusinessException("订单号不能为空");
        }

        Orders order = orderMapper.getByNumber(ordersPaymentDTO.getOrderNumber());
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!userId.equals(order.getUserId())) {
            throw new OrderBusinessException("无权支付该订单");
        }
        if (Orders.PAID.equals(order.getPayStatus())) {
            throw new OrderBusinessException("该订单已支付");
        }
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        if (order.getAmount() == null || order.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderBusinessException("订单金额异常");
        }

        User user = userMapper.getById(userId);
        if (user == null || user.getOpenid() == null) {
            throw new OrderBusinessException("当前用户不存在或未绑定微信账号");
        }

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                order.getNumber(), //商户订单号
                order.getAmount(), //数据库订单金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 开发环境模拟支付，不调用微信商户接口
     */
    protected OrderPaymentVO mockPayment(OrdersPaymentDTO ordersPaymentDTO, Long userId) {
        if (ordersPaymentDTO == null || ordersPaymentDTO.getOrderNumber() == null) {
            throw new OrderBusinessException("订单号不能为空");
        }

        Orders order = orderMapper.getByNumber(ordersPaymentDTO.getOrderNumber());
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!userId.equals(order.getUserId())) {
            throw new OrderBusinessException("无权支付该订单");
        }
        if (Orders.PAID.equals(order.getPayStatus())) {
            throw new OrderBusinessException("该订单已支付");
        }
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders paidOrder = Orders.builder()
                .id(order.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .payMethod(ordersPaymentDTO.getPayMethod())
                .checkoutTime(LocalDateTime.now())
                .build();
        orderMapper.update(paidOrder);

        sendNewOrderReminderAfterCommit(order);
        sendOrderStatusAfterCommit(order, Orders.TO_BE_CONFIRMED, "支付成功，等待商家接单");

        log.info("模拟支付成功：userId={}, orderNumber={}", userId, order.getNumber());
        return OrderPaymentVO.builder()
                .mockPay(true)
                .timeStamp(String.valueOf(System.currentTimeMillis() / 1000))
                .nonceStr("mock")
                .signType("MOCK")
                .packageStr("mock_pay_success")
                .paySign("mock")
                .build();
    }

    @Override
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        PageHelper.startPage(pageNum, pageSize);

        OrdersPageQueryDTO queryDTO = new OrdersPageQueryDTO();
        queryDTO.setUserId(BaseContext.getCurrentId());
        queryDTO.setStatus(status);

        Page<Orders> page = orderMapper.pageQuery(queryDTO);
        List<OrderVO> orderVOList = new ArrayList<>();
        for (Orders order : page.getResult()) {
            OrderVO orderVO = buildOrderVO(order, true);
            orderVOList.add(orderVO);
        }
        return new PageResult(page.getTotal(), orderVOList);
    }

    @Override
    public OrderVO details(Long id) {
        Orders order = getExistingOrder(id);
        return buildOrderVO(order, true);
    }

    @Override
    public OrderVO detailsForUser(Long id) {
        Orders order = getExistingOrder(id);
        checkOrderOwner(order);
        return buildOrderVO(order, true);
    }

    @Override
    @Transactional
    public void userCancelById(Long id) throws Exception {
        Orders order = getExistingOrder(id);
        checkOrderOwner(order);
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus())
                && !Orders.TO_BE_CONFIRMED.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders updateOrder = Orders.builder()
                .id(order.getId())
                .status(Orders.CANCELLED)
                .cancelReason("用户取消")
                .cancelTime(LocalDateTime.now())
                .build();
        refundIfNecessary(order, updateOrder);
        orderMapper.update(updateOrder);
        sendOrderStatusAfterCommit(order, Orders.CANCELLED, "订单已取消");
    }

    @Override
    @Transactional
    public void repetition(Long id) {
        Orders order = getExistingOrder(id);
        checkOrderOwner(order);

        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        if (CollectionUtils.isEmpty(orderDetailList)) {
            throw new OrderBusinessException("订单中没有可重新购买的商品");
        }

        Long userId = BaseContext.getCurrentId();
        LocalDateTime createTime = LocalDateTime.now();
        for (OrderDetail orderDetail : orderDetailList) {
            ShoppingCart query = ShoppingCart.builder()
                    .userId(userId)
                    .dishId(orderDetail.getDishId())
                    .setmealId(orderDetail.getSetmealId())
                    .dishFlavor(orderDetail.getDishFlavor())
                    .build();
            ShoppingCart existing = shoppingCartMapper.getOne(query);
            if (existing != null) {
                existing.setNumber(existing.getNumber() + orderDetail.getNumber());
                shoppingCartMapper.updateNumber(existing);
                continue;
            }

            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(createTime);
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    /**
     * 订单条件搜索（管理端）
     * 支持按订单号、手机号、状态、下单时间等条件进行分页查询
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        // 使用PageHelper进行分页
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        // 将订单列表转换为订单VO列表（不包含订单明细详情）
        List<OrderVO> orderVOList = page.getResult().stream()
                .map(order -> buildOrderVO(order, false))
                .collect(Collectors.toList());
        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 统计各个状态的订单数量
     * 统计待接单、已接单、派送中的订单数量，用于管理端首页展示
     */
    @Override
    public OrderStatisticsVO statistics() {
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(orderMapper.countStatus(Orders.TO_BE_CONFIRMED));
        orderStatisticsVO.setConfirmed(orderMapper.countStatus(Orders.CONFIRMED));
        orderStatisticsVO.setDeliveryInProgress(orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS));
        return orderStatisticsVO;
    }

    /**
     * 接单
     * 将订单状态由"待接单"(2)改为"已接单"(3)
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders order = getExistingOrder(ordersConfirmDTO.getId());
        // 校验订单状态必须为"待接单"
        if (!Orders.TO_BE_CONFIRMED.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 更新订单状态为"已接单"
        orderMapper.update(Orders.builder()
                .id(order.getId())
                .status(Orders.CONFIRMED)
                .build());
        sendOrderStatusAfterCommit(order, Orders.CONFIRMED, "商家已接单");
    }

    /**
     * 拒单
     * 将订单状态改为"已取消"(6)，记录拒单原因和取消时间；
     * 若订单已支付，则调用微信退款接口进行退款
     */
    @Override
    @Transactional
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        // 校验拒单原因不能为空
        if (ordersRejectionDTO == null || ordersRejectionDTO.getRejectionReason() == null
                || ordersRejectionDTO.getRejectionReason().trim().isEmpty()) {
            throw new OrderBusinessException("拒单原因不能为空");
        }

        Orders order = getExistingOrder(ordersRejectionDTO.getId());
        // 校验订单状态必须为"待接单"
        if (!Orders.TO_BE_CONFIRMED.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 构建更新订单对象，设置状态为"已取消"、拒单原因和取消时间
        Orders updateOrder = Orders.builder()
                .id(order.getId())
                .status(Orders.CANCELLED)
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .cancelTime(LocalDateTime.now())
                .build();
        // 如需退款则进行退款处理
        refundIfNecessary(order, updateOrder);
        orderMapper.update(updateOrder);
        sendOrderStatusAfterCommit(order, Orders.CANCELLED, "商家已拒单");
    }

    /**
     * 取消订单（管理端）
     * 商家后台取消订单，需填写取消原因；
     * 若订单已支付，则调用微信退款接口进行退款
     */
    @Override
    @Transactional
    public void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception {
        // 校验取消原因不能为空
        if (ordersCancelDTO == null || ordersCancelDTO.getCancelReason() == null
                || ordersCancelDTO.getCancelReason().trim().isEmpty()) {
            throw new OrderBusinessException("取消原因不能为空");
        }

        Orders order = getExistingOrder(ordersCancelDTO.getId());
        // 校验订单状态：已取消或已完成的订单不能再取消
        if (Orders.CANCELLED.equals(order.getStatus()) || Orders.COMPLETED.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 构建更新订单对象，设置状态为"已取消"、取消原因和取消时间
        Orders updateOrder = Orders.builder()
                .id(order.getId())
                .status(Orders.CANCELLED)
                .cancelReason(ordersCancelDTO.getCancelReason())
                .cancelTime(LocalDateTime.now())
                .build();
        // 如需退款则进行退款处理
        refundIfNecessary(order, updateOrder);
        orderMapper.update(updateOrder);
        sendOrderStatusAfterCommit(order, Orders.CANCELLED, "商家已取消订单");
    }

    /**
     * 派送订单
     * 将订单状态由"已接单"(3)改为"派送中"(4)
     */
    @Override
    public void delivery(Long id) {
        Orders order = getExistingOrder(id);
        // 校验订单状态必须为"已接单"
        if (!Orders.CONFIRMED.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 更新订单状态为"派送中"
        orderMapper.update(Orders.builder()
                .id(order.getId())
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build());
        sendOrderStatusAfterCommit(order, Orders.DELIVERY_IN_PROGRESS, "订单开始配送");
    }

    /**
     * 完成订单
     * 将订单状态由"派送中"(4)改为"已完成"(5)，并记录送达时间
     */
    @Override
    public void complete(Long id) {
        Orders order = getExistingOrder(id);
        // 校验订单状态必须为"派送中"
        if (!Orders.DELIVERY_IN_PROGRESS.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 更新订单状态为"已完成"，记录送达时间
        orderMapper.update(Orders.builder()
                .id(order.getId())
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build());
        sendOrderStatusAfterCommit(order, Orders.COMPLETED, "订单已送达");
    }

    /**
     * 客户催单，仅待接单状态的本人订单可以催单
     */
    @Override
    public void reminder(Long id) {
        Orders order = getExistingOrder(id);
        checkOrderOwner(order);
        if (!Orders.TO_BE_CONFIRMED.equals(order.getStatus())) {
            throw new OrderBusinessException("当前订单状态不能催单");
        }

        Map<String, Object> message = new HashMap<>();
        message.put("type", 2);
        message.put("orderId", order.getId());
        message.put("content", "订单号：" + order.getNumber());
        webSocketServer.sendToAllClient(JSON.toJSONString(message));
        log.info("已推送客户催单提醒：userId={}, orderId={}, orderNumber={}",
                BaseContext.getCurrentId(), order.getId(), order.getNumber());
    }

    /**
     * 根据ID查询订单，若订单不存在则抛出异常
     */
    private Orders getExistingOrder(Long id) {
        if (id == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Orders order = orderMapper.getById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return order;
    }

    /**
     * 校验订单归属，确保当前登录用户是订单的所有者
     */
    private void checkOrderOwner(Orders order) {
        Long userId = BaseContext.getCurrentId();
        if (userId == null || !userId.equals(order.getUserId())) {
            throw new OrderBusinessException("无权访问该订单");
        }
    }

    /**
     * 构建订单VO对象
     * @param order 订单实体
     * @param includeDetails 是否包含订单明细详情
     */
    private OrderVO buildOrderVO(Orders order, boolean includeDetails) {
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(order.getId());
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(order, orderVO);
        // 根据需要是否包含订单明细
        if (includeDetails) {
            orderVO.setOrderDetailList(orderDetailList);
        }
        // 拼接订单中的菜品描述（如：宫保鸡丁*2；鱼香肉丝*1；）
        if (!CollectionUtils.isEmpty(orderDetailList)) {
            String orderDishes = orderDetailList.stream()
                    .map(detail -> detail.getName() + "*" + detail.getNumber() + ";")
                    .collect(Collectors.joining());
            orderVO.setOrderDishes(orderDishes);
        }
        return orderVO;
    }

    /**
     * 如有必要则进行退款处理
     * 若订单已支付且非模拟支付环境，则调用微信退款接口
     * @param order 原订单信息（用于判断支付状态）
     * @param updateOrder 待更新的订单对象（设置退款后的支付状态）
     */
    private void refundIfNecessary(Orders order, Orders updateOrder) throws Exception {
        // 仅对已支付的订单进行退款
        if (!Orders.PAID.equals(order.getPayStatus())) {
            return;
        }

        // 非模拟支付环境下调用微信退款接口
        if (!Boolean.TRUE.equals(weChatProperties.getMockPay())) {
            weChatPayUtil.refund(
                    order.getNumber(),
                    order.getNumber(),
                    new BigDecimal("0.01"),
                    new BigDecimal("0.01"));
        }
        updateOrder.setPayStatus(Orders.REFUND);
    }

    /**
     * 校验收货地址是否超出配送范围。未配置百度地图AK时跳过，便于本地开发。
     */
    private void checkOutOfRange(String userAddress) {
        if (baiduAk == null || baiduAk.trim().isEmpty()) {
            log.warn("未配置BAIDU_MAP_AK，跳过配送范围校验");
            return;
        }
        if (shopAddress == null || shopAddress.trim().isEmpty()) {
            throw new OrderBusinessException("未配置商家门店地址");
        }

        Map<String, String> params = new HashMap<>();
        params.put("address", shopAddress);
        params.put("output", "json");
        params.put("ak", baiduAk);

        String shopCoordinateJson = HttpClientUtil.doGet(
                "https://api.map.baidu.com/geocoding/v3", params);
        JSONObject shopResult = parseMapResult(shopCoordinateJson, "店铺地址解析失败");
        String shopCoordinate = getCoordinate(shopResult);

        params.put("address", userAddress);
        String userCoordinateJson = HttpClientUtil.doGet(
                "https://api.map.baidu.com/geocoding/v3", params);
        JSONObject userResult = parseMapResult(userCoordinateJson, "收货地址解析失败");
        String userCoordinate = getCoordinate(userResult);

        params.clear();
        params.put("origin", shopCoordinate);
        params.put("destination", userCoordinate);
        params.put("steps_info", "0");
        params.put("ak", baiduAk);

        String routeJson = HttpClientUtil.doGet(
                "https://api.map.baidu.com/directionlite/v1/driving", params);
        JSONObject routeResult = parseMapResult(routeJson, "配送路线规划失败");
        JSONArray routes = routeResult.getJSONObject("result").getJSONArray("routes");
        if (routes == null || routes.isEmpty()) {
            throw new OrderBusinessException("未查询到可用配送路线");
        }

        Integer distance = routes.getJSONObject(0).getInteger("distance");
        if (distance == null) {
            throw new OrderBusinessException("配送距离解析失败");
        }
        if (distance > maxDeliveryDistance) {
            throw new OrderBusinessException("超出配送范围");
        }
    }

    private JSONObject parseMapResult(String json, String errorMessage) {
        if (json == null || json.trim().isEmpty()) {
            throw new OrderBusinessException(errorMessage);
        }
        JSONObject jsonObject = JSON.parseObject(json);
        if (!Integer.valueOf(0).equals(jsonObject.getInteger("status"))) {
            log.warn("百度地图接口调用失败：{}", jsonObject);
            throw new OrderBusinessException(errorMessage);
        }
        return jsonObject;
    }

    private String getCoordinate(JSONObject coordinateResult) {
        JSONObject location = coordinateResult.getJSONObject("result").getJSONObject("location");
        if (location == null || location.getString("lat") == null || location.getString("lng") == null) {
            throw new OrderBusinessException("地址坐标解析失败");
        }
        return location.getString("lat") + "," + location.getString("lng");
    }

    private String buildFullAddress(AddressBook addressBook) {
        return safeText(addressBook.getProvinceName())
                + safeText(addressBook.getCityName())
                + safeText(addressBook.getDistrictName())
                + safeText(addressBook.getDetail());
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    @Transactional
    public void paySuccess(String outTradeNo) {

        if (outTradeNo == null || outTradeNo.trim().isEmpty()) {
            throw new OrderBusinessException("订单号不能为空");
        }

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 微信可能重复通知，已处理的订单直接返回，避免重复提醒
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            return;
        }

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        sendNewOrderReminderAfterCommit(ordersDB);
        sendOrderStatusAfterCommit(ordersDB, Orders.TO_BE_CONFIRMED, "支付成功，等待商家接单");
    }

    /**
     * 按管理端约定的JSON协议推送来单提醒，并确保事务提交成功后再发送。
     */
    private void sendNewOrderReminderAfterCommit(Orders order) {
        Runnable sendAction = () -> {
            Map<String, Object> message = new HashMap<>();
            message.put("type", 1);
            message.put("orderId", order.getId());
            message.put("content", "订单号：" + order.getNumber());
            webSocketServer.sendToAllClient(JSON.toJSONString(message));
            log.info("已推送来单提醒：orderId={}, orderNumber={}", order.getId(), order.getNumber());
        };

        runAfterCommit(sendAction);
    }

    private void sendOrderStatusAfterCommit(Orders order, Integer status, String content) {
        runAfterCommit(() -> {
            webSocketServer.sendOrderStatusToUser(
                    order.getUserId(), order.getId(), status, content);
            log.info("已推送订单状态：userId={}, orderId={}, status={}",
                    order.getUserId(), order.getId(), status);
        });
    }

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
