package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT令牌配置属性类
 * 从配置文件中读取sky.jwt前缀下的属性，包括管理端和用户端的JWT配置
 */
@Component
@ConfigurationProperties(prefix = "sky.jwt")
@Data
public class JwtProperties {

    /**
     * 管理端员工生成jwt令牌相关配置
     */
    private String adminSecretKey;

    /**
     * 管理端jwt令牌有效期（毫秒）
     */
    private long adminTtl;

    /**
     * 管理端jwt令牌名称（请求头中的名称）
     */
    private String adminTokenName;

    /**
     * 用户端微信用户生成jwt令牌相关配置
     */
    private String userSecretKey;

    /**
     * 用户端jwt令牌有效期（毫秒）
     */
    private long userTtl;

    /**
     * 用户端jwt令牌名称（请求头中的名称）
     */
    private String userTokenName;

}