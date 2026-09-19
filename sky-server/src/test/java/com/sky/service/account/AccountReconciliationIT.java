package com.sky.service.account;

import com.sky.entity.FundTransfer;
import com.sky.entity.MockAccount;
import com.sky.mapper.AccountLedgerEntryMapper;
import com.sky.mapper.FundTransferMapper;
import com.sky.mapper.MockAccountMapper;
import com.sky.service.account.model.AccountModels.AdjustmentResult;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(AccountReconciliationIT.Config.class)
class AccountReconciliationIT {

    @Autowired private AccountService accountService;
    @Autowired private MockAccountMapper accountMapper;
    @Autowired private FundTransferMapper transferMapper;
    @Autowired private AccountLedgerEntryMapper ledgerMapper;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cleanBusinessRows() {
        jdbc.update("delete from account_ledger_entry");
        jdbc.update("delete from fund_transfer");
        jdbc.update("delete from mock_account where account_type = 'USER' or account_type = 'PLATFORM_TREASURY'");
        jdbc.update("update mock_account set available_cent = 0, frozen_cent = 0, version = 0");
    }

    @Test
    void databaseBalancesReconcileWithSignedLedgerAndEveryTransferIsBalanced() {
        accountService.openUserAccount(7L, 50_000L);
        accountService.grant(7L, "grant-1");
        MockAccount account = accountService.getUserAccount(7L);

        long accountTotal = jdbc.queryForObject(
                "select coalesce(sum(available_cent + frozen_cent), 0) from mock_account", Long.class);
        long ledgerTotal = jdbc.queryForObject(
                "select coalesce(sum(case when direction='DEBIT' then -amount_cent else amount_cent end), 0) "
                        + "from account_ledger_entry", Long.class);
        Integer unbalancedTransfers = jdbc.queryForObject(
                "select count(*) from (select transfer_id from account_ledger_entry group by transfer_id "
                        + "having sum(case when direction='DEBIT' then -amount_cent else amount_cent end) <> 0) t",
                Integer.class);

        assertThat(account.getAvailableCent()).isEqualTo(100_000L);
        assertThat(accountTotal).isZero();
        assertThat(ledgerTotal).isZero();
        assertThat(unbalancedTransfers).isZero();
        List<Long> unreconciledAccounts = jdbc.queryForList(
                "select a.id from mock_account a left join account_ledger_entry l on l.account_id=a.id "
                        + "group by a.id, a.available_cent having a.available_cent <> coalesce(sum("
                        + "case when l.direction='DEBIT' then -l.amount_cent else l.amount_cent end),0)", Long.class);
        assertThat(unreconciledAccounts).isEmpty();
    }

    @Test
    void openingAccountPostsInitialFundingTransferAndBalancedEntries() {
        accountService.openUserAccount(17L, 50_000L);
        MockAccount account = accountService.getUserAccount(17L);
        FundTransfer opening = transferMapper.findByBusinessKey("ACCOUNT_OPEN:17");

        assertThat(opening).isNotNull();
        assertThat(opening.getAmountCent()).isEqualTo(50_000L);
        assertThat(ledgerMapper.findByTransferId(opening.getId())).hasSize(2);
        assertThat(jdbc.queryForObject(
                "select sum(case when direction='DEBIT' then -amount_cent else amount_cent end) "
                        + "from account_ledger_entry where transfer_id=?", Long.class, opening.getId())).isZero();
        assertThat(account.getAvailableCent()).isEqualTo(50_000L);
    }

