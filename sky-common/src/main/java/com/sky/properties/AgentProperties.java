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

}