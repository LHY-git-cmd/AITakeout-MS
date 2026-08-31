package com.sky.controller.admin;

import com.sky.agent.AgentClient;
import com.sky.agent.model.AgentStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.dto.*;
import com.sky.entity.AgentEvent;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.AgentService;
import com.sky.service.agent.AgentEventHub;
import com.sky.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent智能体管理接口
 * 提供会话管理、任务提交、SSE事件订阅、消息查询等功能
 */
@Slf4j
@RestController
@RequestMapping("/admin/agent")
@RequiredArgsConstructor
@Tag(name = "Agent智能体接口")
public class AgentController {

    private final AgentService agentService;
    private final AgentClient agentClient;
    private final AgentEventHub eventHub;
    private final ObjectMapper objectMapper;

    // ========== 基础健康检查 ==========

    @GetMapping("/health")
    @Operation(summary = "Agent服务健康检查")
    public Result<String> health() {
        return Result.success(agentClient.healthCheck());
    }

    // ========== 会话管理 CRUD ==========

    /**
     * 分页查询当前用户的会话列表
     */
    @GetMapping("/sessions")
    @Operation(summary = "分页查询会话列表")
    public Result<PageResult> listSessions(AgentSessionPageQueryDTO dto) {
        return Result.success(agentService.pageQuerySessions(dto));
    }

    /**
     * 查询会话详情（含消息历史）
     */
    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "查询会话详情（含消息历史）")
    public Result<AgentSessionDetailVO> getSessionDetail(
            @Parameter(description = "会话ID") @PathVariable String sessionId) {
        return Result.success(agentService.getSessionDetail(sessionId));
    }

    /**
     * 更新会话（修改标题、归档、删除）
     */
    //todo(目前只实现归档功能，无法修改标题和删除，归档后只能在数据库查看)
    @PutMapping("/sessions")
    @Operation(summary = "更新会话（修改标题、归档、删除）")
    public Result updateSession(@Validated @RequestBody AgentSessionUpdateDTO dto) {
        agentService.updateSession(dto);
        return Result.success();
    }

    // ========== 任务管理 CRUD ==========

    /**
     * 提交任务（submit模式，立即返回taskId）
     */
    @PostMapping("/tasks/submit")
    @Operation(summary = "提交Agent任务")
    public Result<AgentSubmitVO> submitTask(@Validated @RequestBody AgentSubmitDTO dto) {
        return Result.success(agentService.submitTask(dto));
    }

    /**
     * 分页查询任务列表
     */
    @GetMapping("/tasks")
    @Operation(summary = "分页查询任务列表")
    public Result<PageResult> listTasks(AgentTaskPageQueryDTO dto) {
        return Result.success(agentService.pageQueryTasks(dto));
    }

    /**
     * 查询单个任务详情
     */
    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "查询任务详情")
    public Result<AgentTaskVO> getTaskDetail(
            @Parameter(description = "任务ID") @PathVariable String taskId) {
        return Result.success(agentService.getTaskDetail(taskId));
    }

    /**
     * 取消正在执行的任务
     */
    @PostMapping("/tasks/{taskId}/cancel")
    @Operation(summary = "取消正在执行的任务")
    public Result cancelTask(
            @Parameter(description = "任务ID") @PathVariable String taskId) {
        agentService.cancelTask(taskId);
        return Result.success();
    }

    // ========== SSE 事件流 ==========

    /**
     * 订阅任务的SSE事件流（断线恢复支持）
     * 前端可通过 Last-Event-ID 头或 lastSeqNo 参数指定起始位置
     */
    @GetMapping("/tasks/{taskId}/events")
    @Operation(summary = "订阅任务SSE事件流")
    public SseEmitter subscribeEvents(
            @Parameter(description = "任务ID") @PathVariable String taskId,
            @Parameter(description = "断线续传起始序号，默认0")
            @RequestHeader(value = "Last-Event-ID", required = false, defaultValue = "0") int lastSeqNo) {

        AgentTaskVO task = agentService.getTaskDetail(taskId);
        int resumeFrom = Math.max(0, lastSeqNo);
        SseEmitter emitter = new SseEmitter(600000L);
        OrderedSseConnection connection = new OrderedSseConnection(emitter, resumeFrom);
        AtomicReference<AutoCloseable> registration = new AtomicReference<>();

        Runnable cleanup = () -> closeRegistration(registration.getAndSet(null));
        emitter.onCompletion(() -> {
            cleanup.run();
            log.info("SSE订阅完成, taskId={}", taskId);
        });
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(error -> {
            cleanup.run();
            log.debug("SSE客户端连接结束, taskId={}", taskId, error);
        });

        registration.set(eventHub.subscribe(taskId, connection::accept));
        try {
            for (AgentEvent stored : agentService.getEventsAfterSeqNo(taskId, resumeFrom)) {
                connection.accept(toStreamEvent(stored));
            }
            if (task.getStatus() != null && task.getStatus() >= 2 && !connection.isCompleted()) {
                emitter.complete();
            }
        } catch (Exception exception) {
            cleanup.run();
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    private AgentStreamEvent toStreamEvent(AgentEvent event) {
        try {
            return new AgentStreamEvent(event.getTaskId(), event.getSeqNo(),
                    event.getEventType(), objectMapper.readTree(event.getData()));
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取已持久化的Agent事件", exception);
        }
    }

    private void closeRegistration(AutoCloseable registration) {
        if (registration == null) {
            return;
        }
        try {
            registration.close();
        } catch (Exception exception) {
            log.debug("关闭Agent SSE订阅失败", exception);
        }
    }

    /** 合并历史回放和实时事件，并严格按seqNo发送。 */
    private static final class OrderedSseConnection {
        private final SseEmitter emitter;
        private final TreeMap<Integer, AgentStreamEvent> pending = new TreeMap<>();
        private int lastSent;
        private boolean completed;

        private OrderedSseConnection(SseEmitter emitter, int lastSent) {
            this.emitter = emitter;
            this.lastSent = lastSent;
        }

        private synchronized void accept(AgentStreamEvent event) {
            if (completed || event.seqNo() <= lastSent) {
                return;
            }
            pending.putIfAbsent(event.seqNo(), event);
            try {
                AgentStreamEvent next;
                while ((next = pending.remove(lastSent + 1)) != null) {
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(next.seqNo()))
                            .name(next.event())
                            .data(next));
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

        private synchronized boolean isCompleted() {
            return completed;
        }

        private static boolean isTerminal(String eventType) {
            return "task_end".equals(eventType)
                    || "task_error".equals(eventType)
                    || "task_cancelled".equals(eventType);
        }
    }
}
