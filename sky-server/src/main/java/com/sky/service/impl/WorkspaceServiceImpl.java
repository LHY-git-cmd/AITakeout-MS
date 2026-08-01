package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.entity.Orders;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class WorkspaceServiceImpl implements WorkspaceService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 根据时间段统计营业数据
     * @param begin 开始时间
     * @param end 结束时间
     * @return 营业数据
     */
    @Override
    public BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end) {
        /**
         * 营业额：当日已完成订单的总金额
         * 有效订单：当日已完成订单的数量
         * 订单完成率：有效订单数 / 总订单数
         * 平均客单价：营业额 / 有效订单数
         * 新增用户：当日新增用户的数量
         */
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("begin", begin);
        queryMap.put("end", end);

        //查询总订单数
        Integer totalOrderCount = orderMapper.countByMap(queryMap);

        queryMap.put("status", Orders.COMPLETED);
        //查询营业额
        Double turnover = orderMapper.sumByMap(queryMap);
        turnover = turnover == null ? 0.0 : turnover;

        //查询有效订单数
        Integer validOrderCount = orderMapper.countByMap(queryMap);

        Double unitPrice = 0.0;
        Double orderCompletionRate = 0.0;
        if (totalOrderCount != 0 && validOrderCount != 0) {
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
            unitPrice = turnover / validOrderCount;
        }

        //查询新增用户数，用户查询会忽略订单状态条件
        Integer newUsers = userMapper.countByMap(queryMap);

        return BusinessDataVO.builder()
                .turnover(turnover)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .unitPrice(unitPrice)
                .newUsers(newUsers)
                .build();
    }

    /**
     * 查询订单管理数据
     * @return 订单管理数据
     */
    @Override
    public OrderOverViewVO getOrderOverView() {
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("begin", LocalDateTime.now().with(LocalTime.MIN));
        queryMap.put("status", Orders.TO_BE_CONFIRMED);

        //待接单
        Integer waitingOrders = orderMapper.countByMap(queryMap);

        //待派送
        queryMap.put("status", Orders.CONFIRMED);
        Integer deliveredOrders = orderMapper.countByMap(queryMap);

        //已完成
        queryMap.put("status", Orders.COMPLETED);
        Integer completedOrders = orderMapper.countByMap(queryMap);

        //已取消
        queryMap.put("status", Orders.CANCELLED);
        Integer cancelledOrders = orderMapper.countByMap(queryMap);

        //全部订单
        queryMap.put("status", null);
        Integer allOrders = orderMapper.countByMap(queryMap);

        return OrderOverViewVO.builder()
                .waitingOrders(waitingOrders)
                .deliveredOrders(deliveredOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .allOrders(allOrders)
                .build();
    }

    /**
     * 查询菜品总览
     * @return 菜品总览数据
     */
    @Override
    public DishOverViewVO getDishOverView() {
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("status", StatusConstant.ENABLE);
        Integer sold = dishMapper.countByMap(queryMap);

        queryMap.put("status", StatusConstant.DISABLE);
        Integer discontinued = dishMapper.countByMap(queryMap);

        return DishOverViewVO.builder()
                .sold(sold)
                .discontinued(discontinued)
                .build();
    }

    /**
     * 查询套餐总览
     * @return 套餐总览数据
     */
    @Override
    public SetmealOverViewVO getSetmealOverView() {
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("status", StatusConstant.ENABLE);
        Integer sold = setmealMapper.countByMap(queryMap);

        queryMap.put("status", StatusConstant.DISABLE);
        Integer discontinued = setmealMapper.countByMap(queryMap);

        return SetmealOverViewVO.builder()
                .sold(sold)
                .discontinued(discontinued)
                .build();
    }
}
