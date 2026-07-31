package com.sky.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 购物车数据传输对象
 * 用于客户端向服务端传递添加/减少购物车商品的请求参数
 */
@Data
public class ShoppingCartDTO implements Serializable {

    // 菜品ID（添加菜品时使用）
    private Long dishId;

    // 套餐ID（添加套餐时使用）
    private Long setmealId;

    // 菜品口味（仅添加菜品时可能需要）
    private String dishFlavor;

}