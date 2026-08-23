package com.sky.exception;

/**
 * 登录失败异常
 * 当员工或用户登录验证失败时抛出此异常
 */
public class LoginFailedException extends BaseException{

    /**
     * 构造函数
     *
     * @param msg 异常信息
     */
    public LoginFailedException(String msg){
        super(msg);
    }
}