package com.sky.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

/** 从已执行V20260922_02的状态验证双域表迁移和历史管理数据保留。 */
class AgentStorageSplitMigrationTest {
    private static final String URL =
            "jdbc:h2:mem:agent_storage_split;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    @Test
    void migratesExistingAdminRowsAndCreatesEmptyUserDomain() throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, "sa", "")) {
            createPostV02Schema(connection);
            connection.createStatement().execute("""
                    insert into agent_session(id, user_id, actor_type, actor_id)
                    values (1, 42, 'ADMIN', 42)
                    """);
            connection.createStatement().execute("""
                    insert into agent_task(id, task_id, session_id, user_id, actor_type, actor_id,
                                           status, create_time)
                    values (1, 'task-admin', 'session-admin', 42, 'ADMIN', 42, 2, current_timestamp)
                    """);
        }

        Flyway flyway = Flyway.configure().dataSource(URL, "sa", "")
                .locations("classpath:db/migration")
                .baselineVersion("20260922.2")
                .load();
        flyway.baseline();
        flyway.migrate();

        try (Connection connection = DriverManager.getConnection(URL, "sa", "")) {
            assertThat(count(connection, "admin_agent_session")).isEqualTo(1);
            assertThat(count(connection, "admin_agent_task")).isEqualTo(1);
            assertThat(count(connection, "user_agent_session")).isZero();
            assertThat(count(connection, "user_agent_task")).isZero();
            assertThat(count(connection, "agent_task_overview")).isEqualTo(1);
            assertThat(tableExists(connection, "agent_session")).isFalse();
            assertThat(tableExists(connection, "agent_task")).isFalse();
            // 软删除记录保留审计，但不能阻止同一文件重新上传为新的逻辑文档。
            connection.createStatement().execute("""
                    insert into user_agent_knowledge_document
                      (document_id,kb_id,file_name,file_type,file_url,file_hash,version,status,
                       chunk_count,create_user,category,lifecycle_status,review_status)
                    values ('deleted-doc','kb-1','a.txt','txt','deleted/a.txt','same-hash',1,6,
                            0,1,'GENERAL','DELETED','APPROVED')
                    """);
            connection.createStatement().execute("""
                    insert into user_agent_knowledge_document
                      (document_id,kb_id,file_name,file_type,file_url,file_hash,version,status,
                       chunk_count,create_user,category,lifecycle_status,review_status)
                    values ('new-doc','kb-1','a.txt','txt','active/a.txt','same-hash',1,0,
                            0,1,'GENERAL','DRAFT','APPROVED')
                    """);
            assertThat(count(connection, "user_agent_knowledge_document")).isEqualTo(2);
        }
    }

    /** 构造线上已经完成V20260922_02后的最小兼容结构。 */
    private void createPostV02Schema(Connection connection) throws Exception {
        var statement = connection.createStatement();
        statement.execute("create table agent_session(id bigint primary key, user_id bigint not null, actor_type varchar(16), actor_id bigint)");
        statement.execute("""
                create table agent_task(
                    id bigint primary key, task_id varchar(64), session_id varchar(64),
                    user_id bigint not null, actor_type varchar(16), actor_id bigint,
                    status int, create_time timestamp, finished_at timestamp)
                """);
        statement.execute("create table agent_message(id bigint primary key)");
        statement.execute("create table agent_session_summary(id bigint primary key)");
        statement.execute("create table agent_event(id bigint primary key)");
        statement.execute("create table agent_message_citation(id bigint primary key)");
        statement.execute("create table agent_tool_confirmation(id bigint primary key, actor_type varchar(16))");
        statement.execute("create table agent_tool_audit(id bigint primary key, actor_type varchar(16))");
        statement.execute("create table agent_knowledge_base(id bigint primary key)");
        statement.execute("create table agent_knowledge_document(id bigint primary key)");
        statement.execute("create table agent_knowledge_index_task(id bigint primary key)");
    }

    private long count(Connection connection, String table) throws Exception {
        try (var result = connection.createStatement().executeQuery("select count(*) from " + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (var tables = connection.getMetaData().getTables(null, null, table, new String[]{"TABLE"})) {
            return tables.next();
        }
    }
}
