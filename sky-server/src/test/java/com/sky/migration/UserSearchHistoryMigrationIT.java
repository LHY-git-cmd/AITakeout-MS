package com.sky.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证阶段四搜索历史结构可从阶段三版本独立升级。 */
class UserSearchHistoryMigrationIT {
    private static final String URL = "jdbc:h2:mem:phase4_search_migration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    @Test
    void migrationCreatesVersionedUserSearchHistory() throws Exception {
        Flyway flyway = Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration")
                .baselineVersion("20260921.1").load();
        flyway.baseline();
        flyway.migrate();

        assertThat(columns()).contains("user_id", "keyword", "normalized_keyword", "update_time");
        assertThat(indexes()).contains("uk_search_history_user_keyword", "idx_search_history_user_time");
    }

    private Set<String> columns() throws Exception {
        Set<String> names = new HashSet<>();
        try (var connection = DriverManager.getConnection(URL, "sa", "");
             var rows = connection.getMetaData().getColumns(null, null, "user_search_history", null)) {
            while (rows.next()) names.add(rows.getString("COLUMN_NAME"));
        }
        return names;
    }

    private Set<String> indexes() throws Exception {
        Set<String> names = new HashSet<>();
        try (var connection = DriverManager.getConnection(URL, "sa", "");
             var rows = connection.getMetaData().getIndexInfo(null, null, "user_search_history", false, false)) {
            while (rows.next()) if (rows.getString("INDEX_NAME") != null) {
                names.add(rows.getString("INDEX_NAME").replaceFirst("_INDEX_[A-Z0-9]+$", ""));
            }
        }
        return names;
    }
}
