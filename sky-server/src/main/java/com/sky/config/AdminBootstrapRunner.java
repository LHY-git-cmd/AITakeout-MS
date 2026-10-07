package com.sky.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 干净数据库首次启动时创建超级管理员。
 * 密码只从环境变量读取并立即使用BCrypt编码，日志不会输出明文。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "sky.bootstrap-admin", name = "enabled",
        havingValue = "true", matchIfMissing = false)
public class AdminBootstrapRunner implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Value("${sky.bootstrap-admin.username:admin}")
    private String username;
    @Value("${sky.bootstrap-admin.password:}")
    private String password;
    @Value("${sky.bootstrap-admin.name:系统管理员}")
    private String name;

    @Override
    public void run(ApplicationArguments args) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from employee", Integer.class);
        if (count != null && count > 0) return;
        if (password == null || password.length() < 8 || password.contains("replace-with")) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD必须配置为至少8位的非占位密码");
        }
        jdbcTemplate.update("""
                insert into employee(name,username,password,phone,sex,id_number,status,role,
                  create_time,update_time,create_user,update_user)
                values(?,?,?,?,?,?,1,'SUPER_ADMIN',now(),now(),1,1)
                """, name, username, passwordEncoder.encode(password),
                "13800000000", "1", "000000000000000000");
        log.info("干净环境超级管理员已创建: username={}", username);
    }
}
