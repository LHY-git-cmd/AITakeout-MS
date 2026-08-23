package com.sky.config;

import com.sky.properties.JwtProperties;
import com.sky.websocket.WebSocketServer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket配置类
 * 用于注册WebSocket的Bean，并配置JWT密钥供WebSocket消息鉴权使用
 */
@Configuration
@RequiredArgsConstructor
public class WebSocketConfiguration {

    private final JwtProperties jwtProperties;

    /**
     * 注册WebSocket端点导出器
     * 同时配置管理端和用户端的JWT密钥
     *
     * @return ServerEndpointExporter实例
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        WebSocketServer.configureJwtSecrets(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getUserSecretKey());
        return new ServerEndpointExporter();
    }

}