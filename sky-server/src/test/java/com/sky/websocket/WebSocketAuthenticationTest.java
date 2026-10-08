package com.sky.websocket;

import com.sky.constant.JwtClaimsConstant;
import com.sky.utils.JwtUtil;
import jakarta.websocket.CloseReason;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.SendHandler;
import jakarta.websocket.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证未认证连接收不到业务消息，认证首帧支持两种角色且不接受 URL 凭据。 */
class WebSocketAuthenticationTest {
    private static final String ADMIN_KEY = "test-admin-key-only-01234567890123456789012345";
    private static final String USER_KEY = "test-user-key-only-012345678901234567890123456";
    private final WebSocketServer server = new WebSocketServer();
    private Session session;
    private RemoteEndpoint.Async remote;

    @BeforeEach
    void setUp() {
        WebSocketServer.configureJwtSecrets(ADMIN_KEY, USER_KEY);
        session = mock(Session.class);
        remote = mock(RemoteEndpoint.Async.class);
        when(session.getId()).thenReturn("authentication-test");
        when(session.isOpen()).thenReturn(true);
        when(session.getUserProperties()).thenReturn(new HashMap<>());
        when(session.getRequestParameterMap()).thenReturn(Map.of());
        when(session.getAsyncRemote()).thenReturn(remote);
    }

    @AfterEach
    void cleanUp() { server.onClose(session); }

    @Test
    void waitsForAuthenticationWithoutBroadcasting() throws Exception {
        server.onOpen(session, "test-client");
        server.sendToAdmins("private-order");
        verify(remote, never()).sendText(anyString(), any(SendHandler.class));
        verify(session, never()).close(any(CloseReason.class));
        verify(session).setMaxIdleTimeout(10_000L);
    }

    @Test
    void authenticatesAdminThenDeliversMessages() throws Exception {
        server.onOpen(session, "test-client");
        server.onMessage(auth("admin", ADMIN_KEY, JwtClaimsConstant.EMP_ID), session);
        server.sendToAdmins("private-order");
        verify(remote).sendText(contains("connected"), any(SendHandler.class));
        verify(remote).sendText(eq("private-order"), any(SendHandler.class));
        verify(session, never()).close(any(CloseReason.class));
    }

    @Test
    void authenticatedUserReceivesOnlyOwnNotificationsAndCanSubscribe() throws Exception {
        server.onOpen(session, "test-client");
        server.onMessage(auth("user", USER_KEY, JwtClaimsConstant.USER_ID), session);
        server.onMessage("{\"event\":\"orders.subscribe\"}", session);
        server.sendNotificationToUser(7L, "own-order");
        server.sendNotificationToUser(8L, "other-order");
        server.sendToAdmins("admin-order");
        verify(remote).sendText(contains("orders.subscribed"), any(SendHandler.class));
        verify(remote).sendText(eq("own-order"), any(SendHandler.class));
        verify(remote, never()).sendText(eq("other-order"), any(SendHandler.class));
        verify(remote, never()).sendText(eq("admin-order"), any(SendHandler.class));
    }

    @Test
    void rejectsSubscriptionBeforeAuthentication() throws Exception {
        server.onOpen(session, "test-client");
        server.onMessage("{\"event\":\"orders.subscribe\"}", session);
        verify(session).close(argThat(reason -> reason.getCloseCode().getCode() == 1008));
    }

    @Test
    void rejectsTokenSignedForDifferentRole() throws Exception {
        server.onOpen(session, "test-client");
        server.onMessage(auth("admin", USER_KEY, JwtClaimsConstant.EMP_ID), session);
        verify(session).close(argThat(reason -> reason.getCloseCode().getCode() == 1008));
        verify(remote, never()).sendText(contains("connected"), any(SendHandler.class));
    }

    @Test
    void rejectsCredentialsInQueryString() throws Exception {
        when(session.getRequestParameterMap()).thenReturn(Map.of("token", java.util.List.of("test-token")));
        server.onOpen(session, "test-client");
        verify(session).close(argThat(reason -> reason.getCloseCode().getCode() == 1008));
    }

    private String auth(String role, String key, String claim) {
        String token = JwtUtil.createJWT(key, 60_000, Map.of(claim, 7L));
        return "{\"event\":\"authenticate\",\"role\":\"" + role + "\",\"token\":\"" + token + "\"}";
    }
}
