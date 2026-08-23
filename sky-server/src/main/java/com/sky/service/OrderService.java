package com.sky.service;

import com.sky.dto.*;
import com.sky.result.PageResult;
import com.sky.vo.*;

/**
 * 订单业务层接口
 * 提供订单的创建、支付、查询、状态变更等全流程管理功能
 */
public interface OrderService {

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO 订单提交信息
     * @return 订单提交视图对象
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO 支付信息
     * @return 支付视图对象
     * @throws Exception 支付异常
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo 订单号
     */
    void paySuccess(String outTradeNo);

    /**
     * 用户端分页查询订单
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param status   订单状态
     * @return 分页结果
     */
    PageResult pageQuery4User(int page, int pageSize, Integer status);

    /**
     * 管理端查询订单详情
     *
     * @param id 订单ID
     * @return 订单视图对象
     */
    OrderVO details(Long id);

    /**
     * 用户端查询订单详情
     *
     * @param id 订单ID
     * @return 订单视图对象
     */
    OrderVO detailsForUser(Long id);

    /**
     * 用户取消订单
     *
     * @param id 订单ID
     * @throws Exception 取消异常
     */
    void userCancelById(Long id) throws Exception;

    /**
     * 再来一单
     *
     * @param id 订单ID
     */
    void repetition(Long id);

    /**
     * 订单条件搜索（管理端，支持按订单号、手机号、状态、下单时间等条件筛选）
     *
     * @param ordersPageQueryDTO 分页查询条件
     * @return 分页结果
     */
    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 统计各个状态的订单数量（待接单、已接单、派送中）
     *
     * @return 订单统计视图对象
     */
    OrderStatisticsVO statistics();

    /**
     * 接单（将订单状态由"待接单"改为"已接单"）
     *
     * @param ordersConfirmDTO 接单信息
     */
    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    /**
     * 拒单（将订单状态改为"已取消"，记录拒单原因，已支付订单需退款）
     *
     * @param ordersRejectionDTO 拒单信息
     * @throws Exception 拒单异常
     */
    void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception;

    /**
     * 取消订单（管理端取消订单，已支付订单需退款）
     *
     * @param ordersCancelDTO 取消订单信息
     * @throws Exception 取消异常
     */
    void cancel(OrdersCancelDTO ordersCancelDTO) throws Exception;

    /**
     * 派送订单（将订单状态由"已接单"改为"派送中"）
     *
     * @param id 订单ID
     */
    void delivery(Long id);

    /**
     * 完成订单（将订单状态由"派送中"改为"已完成"，记录送达时间）
     *
     * @param id 订单ID
     */
    void complete(Long id);

    /**
     * 客户催单
     *
     * @param id 订单ID
     */
    void reminder(Long id);

}