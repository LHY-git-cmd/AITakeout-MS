package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent智能体服务配置属性类
 * 从配置文件中读取sky.agent前缀下的属性
 */
@Component
@ConfigurationProperties(prefix = "sky.agent")
@Data
public class AgentProperties {

    /**
     * Agent服务基础URL
     */
    private String baseUrl = "http://localhost:8000";

    /**
     * 连接超时时间（毫秒）
     */
    private int connectTimeout = 5000;

    /**
     * 读取超时时间（毫秒）
     */
    private int readTimeout = 60000;

    /**
     * 请求超时时间（毫秒）
     */
    private int requestTimeout = 65000;

    /** 模型上下文最多携带的历史消息数 */
    private int contextMessageLimit = 20;

    /** 模型上下文历史消息的最大字符数 */
    private int contextCharacterLimit = 12000;

    /** 默认模型 */
    private String defaultModel = "deepseek-v4-pro";

    /** 默认采样温度 */
    private double defaultTemperature = 0.7;

    /** 启动时是否恢复未结束任务，测试环境可关闭 */
    private boolean recoveryEnabled = true;

    /** 是否启用Agent消息历史缓存 */
    private boolean messageCacheEnabled = true;

    /** Agent消息历史缓存key前缀 */
    private String messageCacheKeyPrefix = "sky:agent:messages:";

    /** Agent消息历史缓存有效期（秒） */
    private long messageCacheTtlSeconds = 1800;

    private int summaryMessageThreshold = 20;
    private int summaryTokenThreshold = 5000;
    private int summaryRecentMessageCount = 10;

}
