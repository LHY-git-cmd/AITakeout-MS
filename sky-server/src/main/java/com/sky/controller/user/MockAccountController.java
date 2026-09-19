package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.result.Result;
import com.sky.service.account.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 用户模拟账户、领取和账本接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/user/mock-account")
public class MockAccountController {
    private final AccountService accountService;

    @GetMapping
    public Result<?> account() {
        return Result.success(accountService.getUserAccount(BaseContext.getCurrentId()));
    }

    @PostMapping("/grants")
    public Result<?> grant(@RequestParam String idempotencyKey) {
        return Result.success(accountService.grant(BaseContext.getCurrentId(), idempotencyKey));
    }

    @GetMapping("/ledger")
    public Result<?> ledger() {
        return Result.success(accountService.ledger(BaseContext.getCurrentId()));
    }
}
