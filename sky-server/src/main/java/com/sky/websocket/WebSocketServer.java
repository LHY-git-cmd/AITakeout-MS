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
import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket服务
 */
@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer {

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
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.debug("收到WebSocket客户端消息：sid={}, message={}", sid, message);
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        sessionMap.remove(sid);
        log.info("WebSocket客户端断开连接：sid={}, online={}", sid, sessionMap.size());
    }

    /**
     * 连接异常处理：将IO异常和ClosedChannel视为正常断开，不再输出warn级别日志
     */
    @OnError
    public void onError(Session session, Throwable error, @PathParam("sid") String sid) {
        sessionMap.remove(sid, session);
        if (isDisconnectException(error)) {
            log.info("WebSocket连接异常断开：sid={}, online={}", sid, sessionMap.size());
        } else {
            log.warn("WebSocket连接异常：sid={}", sid, error);
        }
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 判断是否为客户端主动断开导致的异常
     */
    private boolean isDisconnectException(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof ClosedChannelException) {
                return true;
            }
            String simpleName = cause.getClass().getSimpleName();
            if (simpleName.contains("Closed") || simpleName.contains("Broken")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 群发消息
     */
    public void sendToAllClient(String message) {
        for (Map.Entry<String, Session> entry : sessionMap.entrySet()) {
            String sid = entry.getKey();
            Session session = entry.getValue();
            if (!session.isOpen()) {
                sessionMap.remove(sid, session);
                continue;
            }
            try {
                session.getAsyncRemote().sendText(message, result -> {
                    if (!result.isOK()) {
                        Throwable ex = result.getException();
                        sessionMap.remove(sid, session);
                        if (ex != null && isDisconnectException(ex)) {
                            log.info("WebSocket消息发送失败(连接已关闭)：sid={}", sid);
                        } else {
                            log.warn("WebSocket消息发送失败：sid={}", sid, ex);
                        }
                    }
                });
            } catch (Exception exception) {
                sessionMap.remove(sid, session);
                if (isDisconnectException(exception)) {
                    log.info("WebSocket消息发送异常(连接已关闭)：sid={}", sid);
                } else {
                    log.warn("WebSocket消息发送异常：sid={}", sid, exception);
                }
            }
        }
    }

}