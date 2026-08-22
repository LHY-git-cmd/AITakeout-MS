package com.sky.websocket;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.JwtClaimsConstant;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 订单实时消息通道。 */
@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer {

    private static final String ADMIN = "admin";
    private static final String USER = "user";
    private static final Map<String, ClientConnection> connections = new ConcurrentHashMap<>();
    private static volatile String adminSecretKey;
    private static volatile String userSecretKey;

    public static void configureJwtSecrets(String adminSecret, String userSecret) {
        adminSecretKey = adminSecret;
        userSecretKey = userSecret;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        try {
            ClientConnection connection = authenticate(session, sid);
            connections.put(session.getId(), connection);
            log.info("WebSocket客户端建立连接：role={}, principal={}, online={}",
                    connection.role(), connection.principalId(), connections.size());
            send(session, message("connected", null, null, "实时连接已建立"));
        } catch (Exception exception) {
            log.warn("WebSocket握手认证失败：sid={}", sid);
            close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "unauthorized");
        }
    }

    @OnMessage
    public void onMessage(String payload, Session session) {
        ClientConnection connection = connections.get(session.getId());
        if (connection == null) {
            close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "unauthorized");
            return;
        }
        try {
            JSONObject request = JSON.parseObject(payload);
            String event = request.getString("event");
            if ("ping".equals(event)) {
                send(session, message("pong", null, null, null));
            } else if ("orders.subscribe".equals(event) && USER.equals(connection.role())) {
                send(session, message("orders.subscribed", null, null, "订单状态订阅成功"));
            } else {
                send(session, message("error", null, null, "不支持的实时消息"));
            }
        } catch (Exception exception) {
            send(session, message("error", null, null, "实时消息格式错误"));
        }
    }

    @OnClose
    public void onClose(Session session) {
        ClientConnection connection = connections.remove(session.getId());
        log.info("WebSocket客户端断开连接：role={}, principal={}, online={}",
                connection == null ? "unknown" : connection.role(),
                connection == null ? null : connection.principalId(), connections.size());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        ClientConnection connection = connections.remove(session.getId());
        if (isDisconnectException(error)) {
            log.info("WebSocket连接异常断开：role={}, principal={}, online={}",
                    connection == null ? "unknown" : connection.role(),
                    connection == null ? null : connection.principalId(), connections.size());
        } else {
            log.warn("WebSocket连接异常：session={}", session.getId(), error);
        }
        close(session, CloseReason.CloseCodes.UNEXPECTED_CONDITION, "connection error");
    }

    public void sendToAdmins(String payload) {
        connections.values().stream()
                .filter(connection -> ADMIN.equals(connection.role()))
                .forEach(connection -> send(connection.session(), payload));
    }

    public void sendOrderStatusToUser(Long userId, Long orderId, Integer status, String content) {
        if (userId == null) return;
        String payload = message("order.status.changed", orderId, status, content);
        connections.values().stream()
                .filter(connection -> USER.equals(connection.role()) && userId.equals(connection.principalId()))
                .forEach(connection -> send(connection.session(), payload));
    }

    /** 原有广播只发给管理端，避免向用户泄露其他订单。 */
    public void sendToAllClient(String payload) {
        sendToAdmins(payload);
    }

    private ClientConnection authenticate(Session session, String sid) {
        Map<String, List<String>> params = session.getRequestParameterMap();
        String role = first(params, "role");
        String token = first(params, "token");
        if (role == null || token == null) throw new IllegalArgumentException("missing credentials");

        if (USER.equals(role)) {
            Claims claims = JwtUtil.parseJWT(userSecretKey, token);
            Long userId = claims.get(JwtClaimsConstant.USER_ID, Long.class);
            if (userId == null) throw new IllegalArgumentException("missing user id");
            return new ClientConnection(session, USER, userId, sid);
        }
        if (ADMIN.equals(role)) {
            Claims claims = JwtUtil.parseJWT(adminSecretKey, token);
            Long employeeId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
            return new ClientConnection(session, ADMIN, employeeId, sid);
        }
        throw new IllegalArgumentException("invalid role");
    }

    private String first(Map<String, List<String>> params, String name) {
        List<String> values = params.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private String message(String event, Long orderId, Integer status, String content) {
        JSONObject value = new JSONObject();
        value.put("event", event);
        value.put("timestamp", System.currentTimeMillis());
        if (orderId != null) value.put("orderId", orderId);
        if (status != null) value.put("status", status);
        if (content != null) value.put("content", content);
        return value.toJSONString();
    }

    private void send(Session session, String payload) {
        if (session == null || !session.isOpen()) {
            if (session != null) connections.remove(session.getId());
            return;
        }
        try {
            session.getAsyncRemote().sendText(payload, result -> {
                if (!result.isOK()) connections.remove(session.getId());
            });
        } catch (Exception exception) {
            connections.remove(session.getId());
            log.debug("WebSocket消息发送失败：session={}", session.getId(), exception);
        }
    }

    private void close(Session session, CloseReason.CloseCode code, String reason) {
        try {
            if (session != null && session.isOpen()) session.close(new CloseReason(code, reason));
        } catch (Exception ignored) {
        }
    }

    private boolean isDisconnectException(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof ClosedChannelException) return true;
            String name = cause.getClass().getSimpleName();
            if (name.contains("Closed") || name.contains("Broken")) return true;
            cause = cause.getCause();
        }
        return false;
    }

    private record ClientConnection(Session session, String role, Long principalId, String sid) {
    }
}
