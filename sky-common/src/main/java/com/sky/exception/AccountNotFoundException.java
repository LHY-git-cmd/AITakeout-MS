package com.sky.exception;

/**
 * 账号不存在异常
 * 当员工登录时找不到对应的账号记录时抛出此异常
 */
public class AccountNotFoundException extends BaseException {

    public AccountNotFoundException() {
    }

    /**
     * 构造函数
     *
     * @param msg 异常信息
     */
    public AccountNotFoundException(String msg) {
        super(msg);
    }

}