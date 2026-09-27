package com.sky.enumeration;

/**
 * Agent 调用主体类型，用于区分管理端员工、普通用户和系统任务。
 */
public enum AgentActorType {
    ADMIN,
    USER,
    SYSTEM;

    /**
     * 解析持久化值。历史任务没有主体类型时按管理端任务兼容处理。
     */
    public static AgentActorType fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            return ADMIN;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
