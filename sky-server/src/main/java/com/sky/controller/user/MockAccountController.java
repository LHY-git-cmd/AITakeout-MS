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

    /**
     * 查询当前用户的模拟账户信息（余额、赠金等）
     *
     * @return 用户账户信息
     */
    @GetMapping
    public Result<?> account() {
        return Result.success(accountService.getUserAccount(BaseContext.getCurrentId()));
    }

    /**
     * 领取模拟赠金，支持幂等性防重复领取
     *
     * @param idempotencyKey 幂等性键，防止重复领取
     * @return 领取结果（含赠金金额）
     */
    @PostMapping("/grants")
    public Result<?> grant(@RequestParam String idempotencyKey) {
        return Result.success(accountService.grant(BaseContext.getCurrentId(), idempotencyKey));
    }

    /**
     * 查询当前用户的账户账本（收支明细）
     *
     * @return 账本流水列表
     */
    @GetMapping("/ledger")
    public Result<?> ledger() {
        return Result.success(accountService.ledger(BaseContext.getCurrentId()));
    }
}