package com.sky.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证阶段三退款、售后和可靠通知结构可以独立升级。 */
class AfterSaleNotificationMigrationIT {
    private static final String URL = "jdbc:h2:mem:phase3_migration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    @Test
    void migrationCreatesRefundAndReliableNotificationTables() throws Exception {
        Flyway flyway = Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration")
                .baselineVersion("20260920.2").load();
        flyway.baseline();
        flyway.migrate();

        assertThat(columns("refund_transaction")).contains("refund_no", "amount_cent", "status", "attempt_count");
        assertThat(columns("after_sale_request")).contains("request_no", "previous_order_status", "refund_no");
        assertThat(indexes("outbox_event")).contains("uk_outbox_business_event");
        assertThat(indexes("user_notification")).contains("uk_notification_business_key");
    }

    private Set<String> columns(String table) throws Exception {
        Set<String> names = new HashSet<>();
        try (var connection = DriverManager.getConnection(URL, "sa", "");
             var rows = connection.getMetaData().getColumns(null, null, table, null)) {
            while (rows.next()) names.add(rows.getString("COLUMN_NAME"));
        }
        return names;
    }

    private Set<String> indexes(String table) throws Exception {
        Set<String> names = new HashSet<>();
        try (var connection = DriverManager.getConnection(URL, "sa", "");
             var rows = connection.getMetaData().getIndexInfo(null, null, table, false, false)) {
            while (rows.next()) if (rows.getString("INDEX_NAME") != null) {
                names.add(rows.getString("INDEX_NAME").replaceFirst("_INDEX_[A-Z0-9]+$", ""));
            }
        }
        return names;
    }
}
