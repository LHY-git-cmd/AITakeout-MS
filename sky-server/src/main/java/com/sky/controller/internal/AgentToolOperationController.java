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

    @PostMapping("/execute")
    public AgentToolOperationResponse execute(@RequestBody AgentToolOperationRequest request) {
        return operationService.execute(request);
    }

    @PostMapping("/prepare")
    public AgentToolOperationResponse prepare(@RequestBody AgentToolOperationRequest request) {
        return operationService.prepare(request);
    }

    @org.springframework.web.bind.annotation.GetMapping("/confirmations/{confirmationId}")
    public Map<String, Object> confirmationStatus(
            @org.springframework.web.bind.annotation.PathVariable String confirmationId) {
        return operationService.confirmationStatus(confirmationId);
    }

    @PostMapping("/execute-confirmed")
    public AgentToolOperationResponse executeConfirmed(
            @RequestBody AgentToolConfirmationRequest request) {
        return operationService.executeConfirmed(request.confirmationId());
    }
}
