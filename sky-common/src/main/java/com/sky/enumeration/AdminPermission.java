package com.sky.enumeration;

/** 管理端权限编码；AI原子操作与普通管理接口共用同一套编码。 */
public enum AdminPermission {
    EMPLOYEE_READ,
    EMPLOYEE_WRITE,
    KNOWLEDGE_READ,
    KNOWLEDGE_WRITE,
    ORDER_READ,
    ORDER_STATUS_WRITE,
    DISH_READ,
    DISH_WRITE,
    SETMEAL_READ,
    SETMEAL_WRITE,
    SHOP_READ,
    SHOP_STATUS_WRITE,
    WORKSPACE_READ,
    REPORT_READ
}
