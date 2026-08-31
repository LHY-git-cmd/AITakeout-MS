package com.sky.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.agent.model.AgentStreamEvent;
import com.sky.entity.AgentEvent;
import com.sky.entity.AgentMessage;
import com.sky.entity.AgentSession;
import com.sky.entity.AgentTask;
import com.sky.mapper.AgentEventMapper;
import com.sky.mapper.AgentMessageMapper;
import com.sky.mapper.AgentSessionMapper;
import com.sky.mapper.AgentTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 将Python事件幂等落库，并推进Java权威业务状态。 */
@Service
@RequiredArgsConstructor
public class AgentEventProcessor {

    private final AgentEventMapper eventMapper;
    private final AgentTaskMapper taskMapper;
    private final AgentMessageMapper messageMapper;
    private final AgentSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    /**
     * 处理一条来自Python Agent的流式事件：幂等落库 + 推进任务状态机
     *
     * @param event Python Agent推送的流式事件
     * @return true-事件处理成功；false-事件被跳过（已处理或状态不兼容）
     */
    @Transactional
    public boolean process(AgentStreamEvent event) {
        // 1. 校验任务存在且状态兼容
        AgentTask task = taskMapper.getByTaskId(event.taskId());
        if (task == null || !isCompatible(task.getStatus(), event.event())) {
            return false;
        }

        // 2. 幂等写入事件日志
        AgentEvent stored = AgentEvent.builder()
                .eventId(event.taskId() + ":" + event.seqNo())
                .taskId(event.taskId())
                .seqNo(event.seqNo())
                .eventType(event.event())
                .data(writeJson(event.data()))
                .build();
        if (eventMapper.insertIfAbsent(stored) == 0) {
            return false;
        }

        // 3. 根据事件类型推进业务状态
        return switch (event.event()) {
            case "task_start" -> startTask(task);
            case "token" -> acceptToken(task);
            case "task_end" -> completeTask(task, event.data());
            case "task_error" -> failTask(task, event.data());
            case "task_cancelled" -> cancelTask(task);
            default -> true;
        };
    }

    // ---- 任务状态机转换 ----

    /** 任务开始：状态 0(等待) → 1(运行中) */
    private boolean startTask(AgentTask task) {
        if (task.getStatus() == 1 || task.getStatus() == 4) {
            return true;
        }
        return taskMapper.transitionStatus(task.getTaskId(), 1, 1, null, null,
                List.of(0)) == 1;
    }

    /** 收到首个token：视为任务已启动，状态 0 → 1 */
    private boolean acceptToken(AgentTask task) {
        if (task.getStatus() == 0) {
            return taskMapper.transitionStatus(task.getTaskId(), 1, 1, null, null,
                    List.of(0)) == 1;
        }
        return task.getStatus() == 1 || task.getStatus() == 4;
    }

    /** 任务完成：状态 1 → 2(已完成)，同时写入assistant消息 */
    private boolean completeTask(AgentTask task, JsonNode data) {
        String messageId = UUID.randomUUID().toString().replace("-", "");
        int transitioned = taskMapper.transitionStatus(task.getTaskId(), 2, 100,
                messageId, null, List.of(0, 1));
        if (transitioned == 0) {
            return false;
        }

        // 写入assistant回复消息并更新会话消息计数
        AgentSession session = sessionMapper.getBySessionIdForUpdate(task.getSessionId());
        AgentMessage assistantMessage = AgentMessage.builder()
                .messageId(messageId)
                .sessionId(task.getSessionId())
                .taskId(task.getTaskId())
                .role(2)
                .content(text(data, "result", ""))
                .contentType("markdown")
                .seqNo(messageMapper.getNextSeqNo(task.getSessionId()))
                .build();
        if (messageMapper.insertIfAbsent(assistantMessage) == 1) {
            if (session != null) {
                sessionMapper.incrementMessageCount(session.getId(), task.getTaskId());
            }
        }
        return true;
    }

    /** 任务失败：状态 → 3(失败)，记录错误信息 */
    private boolean failTask(AgentTask task, JsonNode data) {
        return taskMapper.transitionStatus(task.getTaskId(), 3, task.getProgress(), null,
                text(data, "error_msg", "Agent执行失败"), List.of(0, 1)) == 1;
    }

    /** 任务取消：状态 → 4(已取消)，已取消则直接返回 */
    private boolean cancelTask(AgentTask task) {
        if (task.getStatus() == 4) {
            return true;
        }
        return taskMapper.transitionStatus(task.getTaskId(), 4, task.getProgress(), null,
                null, List.of(0, 1)) == 1;
    }

    // ---- 辅助方法 ----

    /** 判断事件类型与当前任务状态是否兼容（终态不再接受非取消事件） */
    private boolean isCompatible(Integer status, String eventType) {
        if (status == null) {
            return false;
        }
        // 已完成或已失败：不再接受任何事件
        if (status == 2 || status == 3) {
            return false;
        }
        // 已取消：不再接受终态事件
        if (status == 4) {
            return !"task_end".equals(eventType) && !"task_error".equals(eventType);
        }
        return true;
    }

    /** 将JsonNode序列化为字符串，null时写入JSON null */
    private String writeJson(JsonNode data) {
        try {
            return objectMapper.writeValueAsString(data == null ? objectMapper.nullNode() : data);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法序列化Agent事件数据", exception);
        }
    }

    /** 从事件data中提取指定字段的文本值，缺失时返回默认值 */
    private String text(JsonNode data, String field, String defaultValue) {
        if (data == null || !data.hasNonNull(field)) {
            return defaultValue;
        }
        return data.get(field).asText(defaultValue);
    }
}