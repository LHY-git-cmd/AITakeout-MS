package com.sky.service;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;

import java.util.List;

/**
 * 购物车业务层接口
 * 提供购物车的添加、减少、查询和清空功能
 */
public interface ShoppingCartService {

    /**
     * 添加商品到购物车
     *
     * @param shoppingCartDTO 购物车数据传输对象
     */
    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);

    /**
     * 查询当前用户的购物车
     *
     * @return 购物车商品列表
     */
    List<ShoppingCart> showShoppingCart();

    /**
     * 减少购物车中的商品数量
     *
     * @param shoppingCartDTO 购物车数据传输对象
     */
    void subShoppingCart(ShoppingCartDTO shoppingCartDTO);

    /**
     * 清空当前用户的购物车
     */
    void cleanShoppingCart();
}