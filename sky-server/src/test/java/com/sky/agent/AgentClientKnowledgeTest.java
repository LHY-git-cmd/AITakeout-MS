package com.sky.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.agent.model.AgentSubmitRequest;
import com.sky.properties.AgentProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentClientKnowledgeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<RequestSnapshot> lastRequest = new AtomicReference<>();
    private HttpServer server;
    private AgentClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/knowledge", this::handleKnowledgeRequest);
        server.createContext("/api/v1/agent/submit", this::handleSubmitRequest);
        server.start();

        AgentProperties properties = new AgentProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        properties.setConnectTimeout(500);
        properties.setReadTimeout(1000);
        client = new AgentClient(new RestTemplateBuilder(), properties, objectMapper);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /**
     * 测试知识库相关方法是否与Python后端路由和JSON格式匹配
     */
    @Test
    void knowledgeMethodsMatchPythonRoutesAndJsonContract() throws Exception {
        Map<String, Object> request = Map.of(
                "task_id", "task-1",
                "document_id", "doc-1",
                "document_version", 2);

        Map<String, Object> submitted = client.indexKnowledge(request, file("dish_data.txt", "宫保鸡丁"));
        RequestSnapshot indexRequest = lastRequest.get();
        assertEquals("POST", indexRequest.method());
        assertEquals("/api/v1/knowledge/index", indexRequest.path());
        org.junit.jupiter.api.Assertions.assertTrue(indexRequest.body().contains("name=\"metadata\""));
        org.junit.jupiter.api.Assertions.assertTrue(indexRequest.body().contains("\"task_id\":\"task-1\""));
        org.junit.jupiter.api.Assertions.assertTrue(indexRequest.body().contains("name=\"file\""));
        org.junit.jupiter.api.Assertions.assertTrue(indexRequest.body().contains("宫保鸡丁"));
        assertEquals("pending", submitted.get("status"));

        Map<String, Object> status = client.getKnowledgeIndexStatus("task/1");
        RequestSnapshot statusRequest = lastRequest.get();
        assertEquals("GET", statusRequest.method());
        assertEquals("/api/v1/knowledge/index/task%2F1", statusRequest.path());
        assertEquals(25, ((Number) status.get("progress")).intValue());

        client.deleteKnowledgeDocument("doc/1", 2);
        RequestSnapshot deleteRequest = lastRequest.get();
        assertEquals("DELETE", deleteRequest.method());
        assertEquals("/api/v1/knowledge/documents/doc%2F1", deleteRequest.path());
        assertEquals("version=2", deleteRequest.query());
    }

    /**
     * 测试将Python后端的冲突（conflict）异常映射为自定义的、不可重试的异常
     */
    @Test
    void mapsPythonConflictToTypedNonRetryableException() {
        AgentClientException exception = assertThrows(AgentClientException.class,
                () -> client.indexKnowledge(Map.of("task_id", "conflict"), file("a.txt", "x")));

        assertEquals(AgentClientException.Reason.CONFLICT, exception.getReason());
        assertEquals(409, exception.getStatusCode());
        assertFalse(exception.isRetryable());
        assertEquals("提交知识库索引失败，HTTP 409: task_id reused", exception.getMessage());
    }

    /**
     * 测试提交任务时，是否使用兼容HTTP/1.1的客户端发送JSON格式的请求体
     */
    @Test
    void submitSendsJsonBodyUsingHttp11CompatibleClient() throws Exception {
        client.submit(new AgentSubmitRequest(
                "task-1", "session-1", 1L, "测试问题", "model-1", 0.2,
                Map.of("history", java.util.List.of())));

        RequestSnapshot request = lastRequest.get();
        JsonNode body = objectMapper.readTree(request.body());
        assertEquals("POST", request.method());
        assertEquals("/api/v1/agent/submit", request.path());
        assertEquals("task-1", body.path("task_id").asText());
        assertEquals("测试问题", body.path("query").asText());
    }

    /**
     * 测试当后端服务容量超限时，是否能正确映射为可重试的自定义异常
     */
    @Test
    void submitMapsCapacityToTypedRetryableException() {
        AgentClientException exception = assertThrows(AgentClientException.class,
                () -> client.submit(new AgentSubmitRequest(
                        "capacity", "session-1", 1L, "测试问题", "model-1", 0.2,
                        Map.of("history", java.util.List.of()))));

        assertEquals(AgentClientException.Reason.CAPACITY_EXCEEDED, exception.getReason());
        assertEquals(429, exception.getStatusCode());
        org.junit.jupiter.api.Assertions.assertTrue(exception.isRetryable());
    }

    private void handleSubmitRequest(HttpExchange exchange) throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        lastRequest.set(new RequestSnapshot(
                exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                exchange.getRequestURI().getRawQuery(),
                new String(requestBody, StandardCharsets.UTF_8)));
        if (new String(requestBody, StandardCharsets.UTF_8).contains("\"task_id\":\"capacity\"")) {
            respond(exchange, 429,
                    "{\"detail\":{\"error_type\":\"CAPACITY_EXCEEDED\",\"message\":\"full\"}}");
            return;
        }
        respond(exchange, 200,
                "{\"task_id\":\"task-1\",\"status\":\"pending\","
                        + "\"stream_url\":\"/api/v1/agent/stream/task-1\"}");
    }

    private void handleKnowledgeRequest(HttpExchange exchange) throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        String path = exchange.getRequestURI().getRawPath();
        lastRequest.set(new RequestSnapshot(
                exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(),
                new String(requestBody, StandardCharsets.UTF_8)));

        if ("POST".equals(exchange.getRequestMethod())
                && "/api/v1/knowledge/index".equals(path)) {
            if (new String(requestBody, StandardCharsets.UTF_8)
                    .contains("\"task_id\":\"conflict\"")) {
                respond(exchange, 409, "{\"detail\":\"task_id reused\"}");
            } else {
                respond(exchange, 200, "{\"task_id\":\"task-1\",\"status\":\"pending\"}");
            }
            return;
        }
        if ("GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 200, "{\"status\":\"embedding\",\"progress\":25}");
            return;
        }
        respond(exchange, 200, "{\"status\":\"deleted\"}");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record RequestSnapshot(String method, String path, String query, String body) {
    }

    private ByteArrayResource file(String name, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return name;
            }
        };
    }
}