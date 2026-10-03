package com.sky.controller.admin;

import com.sky.context.BaseContext;
import com.sky.result.Result;
import com.sky.service.AgentToolOperationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI Agent工具操作确认接口
 * <p>
 * 提供给管理员，用于确认或拒绝由AI Agent建议的写操作。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/agent/tool-confirmations")
@RequiredArgsConstructor
@Tag(name = "AI Agent工具操作确认接口")
public class AgentToolConfirmationController {
    private final AgentToolOperationService operationService;

    /**
     * 确认AI建议的操作。
     *
     * @param confirmationId 确认ID。
     * @return 操作结果。
     */
    @PostMapping("/{confirmationId}/confirm")
    @Operation(summary = "确认AI操作")
    public Result<Map<String, Object>> confirm(
            @Parameter(description = "确认ID") @PathVariable String confirmationId) {
        log.info("确认AI操作: confirmationId={}", confirmationId);
        return Result.success(operationService.decideConfirmation(
                confirmationId, BaseContext.getCurrentId(), true));
    }

    /**
     * 拒绝AI建议的操作。
     *
     * @param confirmationId 确认ID。
     * @return 操作结果。
     */
    @PostMapping("/{confirmationId}/reject")
    @Operation(summary = "拒绝AI操作")
    public Result<Map<String, Object>> reject(
            @Parameter(description = "确认ID") @PathVariable String confirmationId) {
        log.info("拒绝AI操作: confirmationId={}", confirmationId);
        return Result.success(operationService.decideConfirmation(
                confirmationId, BaseContext.getCurrentId(), false));
    }
}