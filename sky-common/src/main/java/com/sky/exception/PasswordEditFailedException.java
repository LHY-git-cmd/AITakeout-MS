package com.sky.exception;

/**
 * 密码修改失败异常
 * 当员工修改密码时原密码不正确或修改操作失败时抛出此异常
 */
public class PasswordEditFailedException extends BaseException{

    /**
     * 构造函数
     *
     * @param msg 异常信息
     */
    public PasswordEditFailedException(String msg){
        super(msg);
    }

}