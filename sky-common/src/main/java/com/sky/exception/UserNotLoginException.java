package com.sky.exception;

/**
 * 用户未登录异常
 * 当用户尝试访问需要认证的资源但未登录时抛出此异常
 */
public class UserNotLoginException extends BaseException {

    public UserNotLoginException() {
    }

    /**
     * 构造函数
     *
     * @param msg 异常信息
     */
    public UserNotLoginException(String msg) {
        super(msg);
    }

}