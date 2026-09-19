package com.sky.service.account;

import com.sky.entity.FundTransfer;
import com.sky.entity.MockAccount;
import com.sky.mapper.AccountLedgerEntryMapper;
import com.sky.mapper.FundTransferMapper;
import com.sky.mapper.MockAccountMapper;
import com.sky.service.account.model.AccountModels.AdjustmentResult;
import com.sky.service.account.model.AccountModels.GrantResult;
import com.sky.service.account.model.AccountModels.TransferCommand;
import com.sky.service.account.model.AccountModels.TransferResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 用户模拟账户编排服务，提供开户、领取和管理端调账。 */
@Service
@RequiredArgsConstructor
public class AccountService {
    public static final long GRANT_CENT = 50_000L;
    public static final long MAX_BALANCE_CENT = 1_000_000L;
    private final MockAccountMapper accountMapper;
    private final FundTransferMapper transferMapper;
    private final AccountLedgerEntryMapper ledgerMapper;
    private final LedgerTransferService transferService;

    @Transactional
    public void openUserAccount(long userId, long initialCent) {
        if (userId <= 0 || initialCent < 0 || initialCent > MAX_BALANCE_CENT) throw new IllegalArgumentException("开户参数无效");
        if (accountMapper.findByTypeAndOwner("USER", userId) != null) return;
        MockAccount treasury = ensureTreasury();
        LocalDateTime now = LocalDateTime.now();
        accountMapper.insert(MockAccount.builder().accountNo("USER-" + userId).accountType("USER").ownerId(userId)
                .availableCent(initialCent).frozenCent(0L).version(0).createTime(now).updateTime(now).build());
        if (initialCent > 0) {
            if (accountMapper.updateBalances(treasury.getId(), treasury.getAvailableCent() - initialCent,
                    treasury.getFrozenCent(), treasury.getVersion(), now) != 1) throw new IllegalStateException("账户并发冲突");
        }
    }

    @Transactional
    public GrantResult grant(long userId, String idempotencyKey) {
        MockAccount user = requireUser(userId);
        MockAccount treasury = ensureTreasury();
        String businessKey = "GRANT:" + idempotencyKey;
        FundTransfer existing = transferMapper.findByBusinessKeyForUpdate(businessKey);
        if (existing != null) return new GrantResult(existing.getId(), existing.getTransferNo(), user.getId(), existing.getAmountCent(), user.getAvailableCent(), true);
        if (user.getAvailableCent() + GRANT_CENT > MAX_BALANCE_CENT) throw new IllegalStateException("账户余额超过领取上限");
        TransferResult result = transferService.transfer(new TransferCommand(businessKey, treasury.getId(), user.getId(), GRANT_CENT, "MOCK_GRANT"));
        return new GrantResult(result.transferId(), result.transferNo(), user.getId(), GRANT_CENT, accountMapper.findById(user.getId()).getAvailableCent(), result.replayed());
    }

    @Transactional
    public AdjustmentResult adjust(long operatorId, long userId, long deltaCent, String reason, String idempotencyKey) {
        if (operatorId <= 0) throw new IllegalArgumentException("操作人不能为空");
        if (deltaCent == 0) throw new IllegalArgumentException("调整金额不能为0");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("调账原因不能为空");
        MockAccount user = requireUser(userId);
        String businessKey = "ADMIN_ADJUSTMENT:" + idempotencyKey;
        FundTransfer existing = transferMapper.findByBusinessKeyForUpdate(businessKey);
        if (existing != null) return new AdjustmentResult(existing.getId(), existing.getTransferNo(), existing.getOperatorId(), userId, existing.getAdjustedAccountId(), deltaCent, existing.getReason(), existing.getBalanceBeforeCent(), existing.getBalanceAfterCent(), true);
        if (deltaCent > 0 && user.getAvailableCent() + deltaCent > MAX_BALANCE_CENT) throw new IllegalStateException("账户余额超过上限");
        MockAccount treasury = ensureTreasury();
        long before = user.getAvailableCent();
        long amount = Math.abs(deltaCent);
        TransferCommand command = deltaCent > 0 ? new TransferCommand(businessKey, treasury.getId(), user.getId(), amount, "ADMIN_ADJUSTMENT") : new TransferCommand(businessKey, user.getId(), treasury.getId(), amount, "ADMIN_ADJUSTMENT");
        TransferResult result = transferService.transfer(command);
        FundTransfer transfer = transferMapper.findById(result.transferId());
        transfer.setOperatorId(operatorId); transfer.setReason(reason.trim()); transfer.setAdjustedAccountId(user.getId());
        transfer.setBalanceBeforeCent(before); transfer.setBalanceAfterCent(before + deltaCent);
        transferMapper.updateAudit(transfer);
        return new AdjustmentResult(result.transferId(), result.transferNo(), operatorId, userId, user.getId(), deltaCent, reason.trim(), before, before + deltaCent, false);
    }

    public MockAccount getUserAccount(long userId) { return requireUser(userId); }
    public List<MockAccount> listUserAccounts() { return accountMapper.listUserAccounts(null); }
    public List<MockAccount> listUserAccounts(long userId) { return accountMapper.listUserAccounts(userId); }
    public List<com.sky.entity.AccountLedgerEntry> ledger(long userId) {
        return ledgerMapper.findByAccountId(requireUser(userId).getId());
    }

    private MockAccount requireUser(long userId) {
        MockAccount account = accountMapper.findByTypeAndOwner("USER", userId);
        if (account == null) throw new IllegalStateException("用户账户不存在");
        return account;
    }

    private MockAccount ensureTreasury() {
        MockAccount treasury = accountMapper.findByTypeAndOwner("PLATFORM_TREASURY", 0L);
        if (treasury != null) return treasury;
        LocalDateTime now = LocalDateTime.now();
        accountMapper.insert(MockAccount.builder().accountNo("PLATFORM_TREASURY").accountType("PLATFORM_TREASURY").ownerId(0L).availableCent(0L).frozenCent(0L).version(0).createTime(now).updateTime(now).build());
        return accountMapper.findByTypeAndOwner("PLATFORM_TREASURY", 0L);
    }

}
