package com.sky.enumeration;

/** 管理端权限编码；AI原子操作与普通管理接口共用同一套编码。 */
public enum AdminPermission {
    EMPLOYEE_READ, // 员工只读
    EMPLOYEE_WRITE, // 员工读写
    KNOWLEDGE_READ, // 知识库只读
    KNOWLEDGE_WRITE, // 知识库读写
    ORDER_READ, // 订单只读
    ORDER_STATUS_WRITE, // 订单状态读写
    DISH_READ, // 菜品只读
    DISH_WRITE, // 菜品读写
    SETMEAL_READ, // 套餐只读
    SETMEAL_WRITE, // 套餐读写
    SHOP_READ, // 店铺只读
    SHOP_STATUS_WRITE, // 店铺状态读写
    WORKSPACE_READ, // 工作台只读
    REPORT_READ, // 报表只读
    ACCOUNT_READ, // 账户只读
    ACCOUNT_ADJUST // 账户调整
}