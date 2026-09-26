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

    @PostMapping("/sessions")
    public Result<AgentSessionVO> createSession(
            @Valid @RequestBody(required = false) UserAgentSessionCreateDTO body) {
        return Result.success(agentService.createSession(body == null ? null : body.getTitle()));
    }

    @GetMapping("/sessions")
    public Result<PageResult> listSessions(@Valid AgentSessionPageQueryDTO query) {
        return Result.success(agentService.pageQuerySessions(query));
    }

    @GetMapping("/sessions/{sessionId}")
    public Result<AgentSessionDetailVO> session(@PathVariable String sessionId) {
        return Result.success(agentService.getSessionDetail(sessionId));
    }

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

    @GetMapping("/tasks/{taskId}")
    public Result<AgentTaskVO> task(@PathVariable String taskId) {
        return Result.success(agentService.getTaskDetail(taskId));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public Result<Void> cancel(@PathVariable String taskId) {
        agentService.cancelTask(taskId);
        return Result.success();
    }

    @GetMapping("/tasks/{taskId}/events")
    public SseEmitter events(
            @PathVariable String taskId,
            @RequestHeader(value = "Last-Event-ID", required = false, defaultValue = "0") int lastSeqNo,
            HttpServletResponse response) {
        return sseService.subscribe(taskId, lastSeqNo, response);
    }

    @PostMapping("/confirmations/{confirmationId}/approve")
    public Result<Map<String, Object>> approve(@PathVariable String confirmationId) {
        return Result.success(operationService.decideConfirmation(
                confirmationId, "USER", BaseContext.getCurrentId(), true));
    }

    @PostMapping("/confirmations/{confirmationId}/reject")
    public Result<Map<String, Object>> reject(@PathVariable String confirmationId) {
        return Result.success(operationService.decideConfirmation(
                confirmationId, "USER", BaseContext.getCurrentId(), false));
    }
}
