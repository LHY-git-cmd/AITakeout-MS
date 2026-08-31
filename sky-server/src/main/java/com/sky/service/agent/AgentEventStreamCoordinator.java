package com.sky.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.agent.AgentClient;
import com.sky.agent.model.AgentStreamEvent;
import com.sky.entity.AgentTask;
import com.sky.mapper.AgentEventMapper;
import com.sky.mapper.AgentTaskMapper;
import com.sky.properties.AgentProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** 确保每个任务只有一条Java到Python的上游SSE连接。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentEventStreamCoordinator implements ApplicationRunner {

    private static final int MAX_ATTEMPTS = 3;
    private final AgentClient agentClient;
    private final AgentEventMapper eventMapper;
    private final AgentTaskMapper taskMapper;
    private final AgentEventProcessor eventProcessor;
    private final AgentEventHub eventHub;
    private final ObjectMapper objectMapper;
    private final AgentProperties agentProperties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Future<?>> upstreamTasks = new ConcurrentHashMap<>();

    /**
     * 启动指定任务的上游SSE消费（幂等：已运行则不重复启动）
     */
    public void start(String taskId) {
        upstreamTasks.computeIfAbsent(taskId,
                ignored -> executor.submit(() -> consume(taskId)));
    }

    /** 查询任务的上游事件流是否仍在运行 */
    public boolean isRunning(String taskId) {
        Future<?> future = upstreamTasks.get(taskId);
        return future != null && !future.isDone();
    }

    /**
     * 应用启动后恢复所有未完成任务的事件流订阅，实现Java端重启后的断点续接
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!agentProperties.isRecoveryEnabled()) {
            return;
        }
        taskMapper.listUnfinished().forEach(task -> start(task.getTaskId()));
    }

    /**
     * 消费单个任务的上游SSE事件流，支持断线重试（最多MAX_ATTEMPTS次）
     * 流程：拉取已落库的最大seqNo → 从该位置续接 → 处理→扇出 → 遇终态或异常则重试
     */
    private void consume(String taskId) {
        try {
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                AtomicBoolean terminalSeen = new AtomicBoolean(false);
                try {
                    // 从已落库的最大seqNo续接，避免重复消费
                    int lastSeqNo = eventMapper.getMaxSeqNo(taskId);
                    agentClient.subscribeTaskEvents(taskId, lastSeqNo, event -> {
                        if (!taskId.equals(event.taskId())) {
                            throw new IllegalArgumentException("上游事件task_id不匹配");
                        }
                        if (eventProcessor.process(event)) {
                            eventHub.publish(event);
                        }
                        terminalSeen.set(isTerminal(event.event()));
                    });
                    AgentTask current = taskMapper.getByTaskId(taskId);
                    // 收到终态事件或任务已结束，结束消费
                    if (terminalSeen.get() || current == null || current.getStatus() >= 2) {
                        return;
                    }
                } catch (Exception exception) {
                    log.warn("Agent上游事件流中断, taskId={}, attempt={}",
                            taskId, attempt, exception);
                }
                // 指数退避重试
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            // 超过最大重试次数，主动推送失败事件
            publishStreamFailure(taskId);
        } finally {
            upstreamTasks.remove(taskId);
        }
    }

    /** 超过最大重试次数后，构造一条task_error事件落库并扇出，通知前端 */
    private void publishStreamFailure(String taskId) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("status", "failed");
        data.put("error_msg", "无法恢复Python Agent事件流");
        AgentStreamEvent event = new AgentStreamEvent(
                taskId,
                eventMapper.getMaxSeqNo(taskId) + 1,
                "task_error",
                data);
        if (eventProcessor.process(event)) {
            eventHub.publish(event);
        }
    }

    /** 判断事件类型是否为任务终态（结束/失败/取消） */
    private boolean isTerminal(String eventType) {
        return "task_end".equals(eventType)
                || "task_error".equals(eventType)
                || "task_cancelled".equals(eventType);
    }

    /** 应用关闭时优雅终止所有虚拟线程 */
    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}