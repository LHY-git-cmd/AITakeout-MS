package com.sky.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.agent.model.AgentStreamEvent;
import com.sky.entity.AgentEvent;
import com.sky.service.AdminAgentService;
import com.sky.vo.AgentTaskVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/** 为管理端和用户端提供统一的Agent SSE回放及实时订阅。 */
@Service
@RequiredArgsConstructor
public class AgentSseService {
    private final AdminAgentService agentService;
    private final AgentEventHub eventHub;
    private final ObjectMapper objectMapper;

    /** 创建已经过当前主体所有权校验的SSE连接。 */
    public SseEmitter subscribe(String taskId, int lastSeqNo, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        AgentTaskVO task = agentService.getTaskDetail(taskId);
        OrderedConnection connection = new OrderedConnection(new SseEmitter(600000L), Math.max(0, lastSeqNo));
        AtomicReference<AutoCloseable> registration = new AtomicReference<>();
        Runnable cleanup = () -> close(registration.getAndSet(null));
        connection.emitter.onCompletion(cleanup);
        connection.emitter.onTimeout(() -> { cleanup.run(); connection.emitter.complete(); });
        connection.emitter.onError(ignored -> cleanup.run());
        registration.set(eventHub.subscribe(taskId, connection::accept));
        try {
            for (AgentEvent stored : agentService.getEventsAfterSeqNo(taskId, Math.max(0, lastSeqNo))) {
                connection.accept(toStreamEvent(stored));
            }
            if (task.getStatus() != null && task.getStatus() >= 2 && !connection.completed) {
                connection.emitter.complete();
            }
        } catch (Exception exception) {
            cleanup.run();
            connection.emitter.completeWithError(exception);
        }
        return connection.emitter;
    }

    private AgentStreamEvent toStreamEvent(AgentEvent event) {
        try {
            return new AgentStreamEvent(event.getTaskId(), event.getSeqNo(),
                    event.getEventType(), objectMapper.readTree(event.getData()));
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取已持久化的Agent事件", exception);
        }
    }

    private void close(AutoCloseable registration) {
        if (registration == null) return;
        try { registration.close(); } catch (Exception ignored) { }
    }

    /** 合并历史事件与实时事件，确保客户端只按连续序号接收一次。 */
    private static final class OrderedConnection {
        private final SseEmitter emitter;
        private final TreeMap<Integer, AgentStreamEvent> pending = new TreeMap<>();
        private int lastSent;
        private boolean completed;

        private OrderedConnection(SseEmitter emitter, int lastSent) {
            this.emitter = emitter;
            this.lastSent = lastSent;
        }

        private synchronized void accept(AgentStreamEvent event) {
            if (completed || event.seqNo() <= lastSent) return;
            pending.putIfAbsent(event.seqNo(), event);
            try {
                AgentStreamEvent next;
                while ((next = pending.remove(lastSent + 1)) != null) {
                    emitter.send(SseEmitter.event().id(String.valueOf(next.seqNo()))
                            .name(next.event()).data(next));
                    lastSent = next.seqNo();
                    if (isTerminal(next.event())) {
                        completed = true;
                        emitter.complete();
                        return;
                    }
                }
            } catch (Exception exception) {
                completed = true;
                emitter.completeWithError(exception);
            }
        }

        private static boolean isTerminal(String type) {
            return "task_end".equals(type) || "task_error".equals(type)
                    || "task_cancelled".equals(type) || "task_completed".equals(type)
                    || "task_failed".equals(type);
        }
    }
}
