package com.sky.controller.admin;

import com.sky.agent.AgentClient;
import com.sky.agent.model.AgentStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
import jakarta.servlet.http.HttpServletResponse;

import java.util.TreeMap;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent智能体管理接口
 * <p>
 * 提供会话管理、任务提交、SSE事件订阅、消息查询等功能。
 * 这个控制器是智能代理模块的HTTP入口点，负责处理所有与Agent相关的用户交互。
 * </p>
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

    /**
     * 检查底层Agent服务的健康状况。
     *
     * @return 可展示的强类型健康状态，即使 Agent 当前不可达也会返回安全状态。
     */
    @GetMapping("/health")
    @Operation(summary = "Agent服务健康检查")
    public Result<AgentHealthVO> health() {
        JsonNode source = agentClient.healthCheck();
        String status = source.path("status").asText("offline");
        if (!"online".equals(status) && !"degraded".equals(status) && !"offline".equals(status)) {
            status = "offline";
        }
        String errorType = source.path("error_type").asText("");
        if (!errorType.isEmpty() && !errorType.matches("[A-Z_]{1,64}")) {
            errorType = "AGENT_UNAVAILABLE";
        }
        AgentHealthVO health = AgentHealthVO.builder()
                .service(source.path("service").asText("Agent"))
                .status(status)
                .checkedAt(source.path("checked_at").asText(Instant.now().toString()))
                .errorType(errorType.isEmpty() ? null : errorType)
                .build();
        return Result.success(health);
    }

    // ========== 会话管理 CRUD ==========

    /**
     * 分页查询当前用户的会话列表。
     *
     * @param dto 分页和查询参数。
     * @return 分页后的会话列表。
     */
    @GetMapping("/sessions")
    @Operation(summary = "分页查询会话列表")
    public Result<PageResult> listSessions(AgentSessionPageQueryDTO dto) {
        return Result.success(agentService.pageQuerySessions(dto));
    }

    /**
     * 查询指定会话的详细信息，包括完整的消息历史。
     *
     * @param sessionId 会话的唯一标识符。
     * @return 包含会话详情和消息列表的VO。
     */
    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "查询会话详情（含消息历史）")
    public Result<AgentSessionDetailVO> getSessionDetail(
            @Parameter(description = "会话ID") @PathVariable String sessionId) {
        return Result.success(agentService.getSessionDetail(sessionId));
    }

    /**
     * 更新会话信息，如修改标题、归档或逻辑删除。
     *
     * @param dto 包含更新信息的DTO。
     * @return 成功响应。
     */
    @PutMapping("/sessions")
    @Operation(summary = "更新会话（修改标题、归档、删除）")
    public Result updateSession(@Validated @RequestBody AgentSessionUpdateDTO dto) {
        log.info("更新会话: {}", dto);
        agentService.updateSession(dto);
        return Result.success();
    }

    // ========== 任务管理 CRUD ==========

    /**
     * 以"提交并忘记"（submit-and-forget）的模式创建一个新的Agent任务。
     * 此接口会立即返回一个任务ID，客户端可以随后使用此ID来订阅事件或查询结果。
     *
     * @param dto 包含任务输入（如问题、会话ID等）的DTO。
     * @return 包含新创建任务ID的VO。
     */
    @PostMapping("/tasks/submit")
    @Operation(summary = "提交Agent任务")
    public Result<AgentSubmitVO> submitTask(@Validated @RequestBody AgentSubmitDTO dto) {
        // 问题正文属于完整对话内容，不得进入默认日志。
        log.info("提交Agent任务: taskId={}, sessionId={}, kbId={}, model={}",
                dto.getTaskId(), dto.getSessionId(), dto.getKbId(), dto.getModel());
        return Result.success(agentService.submitTask(dto));
    }

    /**
     * 分页查询Agent任务列表。
     *
     * @param dto 分页和查询参数。
     * @return 分页后的任务列表。
     */
    @GetMapping("/tasks")
    @Operation(summary = "分页查询任务列表")
    public Result<PageResult> listTasks(AgentTaskPageQueryDTO dto) {
        return Result.success(agentService.pageQueryTasks(dto));
    }

    /**
     * 查询单个Agent任务的详细信息。
     *
     * @param taskId 任务的唯一标识符。
     * @return 包含任务详情的VO。
     */
    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "查询任务详情")
    public Result<AgentTaskVO> getTaskDetail(
            @Parameter(description = "任务ID") @PathVariable String taskId) {
        return Result.success(agentService.getTaskDetail(taskId));
    }

    /**
     * 请求取消一个正在执行的Agent任务。
     *
     * @param taskId 要取消的任务的唯一标识符。
     * @return 成功响应。
     */
    @PostMapping("/tasks/{taskId}/cancel")
    @Operation(summary = "取消正在执行的任务")
    public Result cancelTask(
            @Parameter(description = "任务ID") @PathVariable String taskId) {
        log.info("取消正在执行的任务: taskId={}", taskId);
        agentService.cancelTask(taskId);
        return Result.success();
    }

    // ========== SSE 事件流 ==========

    /**
     * 订阅指定任务的服务器发送事件（SSE）流。
     * <p>
     * 此端点支持断线重连。客户端可以通过标准的 `Last-Event-ID` HTTP头
     * 或自定义的 `lastSeqNo` 参数来指定上一次接收到的事件序号，
     * 服务器将从此序号之后开始重新发送事件。
     * </p>
     *
     * @param taskId    要订阅的任务的唯一标识符。
     * @param lastSeqNo 客户端最后接收到的事件序号，用于断线续传。
     * @return 一个 {@link SseEmitter} 实例，用于向客户端推送事件。
     */
    @GetMapping("/tasks/{taskId}/events")
    @Operation(summary = "订阅任务SSE事件流")
    public SseEmitter subscribeEvents(
            @Parameter(description = "任务ID") @PathVariable String taskId,
            @Parameter(description = "断线续传起始序号，默认0")
            @RequestHeader(value = "Last-Event-ID", required = false, defaultValue = "0") int lastSeqNo,
            HttpServletResponse response) {

        // 防止 Nginx 缓冲 SSE，否则确认卡片可能在凭证过期后才到达浏览器。
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-transform");

        AgentTaskVO task = agentService.getTaskDetail(taskId);
        int resumeFrom = Math.max(0, lastSeqNo);
        SseEmitter emitter = new SseEmitter(600000L); // 10分钟超时
        // 使用一个自定义的连接处理器来保证事件按顺序发送
        OrderedSseConnection connection = new OrderedSseConnection(emitter, resumeFrom);
        AtomicReference<AutoCloseable> registration = new AtomicReference<>();

        // 定义清理逻辑，用于在连接关闭时取消订阅
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

        // 1. 订阅实时事件
        registration.set(eventHub.subscribe(taskId, connection::accept));
        try {
            // 2. 回放历史事件（用于断线重连）
            for (AgentEvent stored : agentService.getEventsAfterSeqNo(taskId, resumeFrom)) {
                connection.accept(toStreamEvent(stored));
            }
            // 3. 如果任务已经终结，但连接尚未关闭，则主动关闭
            if (task.getStatus() != null && task.getStatus() >= 2 && !connection.isCompleted()) {
                emitter.complete();
            }
        } catch (Exception exception) {
            cleanup.run();
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    /**
     * 将数据库中存储的 {@link AgentEvent} 转换为用于SSE流的 {@link AgentStreamEvent}。
     */
    private AgentStreamEvent toStreamEvent(AgentEvent event) {
        try {
            return new AgentStreamEvent(event.getTaskId(), event.getSeqNo(),
                    event.getEventType(), objectMapper.readTree(event.getData()));
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取已持久化的Agent事件", exception);
        }
    }

    /**
     * 安全地关闭事件中心的订阅注册。
     */
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

    /**
     * 一个内部类，用于处理SSE事件的有序发送。
     * <p>
     * 它结合了历史事件回放和实时事件流，确保所有事件都严格按照其序号（seqNo）
     * 发送给客户端，即使事件到达的顺序是混乱的。
     * </p>
     */
    private static final class OrderedSseConnection {
        private final SseEmitter emitter;
        // 使用TreeMap作为缓冲区，按事件序号自动排序
        private final TreeMap<Integer, AgentStreamEvent> pending = new TreeMap<>();
        private int lastSent; // 已发送的最后一个事件的序号
        private boolean completed;

        private OrderedSseConnection(SseEmitter emitter, int lastSent) {
            this.emitter = emitter;
            this.lastSent = lastSent;
        }

        /**
         * 接收一个新事件（无论是来自历史记录还是实时流）。
         */
        private synchronized void accept(AgentStreamEvent event) {
            if (completed || event.seqNo() <= lastSent) {
                return; // 忽略已发送或过时的事件
            }
            pending.putIfAbsent(event.seqNo(), event);
            try {
                AgentStreamEvent next;
                // 循环检查并发送所有连续的事件
                while ((next = pending.remove(lastSent + 1)) != null) {
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(next.seqNo())) // 设置SSE的 'id' 字段，用于断线重连
                            .name(next.event()) // 设置SSE的 'event' 字段
                            .data(next));
                    lastSent = next.seqNo();
                    // 如果是任务终结事件，则关闭连接
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

        /**
         * 检查事件类型是否表示任务已终结。
         */
        private static boolean isTerminal(String eventType) {
            return "task_end".equals(eventType)
                    || "task_error".equals(eventType)
                    || "task_cancelled".equals(eventType);
        }
    }
}
