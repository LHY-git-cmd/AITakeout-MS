package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Web端登录配置属性类
 * 从配置文件中读取sky.web-login前缀下的属性，用于Web端演示登录
 */
@Component
@ConfigurationProperties(prefix = "sky.web-login")
@Data
public class WebLoginProperties {

    /**
     * 是否启用Web端演示登录，默认关闭
     */
    private boolean enabled = false;

    /**
     * 演示登录使用的验证码
     */
    private String verificationCode;
}