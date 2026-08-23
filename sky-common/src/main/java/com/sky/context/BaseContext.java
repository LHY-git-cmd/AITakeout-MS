package com.sky.context;

/**
 * 基础上下文工具类
 * 基于ThreadLocal存储当前线程的用户ID，用于在请求链路中传递用户身份信息
 */
public class BaseContext {

    /**
     * 线程局部变量，用于存储当前线程的用户ID
     */
    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    /**
     * 设置当前线程的用户ID
     *
     * @param id 用户ID
     */
    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    /**
     * 获取当前线程的用户ID
     *
     * @return 用户ID
     */
    public static Long getCurrentId() {
        return threadLocal.get();
    }

    /**
     * 移除当前线程的用户ID，防止内存泄漏
     */
    public static void removeCurrentId() {
        threadLocal.remove();
    }

}