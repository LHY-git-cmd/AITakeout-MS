package com.sky.exception;

/**
 * 业务异常基础类
 * 所有自定义业务异常的父类，继承RuntimeException
 * 由GlobalExceptionHandler统一捕获处理
 */
public class BaseException extends RuntimeException {

    public BaseException() {
    }

    /**
     * 构造函数
     *
     * @param msg 异常信息
     */
    public BaseException(String msg) {
        super(msg);
    }

}