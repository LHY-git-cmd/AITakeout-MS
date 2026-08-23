package com.sky.exception;

/**
 * 删除不允许异常
 * 当删除操作不被允许（如删除被其他数据关联的分类、菜品等）时抛出此异常
 */
public class DeletionNotAllowedException extends BaseException {

    /**
     * 构造函数
     *
     * @param msg 异常信息
     */
    public DeletionNotAllowedException(String msg) {
        super(msg);
    }

}