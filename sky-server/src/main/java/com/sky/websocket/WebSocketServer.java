package com.sky.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket服务
 */
@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer {

    //存放会话对象（使用ConcurrentHashMap保证线程安全）
    private static final Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        Session previousSession = sessionMap.put(sid, session);
        if (previousSession != null && previousSession.isOpen()) {
            try {
                previousSession.close();
            } catch (Exception exception) {
                log.warn("关闭重复WebSocket连接失败：sid={}", sid, exception);
            }
        }
        log.info("WebSocket客户端建立连接：sid={}, online={}", sid, sessionMap.size());
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.debug("收到WebSocket客户端消息：sid={}, message={}", sid, message);
    }

    /**
     * 连接关闭调用的方法
     *
     * @param sid
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        sessionMap.remove(sid);
        log.info("WebSocket客户端断开连接：sid={}, online={}", sid, sessionMap.size());
    }

    @OnError
    public void onError(Session session, Throwable error, @PathParam("sid") String sid) {
        sessionMap.remove(sid, session);
        log.warn("WebSocket连接异常：sid={}", sid, error);
    }

    /**
     * 群发消息
     *
     * @param message 要发送的消息
     */
    public void sendToAllClient(String message) {
        for (Map.Entry<String, Session> entry : sessionMap.entrySet()) {
            Session session = entry.getValue();
            if (!session.isOpen()) {
                sessionMap.remove(entry.getKey(), session);
                continue;
            }
            try {
                session.getAsyncRemote().sendText(message, result -> {
                    if (!result.isOK()) {
                        sessionMap.remove(entry.getKey(), session);
                        log.warn("WebSocket消息发送失败：sid={}", entry.getKey(), result.getException());
                    }
                });
            } catch (Exception exception) {
                sessionMap.remove(entry.getKey(), session);
                log.warn("WebSocket消息发送异常：sid={}", entry.getKey(), exception);
            }
        }
    }

}
