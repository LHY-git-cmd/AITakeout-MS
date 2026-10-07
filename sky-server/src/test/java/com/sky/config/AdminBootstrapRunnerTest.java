package com.sky.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** 干净数据库超级管理员引导测试。 */
class AdminBootstrapRunnerTest {
    @Test
    void createsOnlyOneBcryptSuperAdmin() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:bootstrap;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("""
                create table employee(id bigint auto_increment primary key,name varchar(32),username varchar(32),
                  password varchar(100),phone varchar(11),sex varchar(2),id_number varchar(18),status int,
                  role varchar(32),create_time datetime,update_time datetime,create_user bigint,update_user bigint)
                """);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(jdbc, new BCryptPasswordEncoder());
        ReflectionTestUtils.setField(runner, "username", "admin");
        ReflectionTestUtils.setField(runner, "password", "StrongDemoPassword!2026");
        ReflectionTestUtils.setField(runner, "name", "系统管理员");

        runner.run(new DefaultApplicationArguments());
        runner.run(new DefaultApplicationArguments());

        assertThat(jdbc.queryForObject("select count(*) from employee", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select role from employee", String.class)).isEqualTo("SUPER_ADMIN");
        assertThat(jdbc.queryForObject("select password from employee", String.class)).startsWith("$2");
    }
}
