package com.sky.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sky.properties.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import java.util.function.Consumer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
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

    private final RestTemplate restTemplate;
    private final AgentProperties agentProperties;

    public AgentClient(RestTemplateBuilder restTemplateBuilder, AgentProperties agentProperties) {
        this.agentProperties = agentProperties;
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

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(params, headers);
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

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(params, headers);
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