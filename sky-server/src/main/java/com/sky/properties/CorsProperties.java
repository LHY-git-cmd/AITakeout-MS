package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨域配置属性类
 * 从配置文件中读取sky.cors前缀下的属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "sky.cors")
public class CorsProperties {

    /**
     * 允许跨域的源列表
     */
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * 允许跨域的源模式，主要用于开发环境中的动态端口和局域网地址。
     */
    private List<String> allowedOriginPatterns = new ArrayList<>();
}
