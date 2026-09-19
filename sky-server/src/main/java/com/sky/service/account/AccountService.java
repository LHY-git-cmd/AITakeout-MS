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
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 80;
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
                .availableCent(0L).frozenCent(0L).version(0).createTime(now).updateTime(now).build());
        if (initialCent > 0) {
            MockAccount user = accountMapper.findByTypeAndOwner("USER", userId);
            transferService.transfer(new TransferCommand("ACCOUNT_OPEN:" + userId, treasury.getId(), user.getId(),
                    initialCent, "ACCOUNT_OPEN", MAX_BALANCE_CENT));
        }
    }

    @Transactional
    public GrantResult grant(long userId, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        MockAccount user = requireUser(userId);
        MockAccount treasury = ensureTreasury();
        String businessKey = "GRANT:" + userId + ":" + idempotencyKey.trim();
        TransferResult result = transferService.transfer(new TransferCommand(businessKey, treasury.getId(), user.getId(),
                GRANT_CENT, "MOCK_GRANT", MAX_BALANCE_CENT));
        return new GrantResult(result.transferId(), result.transferNo(), user.getId(), GRANT_CENT,
                result.targetBalanceAfterCent(), result.replayed());
    }

    @Transactional
    public AdjustmentResult adjust(long operatorId, long userId, long deltaCent, String reason, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        if (operatorId <= 0) throw new IllegalArgumentException("操作人不能为空");
        if (deltaCent == 0) throw new IllegalArgumentException("调整金额不能为0");
        if (deltaCent == Long.MIN_VALUE) throw new IllegalArgumentException("调整金额超出范围");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("调账原因不能为空");
        MockAccount user = requireUser(userId);
        String businessKey = "ADMIN_ADJUSTMENT:" + userId + ":" + idempotencyKey.trim();
        MockAccount treasury = ensureTreasury();
        long amount = Math.abs(deltaCent);
        TransferCommand command = deltaCent > 0
                ? new TransferCommand(businessKey, treasury.getId(), user.getId(), amount, "ADMIN_ADJUSTMENT", MAX_BALANCE_CENT)
                : new TransferCommand(businessKey, user.getId(), treasury.getId(), amount, "ADMIN_ADJUSTMENT");
        TransferResult result = transferService.transfer(command);
        FundTransfer transfer = transferMapper.findById(result.transferId());
        if (result.replayed()) return originalAdjustment(transfer);
        long after = deltaCent > 0 ? result.targetBalanceAfterCent() : result.sourceBalanceAfterCent();
        long before = deltaCent > 0 ? after - amount : after + amount;
        transfer.setOperatorId(operatorId); transfer.setReason(reason.trim()); transfer.setAdjustedAccountId(user.getId());
        transfer.setBalanceBeforeCent(before); transfer.setBalanceAfterCent(after);
        if (transferMapper.updateAudit(transfer) != 1) throw new IllegalStateException("调账审计并发冲突");
        return new AdjustmentResult(result.transferId(), result.transferNo(), operatorId, userId, user.getId(), deltaCent, reason.trim(), before, after, false);
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

    private AdjustmentResult originalAdjustment(FundTransfer transfer) {
        MockAccount adjusted = accountMapper.findById(transfer.getAdjustedAccountId());
        if (adjusted == null || transfer.getOperatorId() == null || transfer.getBalanceBeforeCent() == null
                || transfer.getBalanceAfterCent() == null) throw new IllegalStateException("调账审计记录不完整");
        return new AdjustmentResult(transfer.getId(), transfer.getTransferNo(), transfer.getOperatorId(),
                adjusted.getOwnerId(), adjusted.getId(), transfer.getBalanceAfterCent() - transfer.getBalanceBeforeCent(),
                transfer.getReason(), transfer.getBalanceBeforeCent(), transfer.getBalanceAfterCent(), true);
    }

    private static void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("幂等键不能为空");
        if (key.trim().length() > MAX_IDEMPOTENCY_KEY_LENGTH) throw new IllegalArgumentException("幂等键过长");
    }

}
