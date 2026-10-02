package com.sky.enumeration;

/** 管理端权限编码；AI原子操作与普通管理接口共用同一套编码。 */
public enum AdminPermission {
    EMPLOYEE_READ, // 员工只读
    EMPLOYEE_WRITE, // 员工读写
    KNOWLEDGE_READ, // 知识库只读
    KNOWLEDGE_WRITE, // 知识库读写
    PUBLIC_KB_READ, // 用户公共知识查看
    PUBLIC_KB_EDIT, // 用户公共知识编辑
    PUBLIC_KB_REVIEW, // 用户公共知识审核
    PUBLIC_KB_PUBLISH, // 用户公共知识发布、下线和回滚
    DIET_DATA_READ, // 饮食营养数据查看
    DIET_DATA_EDIT, // 饮食营养数据编辑
    DIET_DATA_VERIFY, // 饮食营养数据审核
    DIET_RULE_READ, // 食养规则查看
    DIET_RULE_EDIT, // 食养规则编辑
    DIET_RULE_PUBLISH, // 食养规则发布、下线和回滚
    DIET_AUDIT_READ, // 饮食推荐审计查看
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
