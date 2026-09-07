package com.sky.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Test
    void mapsPythonConflictToTypedNonRetryableException() {
        AgentClientException exception = assertThrows(AgentClientException.class,
                () -> client.indexKnowledge(Map.of("task_id", "conflict"), file("a.txt", "x")));

        assertEquals(AgentClientException.Reason.CONFLICT, exception.getReason());
        assertEquals(409, exception.getStatusCode());
        assertFalse(exception.isRetryable());
        assertEquals("提交知识库索引失败，HTTP 409: task_id reused", exception.getMessage());
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
