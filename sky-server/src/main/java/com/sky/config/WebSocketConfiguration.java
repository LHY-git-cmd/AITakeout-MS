package com.sky.config;

import com.sky.properties.JwtProperties;
import com.sky.websocket.WebSocketServer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket配置类，用于注册WebSocket的Bean
 */
@Configuration
@RequiredArgsConstructor
public class WebSocketConfiguration {

    private final JwtProperties jwtProperties;

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        WebSocketServer.configureJwtSecrets(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getUserSecretKey());
        return new ServerEndpointExporter();
    }

}
