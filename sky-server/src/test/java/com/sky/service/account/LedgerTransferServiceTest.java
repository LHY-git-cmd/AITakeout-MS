package com.sky.service.account;

import com.sky.entity.AccountLedgerEntry;
import com.sky.entity.FundTransfer;
import com.sky.entity.MockAccount;
import com.sky.mapper.AccountLedgerEntryMapper;
import com.sky.mapper.FundTransferMapper;
import com.sky.mapper.MockAccountMapper;
import com.sky.service.account.model.AccountModels.FreezeCommand;
import com.sky.service.account.model.AccountModels.TransferCommand;
import com.sky.service.account.model.AccountModels.TransferResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerTransferServiceTest {

    private InMemoryAccountMapper accounts;
    private InMemoryTransferMapper transfers;
    private InMemoryLedgerMapper ledger;
    private LedgerTransferService transferService;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountMapper();
        accounts.add(account(1L, "USER-7", "USER", 7L, 50_000L));
        accounts.add(account(2L, "PLATFORM-PENDING", "PLATFORM_PENDING", 0L, 0L));
        transfers = new InMemoryTransferMapper();
        ledger = new InMemoryLedgerMapper();
        transferService = new LedgerTransferService(accounts, transfers, ledger);
    }

    @Test
    void transferCreatesBalancedEntriesAndChangesBalancesOnce() {
        TransferResult result = transferService.transfer(
                new TransferCommand("PAY-1", 1L, 2L, 3_900L, "ORDER_PAYMENT"));

        assertThat(ledger.sumByTransfer(result.transferId())).isZero();
        assertThat(accounts.findById(1L).getAvailableCent()).isEqualTo(46_100L);
        assertThat(accounts.findById(2L).getAvailableCent()).isEqualTo(3_900L);
        assertThat(ledger.findByTransferId(result.transferId())).hasSize(2);
    }

    @Test
    void repeatedBusinessKeyReturnsOriginalResultWithoutPostingAgain() {
        TransferCommand command = new TransferCommand("PAY-IDEMPOTENT", 1L, 2L, 7_500L, "ORDER_PAYMENT");

        TransferResult first = transferService.transfer(command);
        TransferResult replay = transferService.transfer(command);

        assertThat(replay.transferId()).isEqualTo(first.transferId());
        assertThat(replay.sourceBalanceAfterCent()).isEqualTo(first.sourceBalanceAfterCent());
        assertThat(replay.targetBalanceAfterCent()).isEqualTo(first.targetBalanceAfterCent());
        assertThat(replay.replayed()).isTrue();
        assertThat(ledger.findByTransferId(first.transferId())).hasSize(2);
        assertThat(accounts.findById(1L).getAvailableCent()).isEqualTo(42_500L);
    }

    @Test
    void transferRejectsInsufficientAvailableBalanceWithoutWritingLedger() {
        assertThatThrownBy(() -> transferService.transfer(
                new TransferCommand("PAY-TOO-MUCH", 1L, 2L, 50_001L, "ORDER_PAYMENT")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("余额不足");

        assertThat(transfers.values()).isEmpty();
        assertThat(ledger.entries).isEmpty();
    }

    @Test
    void optimisticVersionConflictRollsBackInMemoryChangesAndWritesNoLedger() {
        accounts.failNextUpdate = true;

        assertThatThrownBy(() -> transferService.transfer(
                new TransferCommand("PAY-CONFLICT", 1L, 2L, 1_000L, "ORDER_PAYMENT")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("并发");

        assertThat(accounts.findById(1L).getAvailableCent()).isEqualTo(50_000L);
        assertThat(accounts.findById(2L).getAvailableCent()).isZero();
        assertThat(ledger.entries).isEmpty();
    }

    @Test
    void freezeMovesAvailableToFrozenAndKeepsLedgerBalanced() {
        var result = transferService.freeze(new FreezeCommand("FREEZE-1", 1L, 8_000L, "ORDER_HOLD"));

        assertThat(result.availableAfterCent()).isEqualTo(42_000L);
        assertThat(result.frozenAfterCent()).isEqualTo(8_000L);
        assertThat(ledger.sumByTransfer(result.transferId())).isZero();
    }

    private static MockAccount account(long id, String no, String type, long ownerId, long available) {
        return MockAccount.builder().id(id).accountNo(no).accountType(type).ownerId(ownerId)
                .availableCent(available).frozenCent(0L).version(0).build();
    }

    private static MockAccount copy(MockAccount source) {
        return MockAccount.builder().id(source.getId()).accountNo(source.getAccountNo())
                .accountType(source.getAccountType()).ownerId(source.getOwnerId())
                .availableCent(source.getAvailableCent()).frozenCent(source.getFrozenCent())
                .version(source.getVersion()).createTime(source.getCreateTime()).updateTime(source.getUpdateTime())
                .build();
    }

    private static final class InMemoryAccountMapper implements MockAccountMapper {
        private final Map<Long, MockAccount> data = new HashMap<>();
        private boolean failNextUpdate;

        void add(MockAccount account) {
            data.put(account.getId(), copy(account));
        }

        @Override
        public int insert(MockAccount account) {
            long id = data.keySet().stream().max(Long::compareTo).orElse(0L) + 1;
            account.setId(id);
            data.put(id, copy(account));
            return 1;
        }

        @Override
        public MockAccount findById(Long id) {
            MockAccount account = data.get(id);
            return account == null ? null : copy(account);
        }

        @Override
        public MockAccount findByIdForUpdate(Long id) {
            return findById(id);
        }

        @Override
        public MockAccount findByTypeAndOwner(String accountType, Long ownerId) {
            return data.values().stream()
                    .filter(value -> accountType.equals(value.getAccountType()) && ownerId.equals(value.getOwnerId()))
                    .findFirst().map(LedgerTransferServiceTest::copy).orElse(null);
        }

        @Override
        public List<MockAccount> listUserAccounts(Long userId) {
            return data.values().stream().filter(value -> "USER".equals(value.getAccountType()))
                    .filter(value -> userId == null || userId.equals(value.getOwnerId()))
                    .sorted(Comparator.comparing(MockAccount::getId)).map(LedgerTransferServiceTest::copy).toList();
        }

        @Override
        public int updateBalances(Long id, Long availableCent, Long frozenCent, Integer expectedVersion,
                                  LocalDateTime updateTime) {
            if (failNextUpdate) {
                failNextUpdate = false;
                return 0;
            }
            MockAccount current = data.get(id);
            if (current == null || !current.getVersion().equals(expectedVersion)) return 0;
            current.setAvailableCent(availableCent);
            current.setFrozenCent(frozenCent);
            current.setVersion(current.getVersion() + 1);
            current.setUpdateTime(updateTime);
            return 1;
        }
    }

    private static final class InMemoryTransferMapper implements FundTransferMapper {
        private final Map<Long, FundTransfer> data = new HashMap<>();

        @Override
        public int insert(FundTransfer transfer) {
            long id = data.size() + 1L;
            transfer.setId(id);
            data.put(id, transfer);
            return 1;
        }

        @Override
        public FundTransfer findByBusinessKey(String businessKey) {
            return data.values().stream().filter(value -> businessKey.equals(value.getBusinessKey()))
                    .findFirst().orElse(null);
        }

        @Override
        public FundTransfer findById(Long id) {
            return data.get(id);
        }

        @Override
        public int updateAudit(FundTransfer transfer) {
            data.put(transfer.getId(), transfer);
            return 1;
        }

        List<FundTransfer> values() {
            return new ArrayList<>(data.values());
        }
    }

    private static final class InMemoryLedgerMapper implements AccountLedgerEntryMapper {
        private final List<AccountLedgerEntry> entries = new ArrayList<>();

        @Override
        public int insert(AccountLedgerEntry entry) {
            entry.setId((long) entries.size() + 1);
            entries.add(entry);
            return 1;
        }

        @Override
        public List<AccountLedgerEntry> findByTransferId(Long transferId) {
            return entries.stream().filter(value -> transferId.equals(value.getTransferId()))
                    .sorted(Comparator.comparing(AccountLedgerEntry::getId)).toList();
        }

        @Override
        public List<AccountLedgerEntry> findByAccountId(Long accountId) {
            return entries.stream().filter(value -> accountId.equals(value.getAccountId()))
                    .sorted(Comparator.comparing(AccountLedgerEntry::getId).reversed()).toList();
        }

        long sumByTransfer(long transferId) {
            return findByTransferId(transferId).stream()
                    .mapToLong(value -> "DEBIT".equals(value.getDirection())
                            ? -value.getAmountCent() : value.getAmountCent()).sum();
        }
    }
}
