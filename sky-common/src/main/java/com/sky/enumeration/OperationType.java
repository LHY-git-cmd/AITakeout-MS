package com.sky.enumeration;

/**
 * 数据库操作类型枚举
 * 用于标识AutoFill切面中需要自动填充的数据库操作类型
 */
public enum OperationType {

    /**
     * 更新操作
     */
    UPDATE,

    /**
     * 插入操作
     */
    INSERT

}