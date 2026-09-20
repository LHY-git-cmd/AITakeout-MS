package com.sky.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutDeliveryMigrationIT {
    private static final String URL = "jdbc:h2:mem:checkout_migration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    @Test
    void migrationAddsAuthoritativeCheckoutFields() throws Exception {
        try (var connection = DriverManager.getConnection(URL, "sa", ""); var statement = connection.createStatement()) {
            statement.execute("create table orders(id bigint auto_increment primary key, amount decimal(10,2) not null, pack_amount int)");
            statement.execute("insert into orders(amount, pack_amount) values (12.34, 2)");
            statement.execute("create table address_book(id bigint auto_increment primary key)");
        }
        Flyway flyway = Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration")
                .baselineVersion("20260920.1").load();
        flyway.baseline();
        flyway.migrate();

        assertThat(columns("orders")).contains("amount_cent", "delivery_fee_cent", "pricing_rule_version", "expires_at");
        assertThat(columns("address_book")).contains("latitude", "longitude", "geocode_status", "deliverable");
        assertThat(uniqueIndexes("order_submission")).contains("uk_order_submit_user_key");
        try (var connection = DriverManager.getConnection(URL, "sa", ""); var statement = connection.createStatement();
             var rows = statement.executeQuery("select amount_cent from orders")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong(1)).isEqualTo(1234L);
        }
    }

    private Set<String> columns(String table) throws Exception {
        Set<String> names = new HashSet<>();
        try (var connection = DriverManager.getConnection(URL, "sa", "");
             var rows = connection.getMetaData().getColumns(null, null, table, null)) {
            while (rows.next()) names.add(rows.getString("COLUMN_NAME"));
        }
        return names;
    }

    private Set<String> uniqueIndexes(String table) throws Exception {
        Set<String> names = new HashSet<>();
        try (var connection = DriverManager.getConnection(URL, "sa", "");
             var rows = connection.getMetaData().getIndexInfo(null, null, table, true, false)) {
            while (rows.next()) if (rows.getString("INDEX_NAME") != null) {
                names.add(rows.getString("INDEX_NAME").replaceFirst("_INDEX_\\d+$", ""));
            }
        }
        return names;
    }
}
