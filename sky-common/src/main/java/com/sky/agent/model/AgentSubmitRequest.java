package com.sky.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Agent任务提交请求
 *
 * @param taskId      任务ID
 * @param sessionId   会话ID
 * @param actorId     任务主体ID
 * @param query       查询内容
 * @param model       模型
 * @param temperature 温度
 * @param context     上下文
 * @param knowledge   知识库
 * @param traceId     跨服务调用追踪ID
 * @param actorType   任务主体类型
 * @param actorRole   Java根据数据库确定的角色快照
 */
public record AgentSubmitRequest(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("actor_id") Long actorId,
        String query,
        String model,
        Double temperature,
        Map<String, Object> context,
        AgentKnowledgeScope knowledge,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("actor_type") String actorType,
        @JsonProperty("actor_role") String actorRole,
        @JsonProperty("agent_profile") String agentProfile) {

    /** 新协议构造器：根据可信主体显式选择逻辑Agent。 */
    public AgentSubmitRequest(String taskId, String sessionId, Long actorId, String query,
                              String model, Double temperature, Map<String, Object> context,
                              AgentKnowledgeScope knowledge, String traceId, String actorType,
                              String actorRole) {
        this(taskId, sessionId, actorId, query, model, temperature, context, knowledge,
                traceId, actorType, actorRole,
                "USER".equals(actorType) ? "USER_ASSISTANT" : "ADMIN_ASSISTANT");
    }

    public AgentSubmitRequest(String taskId, String sessionId, Long userId, String query,
                              String model, Double temperature,
                              Map<String, Object> context,
                              AgentKnowledgeScope knowledge) {
        this(taskId, sessionId, userId, query, model, temperature, context, knowledge,
                null, "ADMIN", "ADMIN", "ADMIN_ASSISTANT");
    }

    public AgentSubmitRequest(String taskId, String sessionId, Long userId, String query,
                              String model, Double temperature,
                              Map<String, Object> context) {
        this(taskId, sessionId, userId, query, model, temperature, context, null,
                null, "ADMIN", "ADMIN", "ADMIN_ASSISTANT");
    }

    public AgentSubmitRequest(String taskId, String sessionId, Long userId, String query,
                              String model, Double temperature,
                              Map<String, Object> context,
                              AgentKnowledgeScope knowledge, String traceId) {
        this(taskId, sessionId, userId, query, model, temperature, context, knowledge,
                traceId, "ADMIN", "ADMIN", "ADMIN_ASSISTANT");
    }

    /** 保留既有管理端调用签名，并自动补充主体类型。 */
    public AgentSubmitRequest(String taskId, String sessionId, Long actorId, String query,
                              String model, Double temperature,
                              Map<String, Object> context,
                              AgentKnowledgeScope knowledge, String traceId, String actorRole) {
        this(taskId, sessionId, actorId, query, model, temperature, context, knowledge,
                traceId, "ADMIN", actorRole, "ADMIN_ASSISTANT");
    }
}
