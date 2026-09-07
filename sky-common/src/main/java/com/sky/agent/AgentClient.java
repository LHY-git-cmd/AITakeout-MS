package com.sky.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.agent.model.AgentStreamEvent;
import com.sky.agent.model.AgentSubmitRequest;
import com.sky.agent.model.AgentSubmitResponse;
import com.sky.agent.model.AgentTaskStatusResponse;
import com.sky.properties.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.function.Consumer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Agent智能体HTTP客户端
 * 封装与Python Agent服务的所有HTTP接口通信
 *
 * 支持的接口：
 * - GET  /                       服务信息
 * - GET  /health                 健康检查
 * - GET  /docs                   Swagger UI文档
 * - POST /api/v1/agent/query     同步查询
 * - POST /api/v1/agent/stream    流式SSE查询
 * - POST /api/v1/agent/submit    异步提交
 * - GET  /api/v1/agent/status/{id} 轮询状态
 * - GET  /api/v1/agent/tasks     任务列表
 */
@Slf4j
@Service
public class AgentClient {

    private static final String KNOWLEDGE_INDEX_PATH = "/api/v1/knowledge/index";
    private static final String KNOWLEDGE_DOCUMENT_PATH = "/api/v1/knowledge/documents";

    private final RestTemplate restTemplate;
    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper;

