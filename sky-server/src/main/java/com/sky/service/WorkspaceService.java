package com.sky.service;

import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;

import java.time.LocalDateTime;

/**
 * 工作台业务层接口
 * 提供管理端首页展示的各类统计数据查询功能
 */
public interface WorkspaceService {

    /**
     * 根据时间段统计营业数据
     *
     * @param begin 开始时间
     * @param end   结束时间
     * @return 营业数据
     */
    BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end);

    /**
     * 查询订单管理数据（各状态订单数量）
     *
     * @return 订单管理数据
     */
    OrderOverViewVO getOrderOverView();

    /**
     * 查询菜品总览（启售/停售菜品数量）
     *
     * @return 菜品总览数据
     */
    DishOverViewVO getDishOverView();

    /**
     * 查询套餐总览（启售/停售套餐数量）
     *
     * @return 套餐总览数据
     */
    SetmealOverViewVO getSetmealOverView();
}