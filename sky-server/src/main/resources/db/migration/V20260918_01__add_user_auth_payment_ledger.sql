CREATE TABLE `user_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `refresh_token_hash` VARCHAR(128) NOT NULL,
    `device_id` VARCHAR(128) DEFAULT NULL,
    `expires_at` DATETIME NOT NULL,
    `revoked_at` DATETIME DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_session_refresh_token` (`refresh_token_hash`),
    KEY `idx_user_session_user_expiry` (`user_id`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户刷新会话';

CREATE TABLE `sms_verification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `phone` VARCHAR(32) NOT NULL,
    `code_hash` VARCHAR(128) NOT NULL,
    `purpose` VARCHAR(32) NOT NULL,
    `expires_at` DATETIME NOT NULL,
    `used_at` DATETIME DEFAULT NULL,
    `attempt_count` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_sms_verification_phone_purpose` (`phone`, `purpose`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信验证码';

CREATE TABLE `mock_account` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `account_no` VARCHAR(64) NOT NULL,
    `account_type` VARCHAR(32) NOT NULL,
    `owner_id` BIGINT NOT NULL,
    `available_cent` BIGINT NOT NULL DEFAULT 0,
    `frozen_cent` BIGINT NOT NULL DEFAULT 0,
    `version` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mock_account_no` (`account_no`),
    UNIQUE KEY `uk_mock_account_type_owner` (`account_type`, `owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟资金账户';

CREATE TABLE `fund_transfer` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `transfer_no` VARCHAR(64) NOT NULL,
    `business_key` VARCHAR(128) NOT NULL,
    `transfer_type` VARCHAR(32) NOT NULL,
    `source_account_id` BIGINT NOT NULL,
    `target_account_id` BIGINT NOT NULL,
    `amount_cent` BIGINT NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `completed_at` DATETIME DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_transfer_no` (`transfer_no`),
    UNIQUE KEY `uk_transfer_business_key` (`business_key`),
    KEY `idx_fund_transfer_source_time` (`source_account_id`, `create_time`),
    KEY `idx_fund_transfer_target_time` (`target_account_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金转移';

CREATE TABLE `account_ledger_entry` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `transfer_id` BIGINT NOT NULL,
    `account_id` BIGINT NOT NULL,
    `direction` VARCHAR(16) NOT NULL,
    `amount_cent` BIGINT NOT NULL,
    `balance_after_cent` BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ledger_transfer_account_direction` (`transfer_id`, `account_id`, `direction`),
    KEY `idx_ledger_account_time` (`account_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变账户账本分录';

CREATE TABLE `payment_transaction` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `payment_no` VARCHAR(64) NOT NULL,
    `order_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `channel` VARCHAR(32) NOT NULL,
    `idempotency_key` VARCHAR(128) NOT NULL,
    `amount_cent` BIGINT NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `expires_at` DATETIME NOT NULL,
    `succeeded_at` DATETIME DEFAULT NULL,
    `failure_code` VARCHAR(64) DEFAULT NULL,
    `version` INT NOT NULL DEFAULT 0,
    `success_order_id` BIGINT GENERATED ALWAYS AS (CASE WHEN `status` = 'SUCCEEDED' THEN `order_id` ELSE NULL END),
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_no` (`payment_no`),
    UNIQUE KEY `uk_payment_idempotency` (`idempotency_key`),
    UNIQUE KEY `uk_payment_order_success_guard` (`success_order_id`),
    KEY `idx_payment_order_time` (`order_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付交易';

CREATE TABLE `order_timeline_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `event_no` VARCHAR(64) NOT NULL,
    `order_id` BIGINT NOT NULL,
    `event_type` VARCHAR(32) NOT NULL,
    `operator_type` VARCHAR(32) NOT NULL,
    `operator_id` BIGINT DEFAULT NULL,
    `payload_json` TEXT DEFAULT NULL,
    `event_time` DATETIME NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_timeline_event_no` (`event_no`),
    KEY `idx_order_timeline_order_time` (`order_id`, `event_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单时间轴事件';

CREATE TABLE `user_security_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `event_type` VARCHAR(32) NOT NULL,
    `detail_json` TEXT DEFAULT NULL,
    `ip_address` VARCHAR(64) DEFAULT NULL,
    `user_agent` VARCHAR(512) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_security_audit_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户安全审计';

INSERT INTO `mock_account` (`account_no`, `account_type`, `owner_id`, `available_cent`, `frozen_cent`, `version`)
VALUES ('PLATFORM_PENDING', 'PLATFORM_PENDING', 0, 0, 0, 0),
       ('MERCHANT_DEFAULT', 'MERCHANT', 0, 0, 0, 0);
