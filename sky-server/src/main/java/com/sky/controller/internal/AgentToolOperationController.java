package com.sky.controller.internal;

import com.sky.dto.AgentToolOperationRequest;
import com.sky.dto.AgentToolConfirmationRequest;
import com.sky.service.AgentToolOperationService;
import com.sky.vo.AgentToolOperationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 仅供受信任Python Agent服务访问的原子业务接口。 */
@RestController
@RequestMapping("/internal/agent/operations")
@RequiredArgsConstructor
public class AgentToolOperationController {
    private final AgentToolOperationService operationService;

    /**
     * 直接执行工具操作（无需用户确认的低风险操作）
     *
     * @param request 工具操作请求（含操作类型、参数等）
     * @return 工具操作执行结果
     */
    @PostMapping("/execute")
    public AgentToolOperationResponse execute(@RequestBody AgentToolOperationRequest request) {
        return operationService.execute(request);
    }

    /**
     * 预执行高风险工具操作，生成待用户确认的确认单
     *
     * @param request 工具操作请求
     * @return 包含确认ID的响应，等待用户审批
     */
    @PostMapping("/prepare")
    public AgentToolOperationResponse prepare(@RequestBody AgentToolOperationRequest request) {
        return operationService.prepare(request);
    }

    /**
     * 查询确认单的当前状态（待确认/已批准/已拒绝/已执行等）
     *
     * @param confirmationId 确认单ID
     * @return 确认单状态及相关信息
     */
    @org.springframework.web.bind.annotation.GetMapping("/confirmations/{confirmationId}")
    public Map<String, Object> confirmationStatus(
            @org.springframework.web.bind.annotation.PathVariable String confirmationId) {
        return operationService.confirmationStatus(confirmationId);
    }

    /**
     * 在用户批准确认单后，正式执行对应的高风险工具操作
     *
     * @param request 包含确认单ID的确认执行请求
     * @return 工具操作执行结果
     */
    @PostMapping("/execute-confirmed")
    public AgentToolOperationResponse executeConfirmed(
            @RequestBody AgentToolConfirmationRequest request) {
        return operationService.executeConfirmed(request.confirmationId());
    }
}