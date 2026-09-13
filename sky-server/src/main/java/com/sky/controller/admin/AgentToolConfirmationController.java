package com.sky.controller.admin;

import com.sky.context.BaseContext;
import com.sky.result.Result;
import com.sky.service.AgentToolOperationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 当前管理员确认或拒绝AI建议的写操作。 */
@RestController
@RequestMapping("/admin/agent/tool-confirmations")
@RequiredArgsConstructor
public class AgentToolConfirmationController {
    private final AgentToolOperationService operationService;

    @PostMapping("/{confirmationId}/confirm")
    public Result<Map<String, Object>> confirm(@PathVariable String confirmationId) {
        return Result.success(operationService.decideConfirmation(
                confirmationId, BaseContext.getCurrentId(), true));
    }

    @PostMapping("/{confirmationId}/reject")
    public Result<Map<String, Object>> reject(@PathVariable String confirmationId) {
        return Result.success(operationService.decideConfirmation(
                confirmationId, BaseContext.getCurrentId(), false));
    }
}
