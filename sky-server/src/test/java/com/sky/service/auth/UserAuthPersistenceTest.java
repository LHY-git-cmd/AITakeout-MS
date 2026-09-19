package com.sky.service.auth;

import com.sky.auth.AuthClientContext;
import com.sky.dto.UserPasswordLoginDTO;
import com.sky.entity.UserSession;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Database-backed transaction and concurrency coverage for authentication guards. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserAuthPersistenceTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.druid.url", () ->
                "jdbc:h2:mem:auth_persistence;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    }

    @Autowired UserAuthService service;
    @Autowired UserSessionMapper sessionMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MockSmsGateway smsGateway;

    @BeforeEach
    void prepareSchema() {
        jdbc.execute("drop table if exists auth_sms_cooldown");
        jdbc.execute("drop table if exists user_security_audit");
        jdbc.execute("drop table if exists user_session");
        jdbc.execute("drop table if exists sms_verification");
        jdbc.execute("drop table if exists `user`");
        jdbc.execute("create table `user` (id bigint auto_increment primary key, openid varchar(64), name varchar(32), "
                + "phone varchar(32) unique, password varchar(128), sex varchar(2), id_number varchar(32), "
                + "avatar varchar(500), create_time datetime)");
        jdbc.execute("create table user_security_audit (id bigint auto_increment primary key, user_id bigint not null, "
                + "event_type varchar(32) not null, detail_json text, ip_address varchar(64), user_agent varchar(512), create_time datetime not null)");
        jdbc.execute("create table user_session (id bigint auto_increment primary key, user_id bigint not null, "
                + "refresh_token_hash varchar(128) unique not null, device_id varchar(128), expires_at datetime not null, "
                + "revoked_at datetime, create_time datetime not null)");
        jdbc.execute("create table sms_verification (id bigint auto_increment primary key, phone varchar(32) not null, "
                + "code_hash varchar(128) not null, purpose varchar(32) not null, expires_at datetime not null, "
                + "used_at datetime, attempt_count int not null, create_time datetime not null)");
        jdbc.execute("create table auth_sms_cooldown (phone varchar(32) not null, purpose varchar(32) not null, "
                + "next_allowed_at datetime not null, primary key(phone, purpose))");
    }

    @Test
    void fiveRolledBackLoginRequestsPersistFailuresAndBlockSixth() {
        insertUser();
        UserPasswordLoginDTO wrong = new UserPasswordLoginDTO("13800138000", "wrong-pass");
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login(wrong, client())).isInstanceOf(LoginFailedException.class);
        }

        assertThat(jdbc.queryForObject("select count(*) from user_security_audit where event_type='LOGIN_FAILURE'", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from user_security_audit where event_type='LOGIN_LOCKED'", Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(() -> service.login(new UserPasswordLoginDTO("13800138000", "StrongPass8"), client()))
                .isInstanceOf(LoginFailedException.class).hasMessageContaining("15分钟");
    }

    @Test
    void concurrentSameSecondFailuresAreSerializedAndExactlyOneLockIsCreated() throws Exception {
        insertUser();
        var pool = Executors.newFixedThreadPool(5);
        CountDownLatch ready = new CountDownLatch(5);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> calls = new ArrayList<>();
        for (int i = 0; i < 5; i++) calls.add(pool.submit(() -> {
            ready.countDown(); start.await();
            try { service.login(new UserPasswordLoginDTO("13800138000", "wrong-pass"), client()); }
            catch (LoginFailedException ignored) { }
            return null;
        }));
        ready.await(); start.countDown();
        for (Future<?> call : calls) call.get();
        pool.shutdown();

        assertThat(jdbc.queryForObject("select count(*) from user_security_audit where event_type='LOGIN_FAILURE'", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from user_security_audit where event_type='LOGIN_LOCKED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void concurrentSmsRequestsReserveCooldownBeforeOnlyOneGatewayDispatch() throws Exception {
        var pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) calls.add(pool.submit(() -> {
            start.await();
            try { service.sendSms("13800138000", "register"); }
            catch (LoginFailedException ignored) { }
            return null;
        }));
        start.countDown();
        for (Future<?> call : calls) call.get();
        pool.shutdown();

        assertThat(jdbc.queryForObject("select count(*) from sms_verification", Integer.class)).isEqualTo(1);
        assertThat(smsGateway.codeFor("13800138000", "register")).isEqualTo("246810");
        assertThat(smsGateway.sendCountFor("13800138000", "register")).isEqualTo(1);
    }

    @Test
    void logoutAndRefreshCannotBothSucceedOrLeaveSessionAfterSuccessfulLogout() throws Exception {
        insertUser();
        String raw = "raw-refresh";
        sessionMapper.insert(UserSession.builder().userId(1L).refreshTokenHash(sha256(raw)).deviceId("device-1")
                .expiresAt(LocalDateTime.now().plusDays(1)).createTime(LocalDateTime.now()).build());
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> refresh = pool.submit(() -> run(() -> service.refresh(raw), start));
        Future<Boolean> logout = pool.submit(() -> run(() -> service.logout(raw, false), start));
        start.countDown();
        boolean refreshSucceeded = refresh.get();
        boolean logoutSucceeded = logout.get();
        pool.shutdown();

        assertThat(logoutSucceeded).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from user_session where user_id=1 and device_id='device-1' and revoked_at is null", Integer.class))
                .isZero();
    }

    private boolean run(ThrowingCall call, CountDownLatch start) throws Exception {
        start.await();
        try { call.run(); return true; } catch (LoginFailedException ex) { return false; }
    }

    private void insertUser() {
        jdbc.update("insert into `user` (id, name, phone, password, create_time) values (1, '小苍', ?, ?, now())",
                "13800138000", passwordEncoder.encode("StrongPass8"));
    }

    private AuthClientContext client() { return new AuthClientContext("127.0.0.1", "test", "device-1"); }

    private static String sha256(String input) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8)));
    }

    @FunctionalInterface interface ThrowingCall { void run() throws Exception; }
}
