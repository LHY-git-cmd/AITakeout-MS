package com.sky.controller.admin;

import com.sky.annotation.RequireAdminPermission;
import com.sky.context.BaseContext;
import com.sky.dto.AdminAccountAdjustmentDTO;
import com.sky.enumeration.AdminPermission;
import com.sky.result.Result;
import com.sky.service.account.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端模拟账户管理接口
 * <p>
 * 提供对用户模拟账户的查询和调账功能。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/mock-account")
@Tag(name = "模拟账户管理接口")
public class AdminMockAccountController {
    private final AccountService accountService;

    /**
     * 获取所有用户账户列表。
     *
     * @return 用户账户列表。
     */
    @GetMapping
    @Operation(summary = "获取所有用户账户")
    @RequireAdminPermission(AdminPermission.ACCOUNT_READ)
    public Result<?> accounts() {
        log.info("获取所有用户账户列表");
        return Result.success(accountService.listUserAccounts());
    }

    /**
     * 获取指定用户的账户信息。
     *
     * @param userId 用户ID。
     * @return 用户账户信息。
     */
    @GetMapping("/{userId}")
    @Operation(summary = "获取用户账户信息")
    @RequireAdminPermission(AdminPermission.ACCOUNT_READ)
    public Result<?> account(@Parameter(description = "用户ID") @PathVariable long userId) {
        log.info("获取用户账户信息: userId={}", userId);
        return Result.success(accountService.getUserAccount(userId));
    }

    /**
     * 对用户账户进行调账。
     *
     * @param dto 包含调账信息的DTO。
     * @return 调账结果。
     */
    @PostMapping("/adjustments")
    @Operation(summary = "调整用户账户余额")
    @RequireAdminPermission(AdminPermission.ACCOUNT_ADJUST)
    public Result<?> adjust(@Valid @RequestBody AdminAccountAdjustmentDTO dto) {
        log.info("调整用户账户余额: {}", dto);
        return Result.success(accountService.adjust(BaseContext.getCurrentId(), dto.getUserId(), dto.getDeltaCent(), dto.getReason(), dto.getIdempotencyKey()));
    }
}