package com.sky.service.account;

import com.sky.entity.AccountLedgerEntry;
import com.sky.entity.FundTransfer;
import com.sky.entity.MockAccount;
import com.sky.mapper.AccountLedgerEntryMapper;
import com.sky.mapper.FundTransferMapper;
import com.sky.mapper.MockAccountMapper;
import com.sky.service.account.model.AccountModels.FreezeCommand;
import com.sky.service.account.model.AccountModels.FreezeResult;
import com.sky.service.account.model.AccountModels.TransferCommand;
import com.sky.service.account.model.AccountModels.TransferResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 统一资金转移入口；所有余额变更都在此服务内完成。 */
@Service
@RequiredArgsConstructor
public class LedgerTransferService {
    private final MockAccountMapper accountMapper;
    private final FundTransferMapper transferMapper;
    private final AccountLedgerEntryMapper ledgerMapper;

    @Transactional
    public TransferResult transfer(TransferCommand command) {
        validate(command);
        FundTransfer existing = transferMapper.findByBusinessKeyForUpdate(command.businessKey());
        if (existing != null) return toResult(existing, true);
        List<Long> ids = List.of(command.sourceAccountId(), command.targetAccountId()).stream().distinct().sorted().toList();
        List<MockAccount> locked = ids.stream().map(accountMapper::findByIdForUpdate).toList();
        MockAccount source = find(locked, command.sourceAccountId());
        MockAccount target = find(locked, command.targetAccountId());
        if (source == null || target == null) throw new IllegalStateException("账户不存在");
        if (!"PLATFORM_TREASURY".equals(source.getAccountType()) && source.getAvailableCent() < command.amountCent()) throw new IllegalStateException("余额不足");
        LocalDateTime now = LocalDateTime.now();
        long sourceAfter = source.getAvailableCent() - command.amountCent();
        long targetAfter = target.getAvailableCent() + command.amountCent();
        FundTransfer transfer = FundTransfer.builder().transferNo("TR-" + UUID.randomUUID())
                .businessKey(command.businessKey()).transferType(command.transferType())
                .sourceAccountId(source.getId()).targetAccountId(target.getId()).amountCent(command.amountCent())
                .status("COMPLETED").completedAt(now).createTime(now).updateTime(now).build();
        transferMapper.insert(transfer);
        if (accountMapper.updateBalances(source.getId(), sourceAfter, source.getFrozenCent(), source.getVersion(), now) != 1
                || accountMapper.updateBalances(target.getId(), targetAfter, target.getFrozenCent(), target.getVersion(), now) != 1) {
            throw new IllegalStateException("账户并发冲突");
        }
        ledgerMapper.insert(AccountLedgerEntry.builder().transferId(transfer.getId()).accountId(source.getId())
                .direction("DEBIT").amountCent(command.amountCent()).balanceAfterCent(sourceAfter).createTime(now).build());
        ledgerMapper.insert(AccountLedgerEntry.builder().transferId(transfer.getId()).accountId(target.getId())
                .direction("CREDIT").amountCent(command.amountCent()).balanceAfterCent(targetAfter).createTime(now).build());
        return new TransferResult(transfer.getId(), transfer.getTransferNo(), transfer.getBusinessKey(), source.getId(), target.getId(),
                transfer.getAmountCent(), sourceAfter, targetAfter, false);
    }

    @Transactional
    public FreezeResult freeze(FreezeCommand command) {
        if (command.amountCent() <= 0) throw new IllegalArgumentException("冻结金额必须为正数");
        FundTransfer existing = transferMapper.findByBusinessKeyForUpdate(command.businessKey());
        if (existing != null) {
            MockAccount account = accountMapper.findById(existing.getSourceAccountId());
            return new FreezeResult(existing.getId(), existing.getTransferNo(), account.getId(), existing.getAmountCent(), account.getAvailableCent(), account.getFrozenCent(), true);
        }
        MockAccount account = accountMapper.findByIdForUpdate(command.accountId());
        if (account == null) throw new IllegalStateException("账户不存在");
        if (account.getAvailableCent() < command.amountCent()) throw new IllegalStateException("余额不足");
        LocalDateTime now = LocalDateTime.now();
        long availableAfter = account.getAvailableCent() - command.amountCent();
        long frozenAfter = account.getFrozenCent() + command.amountCent();
        FundTransfer transfer = FundTransfer.builder().transferNo("TR-" + UUID.randomUUID()).businessKey(command.businessKey())
                .transferType(command.transferType()).sourceAccountId(account.getId()).targetAccountId(account.getId())
                .amountCent(command.amountCent()).status("COMPLETED").completedAt(now).createTime(now).updateTime(now).build();
        transferMapper.insert(transfer);
        if (accountMapper.updateBalances(account.getId(), availableAfter, frozenAfter, account.getVersion(), now) != 1) throw new IllegalStateException("账户并发冲突");
        ledgerMapper.insert(AccountLedgerEntry.builder().transferId(transfer.getId()).accountId(account.getId()).direction("DEBIT").amountCent(command.amountCent()).balanceAfterCent(availableAfter).createTime(now).build());
        ledgerMapper.insert(AccountLedgerEntry.builder().transferId(transfer.getId()).accountId(account.getId()).direction("CREDIT").amountCent(command.amountCent()).balanceAfterCent(frozenAfter).createTime(now).build());
        return new FreezeResult(transfer.getId(), transfer.getTransferNo(), account.getId(), command.amountCent(), availableAfter, frozenAfter, false);
    }

    private static MockAccount find(List<MockAccount> accounts, long id) {
        return accounts.stream().filter(account -> account != null && account.getId().equals(id)).findFirst().orElse(null);
    }

    private static void validate(TransferCommand command) {
        if (command == null || command.businessKey() == null || command.businessKey().isBlank()) throw new IllegalArgumentException("业务键不能为空");
        if (command.sourceAccountId() == command.targetAccountId()) throw new IllegalArgumentException("转出和转入账户不能相同");
        if (command.amountCent() <= 0) throw new IllegalArgumentException("转移金额必须为正数");
    }

    private TransferResult toResult(FundTransfer transfer, boolean replayed) {
        MockAccount source = accountMapper.findById(transfer.getSourceAccountId());
        MockAccount target = accountMapper.findById(transfer.getTargetAccountId());
        return new TransferResult(transfer.getId(), transfer.getTransferNo(), transfer.getBusinessKey(),
                transfer.getSourceAccountId(), transfer.getTargetAccountId(), transfer.getAmountCent(),
                source == null ? 0L : source.getAvailableCent(), target == null ? 0L : target.getAvailableCent(), replayed);
    }
}
