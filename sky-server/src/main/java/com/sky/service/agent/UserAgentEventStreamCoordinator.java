package com.sky.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.agent.AgentClient;
import com.sky.agent.model.AgentStreamEvent;
import com.sky.entity.AgentTask;
import com.sky.mapper.user.UserAgentEventMapper;
import com.sky.mapper.user.UserAgentTaskMapper;
import com.sky.properties.AgentProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 用户端上游SSE协调器，只恢复和落库user_agent_task。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserAgentEventStreamCoordinator implements ApplicationRunner {
    private static final int MAX_ATTEMPTS = 3;
    private final AgentClient agentClient;
    private final UserAgentEventMapper eventMapper;
    private final UserAgentTaskMapper taskMapper;
    private final UserAgentEventProcessor eventProcessor;
    private final AgentEventHub eventHub;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Future<?>> upstream = new ConcurrentHashMap<>();

    public void start(String taskId) {
        upstream.computeIfAbsent(taskId, ignored -> executor.submit(() -> consume(taskId)));
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.isRecoveryEnabled()) {
            taskMapper.listUnfinished().forEach(task -> start(task.getTaskId()));
        }
    }

    private void consume(String taskId) {
        try {
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                AtomicBoolean terminal = new AtomicBoolean(false);
                try {
                    int lastSeq = eventMapper.getMaxSeqNo(taskId);
                    agentClient.subscribeTaskEvents(
                            taskId, lastSeq, "USER_ASSISTANT", event -> {
                        if (!taskId.equals(event.taskId())) throw new IllegalArgumentException("task_id不匹配");
                        if (eventProcessor.process(event)) eventHub.publish(event);
                        terminal.set(isTerminal(event.event()));
                    });
                    AgentTask current = taskMapper.getByTaskId(taskId);
                    if (terminal.get() || current == null || current.getStatus() >= 2) return;
                } catch (Exception exception) {
                    log.warn("用户Agent上游事件流中断, taskId={}, attempt={}", taskId, attempt, exception);
                }
                if (attempt < MAX_ATTEMPTS) {
                    try { Thread.sleep(1000L * attempt); }
                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); return; }
                }
            }
            ObjectNode data = objectMapper.createObjectNode();
            data.put("status", "failed");
            data.put("error_msg", "无法恢复Python用户Agent事件流");
            AgentStreamEvent failure = new AgentStreamEvent(
                    taskId, eventMapper.getMaxSeqNo(taskId) + 1, "task_failed", data);
            if (eventProcessor.process(failure)) eventHub.publish(failure);
        } finally {
            upstream.remove(taskId);
        }
    }

    private boolean isTerminal(String type) {
        return type != null && (type.equals("task_end") || type.equals("task_error")
                || type.equals("task_completed") || type.equals("task_failed")
                || type.equals("task_cancelled"));
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
