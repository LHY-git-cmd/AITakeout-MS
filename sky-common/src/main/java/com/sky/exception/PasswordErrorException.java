package com.sky.exception;

/**
 * 密码错误异常
 * 当员工登录时输入的密码与系统存储的密码不匹配时抛出此异常
 */
public class PasswordErrorException extends BaseException {

    public PasswordErrorException() {
    }

    /**
     * 构造函数
     *
     * @param msg 异常信息
     */
    public PasswordErrorException(String msg) {
        super(msg);
    }

}