package com.sky.exception;

/**
 * 地址簿业务异常
 * 当地址簿相关业务操作（如删除被订单关联的地址）不满足条件时抛出此异常
 */
public class AddressBookBusinessException extends BaseException {

    /**
     * 构造函数
     *
     * @param msg 异常信息
     */
    public AddressBookBusinessException(String msg) {
        super(msg);
    }

}