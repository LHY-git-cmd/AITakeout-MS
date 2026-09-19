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
        assertThat(transferMapper.findByBusinessKey("GRANT:grant-cap-over")).isNull();
    }

    @Test
    void adminAdjustmentPersistsAuditAndIdempotentReplayReturnsOriginalResult() {
        accountService.openUserAccount(9L, 50_000L);

        AdjustmentResult first = accountService.adjust(101L, 9L, 12_345L, "客服补偿", "adjust-1");
        AdjustmentResult replay = accountService.adjust(101L, 9L, 12_345L, "客服补偿", "adjust-1");
        FundTransfer persisted = transferMapper.findByBusinessKey("ADMIN_ADJUSTMENT:adjust-1");

        assertThat(replay.transferId()).isEqualTo(first.transferId());
        assertThat(replay.replayed()).isTrue();
        assertThat(accountService.getUserAccount(9L).getAvailableCent()).isEqualTo(62_345L);
        assertThat(persisted.getOperatorId()).isEqualTo(101L);
        assertThat(persisted.getReason()).isEqualTo("客服补偿");
        assertThat(persisted.getAdjustedAccountId()).isEqualTo(first.accountId());
        assertThat(persisted.getBalanceBeforeCent()).isEqualTo(50_000L);
        assertThat(persisted.getBalanceAfterCent()).isEqualTo(62_345L);
        assertThat(ledgerMapper.findByTransferId(first.transferId())).hasSize(2);
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
