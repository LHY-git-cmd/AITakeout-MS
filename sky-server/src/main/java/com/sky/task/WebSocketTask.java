package com.sky.task;

import com.alibaba.fastjson.JSON;
import com.sky.websocket.WebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket测试任务（仅用于开发测试，生产环境应注释掉@Scheduled）
 */
@Component
@Profile("websocket-demo")
public class WebSocketTask {
    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 仅在启用websocket-demo Profile时发送测试消息，不影响正常业务环境。
     */
    @Scheduled(cron = "0/5 * * * * ?")
    public void sendMessageToClient() {
        Map<String, Object> message = new HashMap<>();
        message.put("type", 1);
        message.put("orderId", 0);
        message.put("content", "WebSocket连接测试消息");
        webSocketServer.sendToAllClient(JSON.toJSONString(message));
    }
}
