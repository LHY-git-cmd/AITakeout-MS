package com.sky.controller.admin;

import com.sky.annotation.RequireAdminPermission;
import com.sky.context.BaseContext;
import com.sky.dto.AdminAccountAdjustmentDTO;
import com.sky.result.Result;
import com.sky.enumeration.AdminPermission;
import com.sky.service.account.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端模拟账户查询与审计调账接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/mock-account")
public class AdminMockAccountController {
    private final AccountService accountService;

    @GetMapping
    @RequireAdminPermission(AdminPermission.ACCOUNT_READ)
    public Result<?> accounts() {
        return Result.success(accountService.listUserAccounts());
    }

    @GetMapping("/{userId}")
    @RequireAdminPermission(AdminPermission.ACCOUNT_READ)
    public Result<?> account(@PathVariable long userId) {
        return Result.success(accountService.getUserAccount(userId));
    }

    @PostMapping("/adjustments")
    @RequireAdminPermission(AdminPermission.ACCOUNT_ADJUST)
    public Result<?> adjust(@Valid @RequestBody AdminAccountAdjustmentDTO dto) {
        return Result.success(accountService.adjust(BaseContext.getCurrentId(), dto.getUserId(), dto.getDeltaCent(), dto.getReason(), dto.getIdempotencyKey()));
    }
}
