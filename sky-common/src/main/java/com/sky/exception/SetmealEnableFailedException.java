package com.sky.exception;

/**
 * 套餐启用失败异常
 * 当套餐中包含未启售的菜品而尝试启售套餐时抛出此异常
 */
public class SetmealEnableFailedException extends BaseException {

    public SetmealEnableFailedException(){}

    /**
     * 构造函数
     *
     * @param msg 异常信息
     */
    public SetmealEnableFailedException(String msg){
        super(msg);
    }
}