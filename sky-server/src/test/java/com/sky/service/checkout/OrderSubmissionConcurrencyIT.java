package com.sky.service.checkout;

import com.sky.mapper.OrderSubmissionMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(OrderSubmissionConcurrencyIT.Config.class)
class OrderSubmissionConcurrencyIT {
    @org.springframework.beans.factory.annotation.Autowired private SubmitHarness harness;
    @org.springframework.beans.factory.annotation.Autowired private JdbcTemplate jdbc;

    @Test
    void sameIdempotencyKeyCreatesOneOrderReference() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var calls = List.of(0, 1).stream().map(ignored -> executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return harness.submit(7L, "SUBMIT-1", "same-request");
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(calls.get(0).get(10, TimeUnit.SECONDS)).isEqualTo(77L);
            assertThat(calls.get(1).get(10, TimeUnit.SECONDS)).isEqualTo(77L);
        }
        assertThat(harness.createdCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from order_submission", Integer.class)).isEqualTo(1);
    }

    static class SubmitHarness {
        private final OrderSubmissionService service;
        private final AtomicInteger created = new AtomicInteger();
        SubmitHarness(OrderSubmissionService service) { this.service = service; }

        @Transactional
        public long submit(long userId, String key, String hash) {
            var reservation = service.reserve(userId, key, hash);
            if (!reservation.created()) return reservation.submission().getOrderId();
            created.incrementAndGet();
            try { Thread.sleep(Duration.ofMillis(80)); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            service.attachOrder(reservation.submission().getId(), 77L);
            return 77L;
        }

        public int createdCount() { return created.get(); }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = OrderSubmissionMapper.class)
    static class Config {
        @Bean DataSource dataSource() throws Exception {
            var dataSource = new DriverManagerDataSource(
                    "jdbc:h2:mem:order_submission_concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("create table order_submission(id bigint auto_increment primary key, user_id bigint not null, "
                        + "idempotency_key varchar(80) not null, order_id bigint, request_hash varchar(64) not null, "
                        + "create_time datetime not null, constraint uk_order_submit_user_key unique(user_id,idempotency_key))");
            }
            return dataSource;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setTypeAliasesPackage("com.sky.entity");
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/OrderSubmissionMapper.xml"));
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean OrderSubmissionService service(OrderSubmissionMapper mapper) { return new OrderSubmissionService(mapper); }
        @Bean SubmitHarness harness(OrderSubmissionService service) { return new SubmitHarness(service); }
    }
}
