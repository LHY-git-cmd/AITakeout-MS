package com.sky.service.payment;

import com.sky.entity.MockAccount;
import com.sky.entity.Orders;
import com.sky.mapper.MockAccountMapper;
import com.sky.service.payment.model.PaymentModels.PaymentStatus;
import com.sky.service.payment.model.PaymentModels.PaymentView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 数据库级支付幂等、账本平衡和成功/超时竞争测试。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentConcurrencyIT {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.druid.url", () ->
                "jdbc:h2:mem:payment_concurrency;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        registry.add("sky.payment.mock.delay-ms", () -> "60000");
    }

    @Autowired PaymentApplicationService service;
    @Autowired MockPaymentGateway gateway;
    @Autowired MockAccountMapper accountMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void prepareSchema() {
        for (String table : new String[]{"order_timeline_event", "account_ledger_entry", "fund_transfer",
                "payment_transaction", "mock_account", "orders"}) {
            jdbc.execute("drop table if exists " + table);
        }
        jdbc.execute("create table orders (id bigint primary key, number varchar(64), status int, user_id bigint, "
                + "order_time datetime, checkout_time datetime, pay_method int, pay_status int, amount decimal(10,2), "
                + "cancel_reason varchar(255), cancel_time datetime)");
        jdbc.execute("create table mock_account (id bigint auto_increment primary key, account_no varchar(64) unique, "
                + "account_type varchar(32), owner_id bigint, available_cent bigint, frozen_cent bigint, version int, "
                + "create_time datetime, update_time datetime, unique(account_type, owner_id))");
        jdbc.execute("create table payment_transaction (id bigint auto_increment primary key, payment_no varchar(64) unique, "
                + "order_id bigint, user_id bigint, channel varchar(32), idempotency_key varchar(128) unique, amount_cent bigint, "
                + "status varchar(32), gateway_trade_no varchar(64), callback_event_id varchar(64), expires_at datetime, "
                + "succeeded_at datetime, failure_code varchar(64), version int, "
                + "success_order_id bigint generated always as (case when status='SUCCEEDED' then order_id else null end), "
                + "create_time datetime, update_time datetime, unique(success_order_id), "
                + "unique(gateway_trade_no), unique(callback_event_id))");
        jdbc.execute("create table fund_transfer (id bigint auto_increment primary key, transfer_no varchar(64) unique, "
                + "business_key varchar(128) unique, transfer_type varchar(32), source_account_id bigint, target_account_id bigint, "
                + "amount_cent bigint, status varchar(32), completed_at datetime, create_time datetime, update_time datetime, "
                + "operator_id bigint, reason varchar(255), adjusted_account_id bigint, balance_before_cent bigint, balance_after_cent bigint)");
        jdbc.execute("create table account_ledger_entry (id bigint auto_increment primary key, transfer_id bigint, account_id bigint, "
                + "direction varchar(16), amount_cent bigint, balance_after_cent bigint, create_time datetime, "
                + "unique(transfer_id, account_id, direction))");
        jdbc.execute("create table order_timeline_event (id bigint auto_increment primary key, event_no varchar(64) unique, "
                + "order_id bigint, event_type varchar(32), business_no varchar(64), display_message varchar(255), "
                + "operator_type varchar(32), operator_id bigint, payload_json text, event_time datetime, create_time datetime)");
        insertAccount("USER-7", "USER", 7L, 100_000L);
        insertAccount("PLATFORM_PENDING", "PLATFORM_PENDING", 0L, 0L);
        jdbc.update("insert into orders(id,number,status,user_id,order_time,pay_status,amount) values(1,'O-1',1,7,?,0,50.00)",
                LocalDateTime.now());
    }

    @Test
    void duplicateGatewaySuccessDebitsExactlyOnce() {
        PaymentView payment = service.create(7L, 1L, "request-1");
        gateway.completeSuccessfully(payment.paymentNo());

        service.reconcile(payment.paymentNo());
        service.reconcile(payment.paymentNo());

        assertThat(service.query(7L, payment.paymentNo()).status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject("select count(*) from fund_transfer where business_key=?", Integer.class,
                "PAYMENT:" + payment.paymentNo())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from account_ledger_entry", Integer.class)).isEqualTo(2);
        assertThat(accountMapper.findByTypeAndOwner("USER", 7L).getAvailableCent()).isEqualTo(95_000L);
    }

    @Test
    void insufficientBalanceFailsWithoutLedgerEntries() {
        jdbc.update("update mock_account set available_cent=100 where account_type='USER' and owner_id=7");
        PaymentView payment = service.create(7L, 1L, "request-low-balance");
        gateway.completeSuccessfully(payment.paymentNo());

        service.reconcile(payment.paymentNo());

        assertThat(service.query(7L, payment.paymentNo()).status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(jdbc.queryForObject("select count(*) from account_ledger_entry", Integer.class)).isZero();
    }

    @Test
    void expiryAndSuccessRaceEndsInOnlyOneTerminalState() throws Exception {
        PaymentView payment = service.create(7L, 1L, "request-race");
        gateway.completeSuccessfully(payment.paymentNo());
        jdbc.update("update payment_transaction set expires_at=? where payment_no=?",
                LocalDateTime.now().minusSeconds(1), payment.paymentNo());
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        var success = pool.submit(() -> { start.await(); service.reconcile(payment.paymentNo()); return null; });
        var expiry = pool.submit(() -> { start.await(); service.expireBatch(20); return null; });
        start.countDown(); success.get(); expiry.get(); pool.shutdown();

        PaymentStatus status = service.query(7L, payment.paymentNo()).status();
        assertThat(status).isIn(PaymentStatus.SUCCEEDED, PaymentStatus.CLOSED);
        assertThat(jdbc.queryForObject("select count(*) from fund_transfer", Integer.class)).isLessThanOrEqualTo(1);
        if (status == PaymentStatus.CLOSED) {
            gateway.completeSuccessfully(payment.paymentNo());
            service.reconcile(payment.paymentNo());
            assertThat(service.query(7L, payment.paymentNo()).status()).isEqualTo(PaymentStatus.CLOSED);
        }
    }

    @Test
    void closedPaymentRejectsLateGatewaySuccess() {
        PaymentView payment = service.create(7L, 1L, "request-expired");
        jdbc.update("update payment_transaction set expires_at=? where payment_no=?",
                LocalDateTime.now().minusSeconds(1), payment.paymentNo());

        service.expireBatch(20);
        gateway.completeSuccessfully(payment.paymentNo());
        service.reconcile(payment.paymentNo());

        assertThat(service.query(7L, payment.paymentNo()).status()).isEqualTo(PaymentStatus.CLOSED);
        assertThat(jdbc.queryForObject("select count(*) from fund_transfer", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select status from orders where id=1", Integer.class))
                .isEqualTo(Orders.CANCELLED);
    }

    @Test
    void idempotentCreateReturnsOriginalProcessingPayment() {
        PaymentView first = service.create(7L, 1L, "same-request");
        PaymentView replay = service.create(7L, 1L, "same-request");

        assertThat(replay.paymentNo()).isEqualTo(first.paymentNo());
        assertThat(jdbc.queryForObject("select count(*) from payment_transaction", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> service.query(8L, first.paymentNo()))
                .isInstanceOf(com.sky.exception.OrderBusinessException.class);
    }

    private void insertAccount(String accountNo, String type, long ownerId, long amount) {
        accountMapper.insert(MockAccount.builder().accountNo(accountNo).accountType(type).ownerId(ownerId)
                .availableCent(amount).frozenCent(0L).version(0).createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now()).build());
    }
}
