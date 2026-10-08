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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单实时消息通道
 * 基于WebSocket实现的双向通信服务，用于管理端来单提醒和用户端订单状态推送
 * 连接建立后通过首条 authenticate 消息鉴权，避免 Token 进入 URL 和代理日志。
 */
@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer {

    private static final String ADMIN = "admin";
    private static final String USER = "user";
    private static final String AUTH_SID = "sky.auth.sid";
    private static final String AUTH_STARTED = "sky.auth.started";
    private static final String PREVIOUS_IDLE_TIMEOUT = "sky.auth.idleTimeout";
    private static final long AUTH_TIMEOUT_MILLIS = 10_000L;
    /** 所有客户端连接映射（sessionId -> ClientConnection） */
    private static final Map<String, ClientConnection> connections = new ConcurrentHashMap<>();
    /** 管理端JWT密钥，由WebSocketConfiguration注入 */
    private static volatile String adminSecretKey;
    /** 用户端JWT密钥，由WebSocketConfiguration注入 */
    private static volatile String userSecretKey;

    /**
     * 配置JWT密钥
     * 在应用启动时由WebSocketConfiguration调用
     *
     * @param adminSecret 管理端密钥
     * @param userSecret  用户端密钥
     */
    public static void configureJwtSecrets(String adminSecret, String userSecret) {
        adminSecretKey = adminSecret;
        userSecretKey = userSecret;
    }

    /**
     * 客户端连接建立时的回调
     * 等待认证首帧；未认证连接不加入广播映射，10秒内未认证自动关闭。
     *
     * @param session WebSocket会话
     * @param sid     会话标识（URL路径参数）
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        // 旧客户端必须刷新；继续接受 URL Token 会让日志泄露再次发生。
        if (session.getRequestParameterMap().containsKey("token")) {
            close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "unauthorized");
            return;
        }
        session.getUserProperties().put(AUTH_SID, sid);
        session.getUserProperties().put(AUTH_STARTED, System.currentTimeMillis());
        session.getUserProperties().put(PREVIOUS_IDLE_TIMEOUT, session.getMaxIdleTimeout());
        session.setMaxIdleTimeout(AUTH_TIMEOUT_MILLIS);
        session.setMaxTextMessageBufferSize(8192);
    }

    /**
     * 接收客户端消息
     * 支持ping/pong心跳保活和订单状态订阅事件
     *
     * @param payload 消息体（JSON字符串）
     * @param session WebSocket会话
     */
    @OnMessage
    public void onMessage(String payload, Session session) {
        ClientConnection connection = connections.get(session.getId());
        if (connection == null) {
            authenticateFirstMessage(payload, session);
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

    /**
     * 客户端连接关闭时的回调
     * 从内存映射中移除该连接
     *
     * @param session WebSocket会话
     */
    @OnClose
    public void onClose(Session session) {
        ClientConnection connection = connections.remove(session.getId());
        log.info("WebSocket客户端断开连接：role={}, principal={}, online={}",
                connection == null ? "unknown" : connection.role(),
                connection == null ? null : connection.principalId(), connections.size());
    }

    /**
     * 连接异常处理
     * 判断是否为正常断开（网络波动），记录日志后关闭连接
     *
     * @param session WebSocket会话
     * @param error   异常信息
     */
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

    /**
     * 向所有管理端客户端广播消息
     *
     * @param payload 消息载荷（JSON字符串）
     */
    public void sendToAdmins(String payload) {
        connections.values().stream()
                .filter(connection -> ADMIN.equals(connection.role()))
                .forEach(connection -> send(connection.session(), payload));
    }

    /**
     * 向指定用户推送订单状态变更通知
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @param status  订单状态
     * @param content 通知内容
     */
    public void sendOrderStatusToUser(Long userId, Long orderId, Integer status, String content) {
        if (userId == null) return;
        String payload = message("order.status.changed", orderId, status, content);
        connections.values().stream()
                .filter(connection -> USER.equals(connection.role()) && userId.equals(connection.principalId()))
                .forEach(connection -> send(connection.session(), payload));
    }

    /** 向指定用户推送精简通知事件；离线用户通过通知列表补拉。 */
    public void sendNotificationToUser(Long userId, String payload) {
        if (userId == null || payload == null) return;
        connections.values().stream()
                .filter(connection -> USER.equals(connection.role()) && userId.equals(connection.principalId()))
                .forEach(connection -> send(connection.session(), payload));
    }

    /**
     * 广播消息给所有客户端（仅发送给管理端）
     * 避免向用户泄露其他订单信息
     *
     * @param payload 消息载荷
     */
    public void sendToAllClient(String payload) {
        sendToAdmins(payload);
    }

    /**
     * 客户端鉴权
     * 校验首帧中的角色与 JWT；任何失败只返回通用原因，不记录载荷或令牌。
     */
    private void authenticateFirstMessage(String payload, Session session) {
        try {
            Object started = session.getUserProperties().get(AUTH_STARTED);
            if (!(started instanceof Long) || System.currentTimeMillis() - (Long) started > AUTH_TIMEOUT_MILLIS) {
                throw new IllegalArgumentException("authentication expired");
            }
            JSONObject request = JSON.parseObject(payload);
            if (request == null || !"authenticate".equals(request.getString("event"))) {
                throw new IllegalArgumentException("authentication required");
            }
            String sid = (String) session.getUserProperties().get(AUTH_SID);
            ClientConnection connection = authenticate(session, sid, request.getString("role"), request.getString("token"));
            // 完成认证后恢复常规空闲超时，并且只在此处开放业务广播。
            session.setMaxIdleTimeout((Long) session.getUserProperties().get(PREVIOUS_IDLE_TIMEOUT));
            session.getUserProperties().remove(AUTH_STARTED);
            connections.put(session.getId(), connection);
            send(session, message("connected", null, null, "实时连接已建立"));
        } catch (Exception exception) {
            connections.remove(session.getId());
            session.getUserProperties().remove(AUTH_STARTED);
            close(session, CloseReason.CloseCodes.VIOLATED_POLICY, "unauthorized");
        }
    }

    /**
     * 根据认证消息校验对应角色的签名密钥。
     *
     * @param session WebSocket会话
     * @param sid     会话标识
     * @return 客户端连接信息
     */
    private ClientConnection authenticate(Session session, String sid, String role, String token) {
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

    /**
     * 构建标准格式的WebSocket消息
     *
     * @param event   事件类型
     * @param orderId 订单ID
     * @param status  订单状态
     * @param content 消息内容
     * @return JSON格式消息字符串
     */
    private String message(String event, Long orderId, Integer status, String content) {
        JSONObject value = new JSONObject();
        value.put("event", event);
        value.put("timestamp", System.currentTimeMillis());
        if (orderId != null) value.put("orderId", orderId);
        if (status != null) value.put("status", status);
        if (content != null) value.put("content", content);
        return value.toJSONString();
    }

    /**
     * 异步发送消息到指定会话
     * 发送失败时自动从连接映射中移除
     *
     * @param session WebSocket会话
     * @param payload 消息载荷
     */
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

    /**
     * 关闭WebSocket会话
     *
     * @param session WebSocket会话
     * @param code    关闭码
     * @param reason  关闭原因
     */
    private void close(Session session, CloseReason.CloseCode code, String reason) {
        try {
            if (session != null && session.isOpen()) session.close(new CloseReason(code, reason));
        } catch (Exception ignored) {
        }
    }

    /**
     * 判断异常是否为正常断开（网络波动等）
     *
     * @param error 异常
     * @return 是否为断开异常
     */
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

    /**
     * 客户端连接记录
     *
     * @param session     WebSocket会话
     * @param role        角色（admin/user）
     * @param principalId 主体ID（员工ID或用户ID）
     * @param sid         会话标识
     */
    private record ClientConnection(Session session, String role, Long principalId, String sid) {
    }
}
