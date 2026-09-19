package com.sky.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserPaymentMigrationIT {

    private static final String JDBC_URL = "jdbc:h2:mem:user_payment_migration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    @Test
    void migrationCreatesPaymentAndLedgerConstraints() throws SQLException {
        migrate();

        assertThat(columns("payment_transaction"))
                .contains("payment_no", "amount_cent", "expires_at", "version");
        assertThat(indexes("payment_transaction"))
                .contains("uk_payment_idempotency", "uk_payment_order_success_guard");
        assertThat(columns("mock_account"))
                .contains("available_cent", "frozen_cent", "version");
        assertThat(indexes("fund_transfer"))
                .contains("uk_transfer_business_key");
        assertThat(indexes("account_ledger_entry"))
                .contains("uk_ledger_transfer_account_direction");
    }

    private void migrate() {
        createBaselineAnchor();
        Flyway flyway = Flyway.configure()
                .dataSource(JDBC_URL, "sa", "")
                .locations("classpath:db/migration")
                .baselineVersion("20260913.3")
                .load()
                ;
        flyway.baseline();
        flyway.migrate();
    }

    private void createBaselineAnchor() {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE migration_test_anchor (id BIGINT PRIMARY KEY)");
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Set<String> columns(String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             var resultSet = connection.getMetaData().getColumns(null, null, tableName, null)) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }

    private Set<String> indexes(String tableName) throws SQLException {
        Set<String> indexes = new HashSet<>();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             var resultSet = connection.getMetaData().getIndexInfo(null, null, tableName, false, false)) {
            while (resultSet.next()) {
                String indexName = resultSet.getString("INDEX_NAME");
                if (indexName != null) {
                    indexes.add(indexName.replaceFirst("_INDEX_\\d+$", ""));
                }
            }
        }
        return indexes;
    }
}
