package com.sky.service;

import com.sky.dto.*;
import com.sky.result.PageResult;
import com.sky.vo.*;

public interface OrderService {

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功，修改订单状态
     * @param outTradeNo
     */
    void paySuccess(String outTradeNo);

    PageResult pageQuery4User(int page, int pageSize, Integer status);

    OrderVO details(Long id);

    OrderVO detailsForUser(Long id);

    void userCancelById(Long id) throws Exception;

    void repetition(Long id);

    /**
     * 订单条件搜索（管理端，支持按订单号、手机号、状态、下单时间等条件筛选）
     */
    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 统计各个状态的订单数量（待接单、已接单、派送中）
     */
    OrderStatisticsVO statistics();

    /**
     * 接单（将订单状态由"待接单"改为"已接单"）
     */
    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    /**
     * 拒单（将订单状态改为"已取消"，记录拒单原因，已支付订单需退款）
     */
    void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception;

    /**
     * 取消订单（管理端取消订单，已支付订单需退款）
     */
    void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception;

    /**
     * 派送订单（将订单状态由"已接单"改为"派送中"）
     */
    void delivery(Long id);

    /**
     * 完成订单（将订单状态由"派送中"改为"已完成"，记录送达时间）
     */
    void complete(Long id);

    /**
     * 客户催单
     */
    void reminder(Long id);

}
