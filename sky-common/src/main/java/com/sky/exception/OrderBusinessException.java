package com.sky.exception;

/**
 * 订单业务异常
 * 当订单相关业务操作（如取消订单、派送订单等）不满足条件时抛出此异常
 */
public class OrderBusinessException extends BaseException {

    /**
     * 构造函数
     *
     * @param msg 异常信息
     */
    public OrderBusinessException(String msg) {
        super(msg);
    }

}