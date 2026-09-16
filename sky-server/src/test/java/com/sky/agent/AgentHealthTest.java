package com.sky.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.controller.admin.AgentController;
import com.sky.properties.AgentProperties;
import com.sky.result.Result;
import com.sky.vo.AgentHealthVO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentHealthTest {

    private HttpServer server;
    private AgentClient client;
    private int readinessStatus = 503;
    private String readinessBody = "{\"service\":\"Python Agent Service\",\"status\":\"degraded\","
            + "\"checked_at\":\"2026-09-15T12:00:00Z\","
            + "\"error_type\":\"QDRANT_UNAVAILABLE\"}";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health/ready", this::handleReadiness);
        server.start();
        AgentProperties properties = new AgentProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setConnectTimeout(500);
        properties.setReadTimeout(1000);
        client = new AgentClient(new RestTemplateBuilder(), properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /** 验证 Python 503 readiness 能转换为前端可展示的脱敏降级状态。 */
    @Test
    void mapsReadiness503ToTypedDegradedHealth() {
        AgentController controller = new AgentController(null, client, null, new ObjectMapper());
        Result<AgentHealthVO> result = controller.health();
        AgentHealthVO health = result.getData();

        assertEquals("Python Agent Service", health.getService());
        assertEquals("degraded", health.getStatus());
        assertEquals("2026-09-15T12:00:00Z", health.getCheckedAt());
        assertEquals("QDRANT_UNAVAILABLE", health.getErrorType());
    }

    /** 正常状态不能携带由异常回退路径生成的错误类型。 */
    @Test
    void keepsOnlineHealthFreeOfErrorType() {
        readinessStatus = 200;
        readinessBody = "{\"service\":\"Python Agent Service\",\"status\":\"online\","
                + "\"checked_at\":\"2026-09-15T12:00:00Z\"}";
        AgentController controller = new AgentController(null, client, null, new ObjectMapper());

        AgentHealthVO health = controller.health().getData();

        assertEquals("online", health.getStatus());
        org.junit.jupiter.api.Assertions.assertNull(health.getErrorType());
    }

    private void handleReadiness(HttpExchange exchange) throws IOException {
        byte[] body = readinessBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(readinessStatus, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
