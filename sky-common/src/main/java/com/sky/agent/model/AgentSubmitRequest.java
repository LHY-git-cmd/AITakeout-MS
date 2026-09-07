package com.sky.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Agent任务提交请求
 *
 * @param taskId      任务ID
 * @param sessionId   会话ID
 * @param userId      用户ID
 * @param query       查询内容
 * @param model       模型
 * @param temperature 温度
 * @param context     上下文
 * @param knowledge   知识库
 */
public record AgentSubmitRequest(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("user_id") Long userId,
        String query,
        String model,
        Double temperature,
        Map<String, List<AgentHistoryMessage>> context,
        AgentKnowledgeScope knowledge) {

    public AgentSubmitRequest(String taskId, String sessionId, Long userId, String query,
                              String model, Double temperature,
                              Map<String, List<AgentHistoryMessage>> context) {
        this(taskId, sessionId, userId, query, model, temperature, context, null);
    }
}