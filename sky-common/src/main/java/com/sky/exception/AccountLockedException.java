package com.sky.exception;

/**
 * 账号被锁定异常
 * 当员工账号因多次登录失败等原因被系统锁定时抛出此异常
 */
public class AccountLockedException extends BaseException {

    public AccountLockedException() {
    }

    /**
     * 构造函数
     *
     * @param msg 异常信息
     */
    public AccountLockedException(String msg) {
        super(msg);
    }

}