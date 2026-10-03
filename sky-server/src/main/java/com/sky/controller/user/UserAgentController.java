package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.dto.AgentSessionPageQueryDTO;
import com.sky.dto.AgentSubmitDTO;
import com.sky.dto.UserAgentSessionCreateDTO;
import com.sky.dto.UserAgentSubmitDTO;
import com.sky.exception.AgentBusinessException;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.UserAgentService;
import com.sky.service.AgentToolOperationService;
import com.sky.service.agent.UserAgentSseService;
import com.sky.vo.AgentSessionDetailVO;
import com.sky.vo.AgentSessionVO;
import com.sky.vo.AgentSubmitVO;
import com.sky.vo.AgentTaskVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/** 普通用户Agent网关：主体身份只从用户JWT上下文获取。 */
@Validated
@RestController
@RequestMapping("/user/agent")
@RequiredArgsConstructor
public class UserAgentController {
    private final UserAgentService agentService;
    private final AgentToolOperationService operationService;
    private final UserAgentSseService sseService;

    /**
     * 创建新的Agent对话会话
     *
     * @param body 可选的会话创建参数（如标题）
     * @return 会话信息（含会话ID）
     */
    @PostMapping("/sessions")
    public Result<AgentSessionVO> createSession(
            @Valid @RequestBody(required = false) UserAgentSessionCreateDTO body) {
        return Result.success(agentService.createSession(body == null ? null : body.getTitle()));
    }

    /**
     * 分页查询当前用户的Agent会话列表
     *
     * @param query 分页查询参数
     * @return 分页会话列表
     */
    @GetMapping("/sessions")
    public Result<PageResult> listSessions(@Valid AgentSessionPageQueryDTO query) {
        return Result.success(agentService.pageQuerySessions(query));
    }

    /**
     * 查询指定会话的详细信息（含历史消息等）
     *
     * @param sessionId 会话ID
     * @return 会话详情
     */
    @GetMapping("/sessions/{sessionId}")
    public Result<AgentSessionDetailVO> session(@PathVariable String sessionId) {
        return Result.success(agentService.getSessionDetail(sessionId));
    }

    /**
     * 向Agent提交用户消息，触发任务执行，支持幂等性防重复提交
     *
     * @param idempotencyKey 幂等性键（格式校验：8-64位字母数字下划线短横线）
     * @param body           用户提交的消息体（含会话ID和消息内容）
     * @return 任务提交结果（含任务ID）
     */
    @PostMapping("/tasks")
    public Result<AgentSubmitVO> submit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody UserAgentSubmitDTO body) {
        String taskId = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (!taskId.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new AgentBusinessException("Idempotency-Key格式不正确");
        }
        AgentSubmitDTO request = new AgentSubmitDTO();
        request.setTaskId(taskId);
        request.setSessionId(body.getSessionId());
        request.setQuery(body.getMessage().trim());
        request.setClientContext(body.getClientContext());
        return Result.success(agentService.submitTask(request));
    }

    /**
     * 查询指定Agent任务的执行状态和结果
     *
     * @param taskId 任务ID
     * @return 任务详情（状态、执行进度、结果等）
     */
    @GetMapping("/tasks/{taskId}")
    public Result<AgentTaskVO> task(@PathVariable String taskId) {
        return Result.success(agentService.getTaskDetail(taskId));
    }

    /**
     * 取消正在执行中的Agent任务
     *
     * @param taskId 任务ID
     * @return 操作结果
     */
    @PostMapping("/tasks/{taskId}/cancel")
    public Result<Void> cancel(@PathVariable String taskId) {
        agentService.cancelTask(taskId);
        return Result.success();
    }

    /**
     * 通过SSE（Server-Sent Events）订阅Agent任务的实时事件流，支持断点续连
     *
     * @param taskId    任务ID
     * @param lastSeqNo 客户端上次接收的最后一条事件序列号，用于断点续连（默认0）
     * @param response  HTTP响应对象，用于建立SSE长连接
     * @return SSE发射器，持续推送任务执行过程中的各类事件
     */
    @GetMapping("/tasks/{taskId}/events")
    public SseEmitter events(
            @PathVariable String taskId,
            @RequestHeader(value = "Last-Event-ID", required = false, defaultValue = "0") int lastSeqNo,
            HttpServletResponse response) {
        return sseService.subscribe(taskId, lastSeqNo, response);
    }

    /**
     * 用户批准Agent工具操作确认单，允许Agent执行高风险操作
     *
     * @param confirmationId 确认单ID
     * @return 审批结果
     */
    @PostMapping("/confirmations/{confirmationId}/approve")
    public Result<Map<String, Object>> approve(@PathVariable String confirmationId) {
        return Result.success(operationService.decideConfirmation(
                confirmationId, "USER", BaseContext.getCurrentId(), true));
    }

    /**
     * 用户拒绝Agent工具操作确认单，阻止Agent执行高风险操作
     *
     * @param confirmationId 确认单ID
     * @return 拒绝结果
     */
    @PostMapping("/confirmations/{confirmationId}/reject")
    public Result<Map<String, Object>> reject(@PathVariable String confirmationId) {
        return Result.success(operationService.decideConfirmation(
                confirmationId, "USER", BaseContext.getCurrentId(), false));
    }
}