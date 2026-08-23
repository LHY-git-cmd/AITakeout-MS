package com.sky.exception;

/**
 * 购物车业务异常
 * 当购物车相关业务操作（如清空购物车、添加商品到购物车等）不满足条件时抛出此异常
 */
public class ShoppingCartBusinessException extends BaseException {

    /**
     * 构造函数
     *
     * @param msg 异常信息
     */
    public ShoppingCartBusinessException(String msg) {
        super(msg);
    }

}