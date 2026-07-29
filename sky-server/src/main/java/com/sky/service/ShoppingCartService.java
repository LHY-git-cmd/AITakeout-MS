package com.sky.service;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {

    /**
     * 添加商品到购物车
     */
    void addShoppingCart(ShoppingCartDTO shoppingCartDTO);

    /**
     * 查询当前用户的购物车
     */
    List<ShoppingCart> showShoppingCart();

    /**
     * 减少购物车中的商品数量
     */
    void subShoppingCart(ShoppingCartDTO shoppingCartDTO);

    /**
     * 清空当前用户的购物车
     */
    void cleanShoppingCart();
}
