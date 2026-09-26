package com.sky.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.agent.model.AgentStreamEvent;
import com.sky.entity.AgentEvent;
import com.sky.service.UserAgentService;
import com.sky.vo.AgentTaskVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/** 用户端SSE回放服务，只读取user_agent_event并校验用户任务归属。 */
@Service
@RequiredArgsConstructor
public class UserAgentSseService {
    private final UserAgentService agentService;
    private final AgentEventHub eventHub;
    private final ObjectMapper objectMapper;

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
            throw new IllegalStateException("无法读取用户Agent事件", exception);
        }
    }

    private void close(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); } catch (Exception ignored) { }
    }

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
                    if (terminal(next.event())) {
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

        private boolean terminal(String type) {
            return type != null && (type.equals("task_end") || type.equals("task_error")
                    || type.equals("task_cancelled") || type.equals("task_completed")
                    || type.equals("task_failed"));
        }
    }
}