    public AgentClient(RestTemplateBuilder restTemplateBuilder, AgentProperties agentProperties,
                       ObjectMapper objectMapper) {
        this.agentProperties = agentProperties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofMillis(agentProperties.getConnectTimeout()))
                .readTimeout(Duration.ofMillis(agentProperties.getReadTimeout()))
                .build();
    }

    /**
     * 获取服务信息
     *
     * @return 服务信息JSON字符串
     */
    public String getServiceInfo() {
        String url = agentProperties.getBaseUrl() + "/";
        log.info("调用Agent服务信息接口: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        return response.getBody();
    }

    /**
     * 健康检查
     *
     * @return 健康状态JSON字符串
     */
    public String healthCheck() {
        String url = agentProperties.getBaseUrl() + "/health";
        log.info("调用Agent健康检查接口: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        return response.getBody();
    }

    /**
     * 同步查询
     * 发送请求到Agent并等待完整响应
     *
     * @param query  查询问题内容
     * @return Agent响应JSON字符串
     */
    public String query(String query) {
        return query(Collections.singletonMap("query", query));
    }

    /**
     * 同步查询（自定义参数）
     *
     * @param params 请求参数Map
     * @return Agent响应JSON字符串
     */
    public String query(Map<String, Object> params) {
        String url = agentProperties.getBaseUrl() + "/api/v1/agent/query";
        log.info("调用Agent同步查询接口: {}, 参数: {}", url, params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        String jsonBody = JSON.toJSONString(params);
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        return response.getBody();
    }

    /**
     * 流式SSE查询
     * 使用RestTemplate实现，通过ResponseExtractor处理流式响应
     *
     * @param query 查询问题内容
     * @return SSE事件列表，每个元素为一个事件JSON字符串
     */
    public List<String> streamQuery(String query) {
        return streamQuery(Collections.singletonMap("query", query));
    }

    /**
     * 流式SSE查询（自定义参数）
     * 使用底层HttpURLConnection处理SSE流式响应
     *
     * @param params 请求参数Map
     * @return SSE事件列表，每个元素为一个事件JSON字符串
     */
    public List<String> streamQuery(Map<String, Object> params) {
        String url = agentProperties.getBaseUrl() + "/api/v1/agent/stream";
        log.info("调用Agent流式查询接口: {}, 参数: {}", url, params);

        List<String> events = new ArrayList<>();
        HttpURLConnection connection = null;

        try {
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setConnectTimeout(agentProperties.getConnectTimeout());
            connection.setReadTimeout(agentProperties.getReadTimeout());
            connection.setDoOutput(true);

            String jsonBody = JSON.toJSONString(params);
            try (java.io.OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            log.info("SSE响应状态码: {}", responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder eventBuilder = new StringBuilder();

                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            if (eventBuilder.length() > 0) {
                                events.add(eventBuilder.toString().trim());
                                eventBuilder.setLength(0);
                            }
                        } else if (line.startsWith("data:")) {
                            String data = line.substring(5).trim();
                            if (!"[DONE]".equals(data)) {
                                eventBuilder.append(data);
                            }
                        }
                    }

                    if (eventBuilder.length() > 0) {
                        events.add(eventBuilder.toString().trim());
                    }
                }
            } else {
                log.error("SSE请求失败, 状态码: {}", responseCode);
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    String errorLine;
                    StringBuilder errorMsg = new StringBuilder();
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorMsg.append(errorLine);
                    }
                    log.error("SSE错误响应: {}", errorMsg);
                }
            }
        } catch (Exception e) {
            log.error("SSE流式查询异常", e);
            throw new RuntimeException("Agent流式查询请求失败", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return events;
    }

        /**
     * 流式SSE查询（回调模式）
     * 每收到一个SSE事件就立即回调consumer，实现真正的实时流式推送
     *
     * @param params        请求参数Map
     * @param eventConsumer 每个SSE事件的回调处理
     */
    public void streamQuery(Map<String, Object> params, Consumer<String> eventConsumer) {
        String url = agentProperties.getBaseUrl() + "/api/v1/agent/stream";
        log.info("调用Agent流式查询接口(回调模式): {}, 参数: {}", url, params);

        HttpURLConnection connection = null;

        try {
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setConnectTimeout(agentProperties.getConnectTimeout());
            connection.setReadTimeout(agentProperties.getReadTimeout());
            connection.setDoOutput(true);

            String jsonBody = JSON.toJSONString(params);
            try (java.io.OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            log.info("SSE响应状态码: {}", responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder eventBuilder = new StringBuilder();

                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            if (eventBuilder.length() > 0) {
                                eventConsumer.accept(eventBuilder.toString().trim());
                                eventBuilder.setLength(0);
                            }
                        } else if (line.startsWith("data:")) {
                            String data = line.substring(5).trim();
                            if (!"[DONE]".equals(data)) {
                                eventBuilder.append(data);
                            }
                        }
                    }

                    if (eventBuilder.length() > 0) {
                        eventConsumer.accept(eventBuilder.toString().trim());
                    }
                }
            } else {
                log.error("SSE请求失败, 状态码: {}", responseCode);
                try (BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    String errorLine;
                    StringBuilder errorMsg = new StringBuilder();
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorMsg.append(errorLine);
                    }
                    log.error("SSE错误响应: {}", errorMsg);
                }
                throw new RuntimeException("Agent流式查询请求失败, 状态码: " + responseCode);
            }
        } catch (Exception e) {
            log.error("SSE流式查询异常", e);
            throw new RuntimeException("Agent流式查询请求失败", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 订阅指定任务的SSE事件流
     *
     * @param taskId        任务ID
     * @param eventConsumer 事件回调
     */
    public void subscribeTaskEvents(String taskId, Consumer<String> eventConsumer) {
        String url = agentProperties.getBaseUrl() + "/api/v1/agent/stream/" + taskId;
        log.info("订阅任务SSE事件流: {}", url);

        HttpURLConnection connection = null;
        try {
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("GET");  // 注意是GET
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setConnectTimeout(agentProperties.getConnectTimeout());
            connection.setReadTimeout(agentProperties.getReadTimeout());

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder eventBuilder = new StringBuilder();

                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            if (eventBuilder.length() > 0) {
                                eventConsumer.accept(eventBuilder.toString().trim());
                                eventBuilder.setLength(0);
                            }
                        } else if (line.startsWith("data:")) {
                            String data = line.substring(5).trim();
                            if (!"[DONE]".equals(data)) {
                                eventBuilder.append(data);
                            }
                        }
                    }
                    if (eventBuilder.length() > 0) {
                        eventConsumer.accept(eventBuilder.toString().trim());
                    }
                }
            } else {
                throw new RuntimeException("订阅SSE流失败, 状态码: " + responseCode);
            }
        } catch (Exception e) {
            log.error("订阅任务SSE事件流异常, taskId={}", taskId, e);
            throw new RuntimeException("订阅任务SSE事件流失败", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 提交新链路任务。Java生成taskId，Python必须原样返回。
     */
    public AgentSubmitResponse submit(AgentSubmitRequest request) {
        String url = agentProperties.getBaseUrl() + "/api/v1/agent/submit";
        ResponseEntity<AgentSubmitResponse> response = restTemplate.postForEntity(
                url, request, AgentSubmitResponse.class);
        AgentSubmitResponse body = response.getBody();
        if (body == null || body.taskId() == null) {
            throw new IllegalStateException("Agent提交响应缺少task_id");
        }
        if (!request.taskId().equals(body.taskId())) {
            throw new IllegalStateException("Agent返回的task_id与请求不一致");
        }
        return body;
    }

    /**
     * 从指定事件序号后订阅结构化任务事件。
     */
    public void subscribeTaskEvents(String taskId, int lastEventId,
                                    Consumer<AgentStreamEvent> eventConsumer) {
        String url = agentProperties.getBaseUrl() + "/api/v1/agent/stream/" + taskId;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("Last-Event-ID", String.valueOf(Math.max(0, lastEventId)));
            connection.setConnectTimeout(agentProperties.getConnectTimeout());
            connection.setReadTimeout(agentProperties.getReadTimeout());

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("订阅SSE流失败, 状态码: " + responseCode);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                StringBuilder data = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        emitStructuredEvent(data, eventConsumer);
                    } else if (line.startsWith("data:")) {
                        if (!data.isEmpty()) {
                            data.append('\n');
                        }
                        data.append(line.substring(5).trim());
                    }
                }
                emitStructuredEvent(data, eventConsumer);
            }
        } catch (Exception e) {
            throw new RuntimeException("订阅任务SSE事件流失败: " + taskId, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public AgentTaskStatusResponse cancelTask(String taskId) {
        String url = agentProperties.getBaseUrl()
                + "/api/v1/agent/tasks/" + taskId + "/cancel";
        return restTemplate.postForObject(url, null, AgentTaskStatusResponse.class);
    }

    public AgentTaskStatusResponse getTaskStatus(String taskId) {
        String url = agentProperties.getBaseUrl() + "/api/v1/agent/status/" + taskId;
        return restTemplate.getForObject(url, AgentTaskStatusResponse.class);
    }

    /**
     * 提交知识库文档索引任务。Python 接口仅受理任务，实际索引进度由状态接口轮询。
     */
    public Map<String, Object> indexKnowledge(Map<String, Object> request, Resource file) {
        Objects.requireNonNull(request, "知识库索引请求不能为空");
        Objects.requireNonNull(file, "知识库索引文件不能为空");
        URI uri = knowledgeUri(KNOWLEDGE_INDEX_PATH).build().encode().toUri();
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        HttpHeaders metadataHeaders = new HttpHeaders();
        metadataHeaders.setContentType(MediaType.APPLICATION_JSON);
        try {
            parts.add("metadata", new HttpEntity<>(
                    objectMapper.writeValueAsString(request), metadataHeaders));
        } catch (JsonProcessingException exception) {
            throw new AgentClientException(AgentClientException.Reason.SERIALIZATION,
                    null, false, "提交知识库索引请求序列化失败", exception);
        }
        parts.add("file", file);
        return exchangeKnowledgeMultipart("提交知识库索引", uri, parts);
    }

    private Map<String, Object> exchangeKnowledgeMultipart(
            String operation, URI uri, MultiValueMap<String, Object> parts) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        try {
            log.info("调用Agent知识库接口: operation={}, method=POST, uri={}", operation, uri);
            ResponseEntity<String> response = restTemplate.exchange(
                    uri, HttpMethod.POST, new HttpEntity<>(parts, headers), String.class);
            if (response.getBody() == null || response.getBody().isBlank()) {
                throw new AgentClientException(AgentClientException.Reason.INVALID_RESPONSE,
                        response.getStatusCode().value(), false, operation + "响应体为空", null);
            }
            return objectMapper.readValue(response.getBody(),
                    new TypeReference<Map<String, Object>>() { });
        } catch (AgentClientException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw mapKnowledgeHttpException(operation, exception);
        } catch (ResourceAccessException exception) {
            boolean timeout = hasCause(exception, SocketTimeoutException.class);
            throw new AgentClientException(
                    timeout ? AgentClientException.Reason.TIMEOUT : AgentClientException.Reason.UNAVAILABLE,
                    null, true, timeout ? operation + "超时" : operation + "失败，Agent服务不可用",
                    exception);
        } catch (JsonProcessingException exception) {
            throw new AgentClientException(AgentClientException.Reason.INVALID_RESPONSE,
                    null, false, operation + "响应不是有效JSON", exception);
        } catch (RestClientException exception) {
            throw new AgentClientException(AgentClientException.Reason.UNAVAILABLE,
                    null, true, operation + "失败，无法访问Agent服务", exception);
        }
    }

    /**
     * 查询 Python 侧知识库索引任务状态。
     */
    public Map<String, Object> getKnowledgeIndexStatus(String taskId) {
        requirePathValue(taskId, "taskId");
        URI uri = knowledgeUri(KNOWLEDGE_INDEX_PATH)
                .pathSegment(taskId)
                .build()
                .encode()
                .toUri();
        return exchangeKnowledge("查询知识库索引状态", uri, HttpMethod.GET, null, true);
    }

    /**
     * 删除文档的全部向量，或只删除指定版本的向量。
     */
    public void deleteKnowledgeDocument(String documentId, Integer version) {
        requirePathValue(documentId, "documentId");
        if (version != null && version <= 0) {
            throw new IllegalArgumentException("version必须大于0");
        }
        UriComponentsBuilder builder = knowledgeUri(KNOWLEDGE_DOCUMENT_PATH)
                .pathSegment(documentId);
        if (version != null) {
            builder.queryParam("version", version);
        }
        exchangeKnowledge("删除知识库文档向量", builder.build().encode().toUri(),
                HttpMethod.DELETE, null, false);
    }

    private Map<String, Object> exchangeKnowledge(String operation, URI uri,
                                                   HttpMethod method, Object request,
                                                   boolean responseRequired) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        String jsonBody = null;
        if (request != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            try {
                jsonBody = objectMapper.writeValueAsString(request);
            } catch (JsonProcessingException exception) {
                throw new AgentClientException(AgentClientException.Reason.SERIALIZATION,
                        null, false, operation + "请求序列化失败", exception);
            }
        }

        try {
            log.info("调用Agent知识库接口: operation={}, method={}, uri={}", operation, method, uri);
            ResponseEntity<String> response = restTemplate.exchange(
                    uri, method, new HttpEntity<>(jsonBody, headers), String.class);
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                if (!responseRequired) {
                    return Collections.emptyMap();
                }
                throw new AgentClientException(AgentClientException.Reason.INVALID_RESPONSE,
                        response.getStatusCode().value(), false,
                        operation + "响应体为空", null);
            }
            try {
                return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() { });
            } catch (JsonProcessingException exception) {
                throw new AgentClientException(AgentClientException.Reason.INVALID_RESPONSE,
                        response.getStatusCode().value(), false,
                        operation + "响应不是有效JSON", exception);
            }
        } catch (AgentClientException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw mapKnowledgeHttpException(operation, exception);
        } catch (ResourceAccessException exception) {
            boolean timeout = hasCause(exception, SocketTimeoutException.class);
            AgentClientException.Reason reason = timeout
                    ? AgentClientException.Reason.TIMEOUT
                    : AgentClientException.Reason.UNAVAILABLE;
            String message = timeout
                    ? operation + "超时"
                    : operation + "失败，Agent服务不可用";
            throw new AgentClientException(reason, null, true, message, exception);
        } catch (RestClientException exception) {
            throw new AgentClientException(AgentClientException.Reason.UNAVAILABLE,
                    null, true, operation + "失败，无法访问Agent服务", exception);
        }
    }

    private AgentClientException mapKnowledgeHttpException(
            String operation, RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        AgentClientException.Reason reason;
        boolean retryable = false;
        if (status == HttpStatus.CONFLICT.value()) {
            reason = AgentClientException.Reason.CONFLICT;
        } else if (status == HttpStatus.NOT_FOUND.value()) {
            reason = AgentClientException.Reason.NOT_FOUND;
        } else if (status >= 400 && status < 500) {
            reason = AgentClientException.Reason.INVALID_REQUEST;
        } else {
            reason = AgentClientException.Reason.REMOTE_ERROR;
            retryable = status >= 500;
        }
        String detail = responseDetail(exception.getResponseBodyAsString());
        String message = operation + "失败，HTTP " + status;
        if (detail != null && !detail.isBlank()) {
            message += ": " + detail;
        }
        return new AgentClientException(reason, status, retryable, message, exception);
    }

    private String responseDetail(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            String detail = objectMapper.readTree(responseBody).path("detail").asText(null);
            return detail == null ? abbreviate(responseBody) : abbreviate(detail);
        } catch (JsonProcessingException exception) {
            return abbreviate(responseBody);
        }
    }

    private String abbreviate(String value) {
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (causeType.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private UriComponentsBuilder knowledgeUri(String path) {
        String baseUrl = agentProperties.getBaseUrl().replaceAll("/+$", "");
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path(path);
    }

    private void requirePathValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
    }

    private void emitStructuredEvent(StringBuilder data,
                                     Consumer<AgentStreamEvent> eventConsumer) throws Exception {
        if (data.isEmpty()) {
            return;
        }
        AgentStreamEvent event = objectMapper.readValue(data.toString(), AgentStreamEvent.class);
        data.setLength(0);
        if (event.taskId() == null || event.seqNo() == null || event.event() == null) {
            throw new IllegalArgumentException("Agent事件缺少必要字段");
        }
        eventConsumer.accept(event);
    }

    /**
     * 异步提交任务
     * 提交任务后返回任务ID，后续可通过轮询状态接口查询执行结果
     *
     * @param query  查询问题内容
     * @return 任务提交响应JSON字符串（包含任务ID）
     */
    public String submit(String query) {
        return submit(Collections.singletonMap("query", query));
    }

    /**
     * 异步提交任务（自定义参数）
     *
     * @param params 请求参数Map
     * @return 任务提交响应JSON字符串（包含任务ID）
     */
    public String submit(Map<String, Object> params) {
        String url = agentProperties.getBaseUrl() + "/api/v1/agent/submit";
        log.info("调用Agent异步提交接口: {}, 参数: {}", url, params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        String jsonBody = JSON.toJSONString(params);
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        return response.getBody();
    }

    /**
     * 根据ID获取任务状态
     *
     * @param id 任务ID
     * @return 任务状态JSON字符串
     */
    public String getStatus(String id) {
        String url = agentProperties.getBaseUrl() + "/api/v1/agent/status/" + id;
        log.info("调用Agent任务状态接口: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        return response.getBody();
    }

    /**
     * 获取任务列表
     *
     * @return 任务列表JSON字符串
     */
    public String getTasks() {
        return getTasks(null);
    }

    /**
     * 获取任务列表（带筛选参数）
     *
     * @param params 查询参数（如状态筛选、分页等）
     * @return 任务列表JSON字符串
     */
    public String getTasks(Map<String, String> params) {
        StringBuilder urlBuilder = new StringBuilder(agentProperties.getBaseUrl() + "/api/v1/agent/tasks");

        if (params != null && !params.isEmpty()) {
            urlBuilder.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) {
                    urlBuilder.append("&");
                }
                urlBuilder.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
        }

        String url = urlBuilder.toString();
        log.info("调用Agent任务列表接口: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        return response.getBody();
    }

    /**
     * 轮询等待任务完成
     * 定时查询任务状态，直到任务完成或超时
     *
     * @param id          任务ID
     * @param maxWaitMs   最大等待时间（毫秒）
     * @param intervalMs  轮询间隔（毫秒）
     * @return 最终任务状态JSON字符串
     */
    public String waitForCompletion(String id, long maxWaitMs, long intervalMs) {
        log.info("开始轮询任务状态, 任务ID: {}, 最大等待: {}ms, 轮询间隔: {}ms", id, maxWaitMs, intervalMs);
        long startTime = System.currentTimeMillis();
        int pollCount = 0;

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            pollCount++;
            String status = getStatus(id);

            try {
                JSONObject json = JSON.parseObject(status);
                String state = json.getString("status");

                if ("completed".equalsIgnoreCase(state) || "done".equalsIgnoreCase(state) || "success".equalsIgnoreCase(state)) {
                    log.info("任务完成, 轮询次数: {}", pollCount);
                    return status;
                }
                if ("failed".equalsIgnoreCase(state) || "error".equalsIgnoreCase(state)) {
                    log.warn("任务失败, 轮询次数: {}, 状态: {}", pollCount, state);
                    return status;
                }
            } catch (Exception e) {
                log.warn("解析任务状态响应异常: {}", e.getMessage());
            }

            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("轮询等待被中断");
                return getStatus(id);
            }
        }

        log.warn("轮询超时, 任务ID: {}, 已轮询: {}次", id, pollCount);
        return getStatus(id);
    }

    /**
     * 获取Swagger文档URL
     *
     * @return Swagger UI文档URL
     */
    public String getDocsUrl() {
        return agentProperties.getBaseUrl() + "/docs";
    }

    /**
     * 将JSON字符串解析为JSONObject
     *
     * @param jsonString JSON字符串
     * @return JSONObject对象
     */
    public JSONObject parseJson(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return new JSONObject();
        }
        return JSON.parseObject(jsonString);
    }

    /**
     * 从SSE事件列表中提取完整的响应内容
     * 合并所有data事件中的内容
     *
     * @param events SSE事件列表
     * @return 合并后的完整响应文本
     */
    public String extractResponseFromEvents(List<String> events) {
        StringBuilder result = new StringBuilder();
        for (String event : events) {
            try {
                JSONObject json = JSON.parseObject(event);
                String content = json.getString("content");
                if (content != null) {
                    result.append(content);
                }
            } catch (Exception e) {
                result.append(event);
            }
        }
        return result.toString();
    }
}