    @Test
    void grantEnforcesMillionCentCapWithoutPostingRejectedTransfer() {
        accountService.openUserAccount(8L, 50_000L);
        for (int index = 1; index < 20; index++) {
            accountService.grant(8L, "grant-cap-" + index);
        }

        assertThat(accountService.getUserAccount(8L).getAvailableCent()).isEqualTo(1_000_000L);
        assertThatThrownBy(() -> accountService.grant(8L, "grant-cap-over"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("上限");
        assertThat(transferMapper.findByBusinessKey("GRANT:8:grant-cap-over")).isNull();
    }

    @Test
    void concurrentDifferentGrantKeysCannotExceedCap() throws Exception {
        accountService.openUserAccount(18L, 950_000L);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> first = () -> grantAfterBarrier(18L, "parallel-a", ready, start);
            Callable<Boolean> second = () -> grantAfterBarrier(18L, "parallel-b", ready, start);
            var futures = List.of(executor.submit(first), executor.submit(second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(futures.stream().map(future -> {
                try { return future.get(10, TimeUnit.SECONDS); }
                catch (Exception exception) { throw new AssertionError(exception); }
            }).filter(Boolean::booleanValue).count()).isEqualTo(1);
        }
        assertThat(accountService.getUserAccount(18L).getAvailableCent()).isEqualTo(1_000_000L);
        assertThat(jdbc.queryForObject("select count(*) from fund_transfer where business_key like 'GRANT:18:parallel-%'", Integer.class)).isEqualTo(1);
    }

    @Test
    void grantIdempotencyKeyIsScopedToUser() {
        accountService.openUserAccount(19L, 50_000L);
        accountService.openUserAccount(20L, 50_000L);

        var first = accountService.grant(19L, "same-key");
        var second = accountService.grant(20L, "same-key");

        assertThat(second.transferId()).isNotEqualTo(first.transferId());
        assertThat(accountService.getUserAccount(19L).getAvailableCent()).isEqualTo(100_000L);
        assertThat(accountService.getUserAccount(20L).getAvailableCent()).isEqualTo(100_000L);
    }

    @Test
    void concurrentSameGrantKeyReturnsOneTransferWithoutDuplicateLedger() throws Exception {
        accountService.openUserAccount(22L, 50_000L);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Long> call = () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return accountService.grant(22L, "same-concurrent-key").transferId();
            };
            var futures = List.of(executor.submit(call), executor.submit(call));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long first = futures.get(0).get(10, TimeUnit.SECONDS);
            long second = futures.get(1).get(10, TimeUnit.SECONDS);
            assertThat(second).isEqualTo(first);
            assertThat(ledgerMapper.findByTransferId(first)).hasSize(2);
        }
        assertThat(accountService.getUserAccount(22L).getAvailableCent()).isEqualTo(100_000L);
        assertThat(jdbc.queryForObject("select count(*) from fund_transfer where business_key='GRANT:22:same-concurrent-key'", Integer.class)).isEqualTo(1);
    }

    @Test
    void adminAdjustmentPersistsAuditAndIdempotentReplayReturnsOriginalResult() {
        accountService.openUserAccount(9L, 50_000L);

        AdjustmentResult first = accountService.adjust(101L, 9L, 12_345L, "客服补偿", "adjust-1");
        AdjustmentResult replay = accountService.adjust(202L, 9L, -999L, "不同请求内容", "adjust-1");
        FundTransfer persisted = transferMapper.findByBusinessKey("ADMIN_ADJUSTMENT:9:adjust-1");

        assertThat(replay.transferId()).isEqualTo(first.transferId());
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.operatorId()).isEqualTo(first.operatorId());
        assertThat(replay.deltaCent()).isEqualTo(first.deltaCent());
        assertThat(replay.reason()).isEqualTo(first.reason());
        assertThat(replay.userId()).isEqualTo(first.userId());
        assertThat(accountService.getUserAccount(9L).getAvailableCent()).isEqualTo(62_345L);
        assertThat(persisted.getOperatorId()).isEqualTo(101L);
        assertThat(persisted.getReason()).isEqualTo("客服补偿");
        assertThat(persisted.getAdjustedAccountId()).isEqualTo(first.accountId());
        assertThat(persisted.getBalanceBeforeCent()).isEqualTo(50_000L);
        assertThat(persisted.getBalanceAfterCent()).isEqualTo(62_345L);
        assertThat(ledgerMapper.findByTransferId(first.transferId())).hasSize(2);
    }

    @Test
    void rejectsInvalidIdempotencyKeysBeforeWritingTransfers() {
        accountService.openUserAccount(21L, 50_000L);
        assertThatThrownBy(() -> accountService.grant(21L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accountService.grant(21L, "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accountService.grant(21L, "x".repeat(81))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accountService.adjust(1L, 21L, 1L, "reason", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accountService.adjust(1L, 21L, 1L, "reason", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accountService.adjust(1L, 21L, 1L, "reason", "x".repeat(81))).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select count(*) from fund_transfer where transfer_type <> 'ACCOUNT_OPEN'", Integer.class)).isZero();
    }

    private boolean grantAfterBarrier(long userId, String key, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await(5, TimeUnit.SECONDS);
            accountService.grant(userId, key);
            return true;
        } catch (IllegalStateException expected) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.sky.mapper")
    @Import({AccountService.class, LedgerTransferService.class})
    static class Config {
        private static final String URL = "jdbc:h2:mem:account_reconciliation;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

        @Bean
        DataSource dataSource() throws Exception {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(URL, "sa", "");
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("create table if not exists migration_test_anchor (id bigint primary key)");
            }
            Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .baselineVersion("20260913.3").load();
            flyway.baseline();
            flyway.migrate();
            return dataSource;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setTypeAliasesPackage("com.sky.entity");
            factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:mapper/*.xml"));
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
