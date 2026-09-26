package com.sky.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.agent.model.AgentStreamEvent;
import com.sky.entity.AgentEvent;
import com.sky.entity.AgentMessage;
import com.sky.entity.AgentSession;
import com.sky.entity.AgentTask;
import com.sky.mapper.user.UserAgentEventMapper;
import com.sky.mapper.user.UserAgentMessageMapper;
import com.sky.mapper.user.UserAgentSessionMapper;
import com.sky.mapper.user.UserAgentTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 用户端事件处理器，所有持久化严格限定在user_agent_*表族。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAgentEventProcessor {
    private final UserAgentEventMapper eventMapper;
    private final UserAgentTaskMapper taskMapper;
    private final UserAgentMessageMapper messageMapper;
    private final UserAgentSessionMapper sessionMapper;
    private final UserAgentMessageCacheService cacheService;
    private final ObjectMapper objectMapper;

    @Transactional
    public boolean process(AgentStreamEvent event) {
        AgentTask task = taskMapper.getByTaskId(event.taskId());
        if (task == null || !compatible(task.getStatus(), event.event())) return false;
        AgentEvent stored = AgentEvent.builder()
                .eventId(event.taskId() + ":" + event.seqNo())
                .taskId(event.taskId()).seqNo(event.seqNo()).eventType(event.event())
                .data(writeJson(event.data())).build();
        if (eventMapper.insertIfAbsent(stored) == 0) return false;
        return switch (event.event()) {
            case "task_start", "task_started" -> start(task);
            case "token", "message_delta", "workflow_routed", "recommendation_cards",
                 "tool_started", "tool_completed", "business_state_changed",
                 "confirmation_required" -> accept(task);
            case "task_end", "task_completed" -> complete(task, event.data());
            case "task_error", "task_failed" -> fail(task, event.data());
            case "task_cancelled" -> cancel(task);
            default -> true;
        };
    }

    private boolean start(AgentTask task) {
        return task.getStatus() == 1 || taskMapper.transitionStatus(
                task.getTaskId(), 1, 1, null, null, List.of(0)) == 1;
    }

    private boolean accept(AgentTask task) {
        return task.getStatus() != 0 || start(task);
    }

    private boolean complete(AgentTask task, JsonNode data) {
        String messageId = UUID.randomUUID().toString().replace("-", "");
        if (taskMapper.transitionStatus(task.getTaskId(), 2, 100, messageId,
                null, List.of(0, 1)) == 0) return false;
        AgentSession session = sessionMapper.getBySessionIdForUpdate(task.getSessionId());
        AgentMessage message = AgentMessage.builder()
                .messageId(messageId).sessionId(task.getSessionId()).taskId(task.getTaskId())
                .role(2).content(text(data, "result", "")).contentType("markdown")
                .seqNo(messageMapper.getNextSeqNo(task.getSessionId())).build();
        if (messageMapper.insertIfAbsent(message) == 1 && session != null) {
            sessionMapper.incrementMessageCount(session.getId(), task.getTaskId());
            cacheService.evict(session.getId());
        }
        return true;
    }

    private boolean fail(AgentTask task, JsonNode data) {
        return taskMapper.transitionStatus(task.getTaskId(), 3, task.getProgress(), null,
                text(data, "error_msg", "Agent执行失败"), List.of(0, 1)) == 1;
    }

    private boolean cancel(AgentTask task) {
        return task.getStatus() == 4 || taskMapper.transitionStatus(
                task.getTaskId(), 4, task.getProgress(), null, null, List.of(0, 1)) == 1;
    }

    private boolean compatible(Integer status, String eventType) {
        if (status == null || status == 2 || status == 3) return false;
        return status != 4 || !(eventType.startsWith("task_end")
                || "task_completed".equals(eventType) || "task_failed".equals(eventType));
    }

    private String writeJson(JsonNode data) {
        try { return objectMapper.writeValueAsString(data == null ? objectMapper.nullNode() : data); }
        catch (Exception exception) { throw new IllegalArgumentException("无法序列化用户Agent事件", exception); }
    }

    private String text(JsonNode data, String field, String fallback) {
        return data != null && data.hasNonNull(field) ? data.get(field).asText(fallback) : fallback;
    }
}
