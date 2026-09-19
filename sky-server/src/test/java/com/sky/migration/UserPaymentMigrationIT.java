package com.sky.migration;

import com.sky.entity.OrderTimelineEvent;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPaymentMigrationIT {

    private static final String JDBC_URL = "jdbc:h2:mem:user_payment_migration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    @Test
    void migrationCreatesUserPaymentAndLedgerSchemaContracts() throws SQLException {
        migrate();

        assertThat(tables()).contains(
                "user_session", "sms_verification", "mock_account", "fund_transfer",
                "account_ledger_entry", "payment_transaction", "order_timeline_event", "user_security_audit",
                "auth_sms_cooldown");
        assertThat(columnTypes("mock_account"))
                .containsEntry("available_cent", Types.BIGINT)
                .containsEntry("frozen_cent", Types.BIGINT);
        assertThat(columnTypes("fund_transfer")).containsEntry("amount_cent", Types.BIGINT);
        assertThat(columnTypes("account_ledger_entry"))
                .containsEntry("amount_cent", Types.BIGINT)
                .containsEntry("balance_after_cent", Types.BIGINT);
        assertThat(columnTypes("payment_transaction")).containsEntry("amount_cent", Types.BIGINT);

        assertThat(uniqueIndexes("payment_transaction"))
                .contains("uk_payment_no", "uk_payment_idempotency", "uk_payment_order_success_guard");
        assertThat(uniqueIndexes("fund_transfer"))
                .contains("uk_transfer_no", "uk_transfer_business_key");
        assertThat(uniqueIndexes("account_ledger_entry"))
                .contains("uk_ledger_transfer_account_direction");
        assertThat(systemAccountTypes())
                .containsEntry("PLATFORM_PENDING", "PLATFORM_PENDING")
                .containsEntry("MERCHANT_DEFAULT", "MERCHANT");

        insertPayment("PAY-FAILED-1", "IDEMP-FAILED-1", "FAILED");
        insertPayment("PAY-FAILED-2", "IDEMP-FAILED-2", "FAILED");
        insertPayment("PAY-SUCCEEDED-1", "IDEMP-SUCCEEDED-1", "SUCCEEDED");
        assertThatThrownBy(() -> insertPayment("PAY-SUCCEEDED-2", "IDEMP-SUCCEEDED-2", "SUCCEEDED"))
                .isInstanceOf(SQLException.class);

        assertThat(columns("order_timeline_event")).contains("display_message", "business_no");
        assertThat(Arrays.stream(OrderTimelineEvent.class.getDeclaredFields()).map(Field::getName))
                .contains("displayMessage", "businessNo");
    }

    private void migrate() {
        createBaselineAnchor();
        Flyway flyway = Flyway.configure()
                .dataSource(JDBC_URL, "sa", "")
                .locations("classpath:db/migration")
                .baselineVersion("20260913.3")
                .load();
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

    private Set<String> tables() throws SQLException {
        Set<String> tables = new HashSet<>();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             var resultSet = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private Set<String> columns(String tableName) throws SQLException {
        return columnTypes(tableName).keySet();
    }

    private Map<String, Integer> columnTypes(String tableName) throws SQLException {
        Map<String, Integer> columns = new HashMap<>();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             var resultSet = connection.getMetaData().getColumns(null, null, tableName, null)) {
            while (resultSet.next()) {
                columns.put(resultSet.getString("COLUMN_NAME"), resultSet.getInt("DATA_TYPE"));
            }
        }
        return columns;
    }

    private Set<String> uniqueIndexes(String tableName) throws SQLException {
        Set<String> indexes = new HashSet<>();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             var resultSet = connection.getMetaData().getIndexInfo(null, null, tableName, true, false)) {
            while (resultSet.next()) {
                String indexName = resultSet.getString("INDEX_NAME");
                if (indexName != null) {
                    indexes.add(indexName.replaceFirst("_INDEX_\\d+$", ""));
                }
            }
        }
        return indexes;
    }

    private Map<String, String> systemAccountTypes() throws SQLException {
        Map<String, String> accountTypes = new HashMap<>();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT account_no, account_type FROM mock_account")) {
            while (resultSet.next()) {
                accountTypes.put(resultSet.getString("account_no"), resultSet.getString("account_type"));
            }
        }
        return accountTypes;
    }

    private void insertPayment(String paymentNo, String idempotencyKey, String status) throws SQLException {
        String sql = "INSERT INTO payment_transaction "
                + "(payment_no, order_id, user_id, channel, idempotency_key, amount_cent, status, expires_at, version) "
                + "VALUES (?, 1001, 2001, 'MOCK', ?, 500, ?, ?, 0)";
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, paymentNo);
            statement.setString(2, idempotencyKey);
            statement.setString(3, status);
            statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.of(2026, 9, 18, 0, 0)));
            statement.executeUpdate();
        }
    }
}
