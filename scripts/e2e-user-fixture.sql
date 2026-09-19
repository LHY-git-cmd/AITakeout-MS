-- 用户端移动 E2E 的最小独立数据夹具：创建核心业务表并提供一件可购买商品。
SET NAMES utf8mb4;

CREATE TABLE user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  openid VARCHAR(45) DEFAULT NULL,
  name VARCHAR(32) DEFAULT NULL,
  phone VARCHAR(11) DEFAULT NULL,
  password VARCHAR(100) DEFAULT NULL,
  sex VARCHAR(2) DEFAULT NULL,
  id_number VARCHAR(18) DEFAULT NULL,
  avatar VARCHAR(500) DEFAULT NULL,
  create_time DATETIME DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE address_book (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  consignee VARCHAR(50) DEFAULT NULL,
  sex VARCHAR(2) DEFAULT NULL,
  phone VARCHAR(11) NOT NULL,
  province_code VARCHAR(12) DEFAULT NULL,
  province_name VARCHAR(32) DEFAULT NULL,
  city_code VARCHAR(12) DEFAULT NULL,
  city_name VARCHAR(32) DEFAULT NULL,
  district_code VARCHAR(12) DEFAULT NULL,
  district_name VARCHAR(32) DEFAULT NULL,
  detail VARCHAR(200) DEFAULT NULL,
  label VARCHAR(100) DEFAULT NULL,
  is_default TINYINT(1) NOT NULL DEFAULT 0,
  latitude DECIMAL(10,7) DEFAULT NULL,
  longitude DECIMAL(10,7) DEFAULT NULL,
  geocode_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  map_provider VARCHAR(16) DEFAULT NULL,
  distance_meters INT DEFAULT NULL,
  deliverable TINYINT(1) DEFAULT NULL,
  validation_message VARCHAR(255) DEFAULT NULL,
  validated_at DATETIME DEFAULT NULL,
  delivery_rule_version VARCHAR(32) DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE category (
  id BIGINT NOT NULL AUTO_INCREMENT,
  type INT DEFAULT NULL,
  name VARCHAR(32) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  status INT DEFAULT NULL,
  create_time DATETIME DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  create_user BIGINT DEFAULT NULL,
  update_user BIGINT DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY idx_category_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dish (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(32) NOT NULL,
  category_id BIGINT NOT NULL,
  price DECIMAL(10,2) DEFAULT NULL,
  image VARCHAR(255) DEFAULT NULL,
  description VARCHAR(255) DEFAULT NULL,
  status INT DEFAULT 1,
  create_time DATETIME DEFAULT NULL,
  update_time DATETIME DEFAULT NULL,
  create_user BIGINT DEFAULT NULL,
  update_user BIGINT DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY idx_dish_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dish_flavor (
  id BIGINT NOT NULL AUTO_INCREMENT,
  dish_id BIGINT NOT NULL,
  name VARCHAR(32) DEFAULT NULL,
  value VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE shopping_cart (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(32) DEFAULT NULL,
  image VARCHAR(255) DEFAULT NULL,
  user_id BIGINT NOT NULL,
  dish_id BIGINT DEFAULT NULL,
  setmeal_id BIGINT DEFAULT NULL,
  dish_flavor VARCHAR(50) DEFAULT NULL,
  number INT NOT NULL DEFAULT 1,
  amount DECIMAL(10,2) NOT NULL,
  create_time DATETIME DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE orders (
  id BIGINT NOT NULL AUTO_INCREMENT,
  number VARCHAR(50) DEFAULT NULL,
  status INT NOT NULL DEFAULT 1,
  user_id BIGINT NOT NULL,
  address_book_id BIGINT NOT NULL,
  order_time DATETIME NOT NULL,
  checkout_time DATETIME DEFAULT NULL,
  pay_method INT NOT NULL DEFAULT 1,
  pay_status TINYINT NOT NULL DEFAULT 0,
  amount DECIMAL(10,2) NOT NULL,
  remark VARCHAR(100) DEFAULT NULL,
  phone VARCHAR(11) DEFAULT NULL,
  address VARCHAR(255) DEFAULT NULL,
  user_name VARCHAR(32) DEFAULT NULL,
  consignee VARCHAR(32) DEFAULT NULL,
  cancel_reason VARCHAR(255) DEFAULT NULL,
  rejection_reason VARCHAR(255) DEFAULT NULL,
  cancel_time DATETIME DEFAULT NULL,
  estimated_delivery_time DATETIME DEFAULT NULL,
  delivery_status TINYINT(1) NOT NULL DEFAULT 1,
  delivery_time DATETIME DEFAULT NULL,
  pack_amount INT DEFAULT NULL,
  tableware_number INT DEFAULT NULL,
  tableware_status TINYINT(1) NOT NULL DEFAULT 1,
  goods_amount_cent BIGINT NOT NULL DEFAULT 0,
  pack_amount_cent BIGINT NOT NULL DEFAULT 0,
  delivery_fee_cent BIGINT NOT NULL DEFAULT 0,
  discount_amount_cent BIGINT NOT NULL DEFAULT 0,
  amount_cent BIGINT NOT NULL DEFAULT 0,
  delivery_distance_meters INT DEFAULT NULL,
  map_provider VARCHAR(16) DEFAULT NULL,
  delivery_mode VARCHAR(16) NOT NULL DEFAULT 'IMMEDIATE',
  delivery_slot_start DATETIME DEFAULT NULL,
  delivery_slot_end DATETIME DEFAULT NULL,
  address_latitude DECIMAL(10,7) DEFAULT NULL,
  address_longitude DECIMAL(10,7) DEFAULT NULL,
  expires_at DATETIME DEFAULT NULL,
  pricing_rule_version VARCHAR(32) NOT NULL DEFAULT 'delivery-v1',
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_orders_status_order_time (status, order_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_submission (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  idempotency_key VARCHAR(80) NOT NULL,
  order_id BIGINT DEFAULT NULL,
  request_hash VARCHAR(64) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_submit_user_key (user_id, idempotency_key),
  KEY idx_order_submission_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_detail (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(32) DEFAULT NULL,
  image VARCHAR(255) DEFAULT NULL,
  order_id BIGINT NOT NULL,
  dish_id BIGINT DEFAULT NULL,
  setmeal_id BIGINT DEFAULT NULL,
  dish_flavor VARCHAR(50) DEFAULT NULL,
  number INT NOT NULL DEFAULT 1,
  amount DECIMAL(10,2) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_order_detail_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_session (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  refresh_token_hash VARCHAR(128) NOT NULL,
  device_id VARCHAR(128) DEFAULT NULL,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_session_refresh_token (refresh_token_hash),
  KEY idx_user_session_user_expiry (user_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sms_verification (
  id BIGINT NOT NULL AUTO_INCREMENT,
  phone VARCHAR(32) NOT NULL,
  code_hash VARCHAR(128) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  expires_at DATETIME NOT NULL,
  used_at DATETIME DEFAULT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sms_verification_phone_purpose (phone, purpose, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE auth_sms_cooldown (
  phone VARCHAR(32) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  reservation_id VARCHAR(64) NOT NULL DEFAULT '',
  next_allowed_at DATETIME NOT NULL,
  PRIMARY KEY (phone, purpose)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mock_account (
  id BIGINT NOT NULL AUTO_INCREMENT,
  account_no VARCHAR(64) NOT NULL,
  account_type VARCHAR(32) NOT NULL,
  owner_id BIGINT NOT NULL,
  available_cent BIGINT NOT NULL DEFAULT 0,
  frozen_cent BIGINT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mock_account_no (account_no),
  UNIQUE KEY uk_mock_account_type_owner (account_type, owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fund_transfer (
  id BIGINT NOT NULL AUTO_INCREMENT,
  transfer_no VARCHAR(64) NOT NULL,
  business_key VARCHAR(128) NOT NULL,
  transfer_type VARCHAR(32) NOT NULL,
  source_account_id BIGINT NOT NULL,
  target_account_id BIGINT NOT NULL,
  amount_cent BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  operator_id BIGINT DEFAULT NULL,
  reason VARCHAR(255) DEFAULT NULL,
  adjusted_account_id BIGINT DEFAULT NULL,
  balance_before_cent BIGINT DEFAULT NULL,
  balance_after_cent BIGINT DEFAULT NULL,
  completed_at DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_transfer_no (transfer_no),
  UNIQUE KEY uk_transfer_business_key (business_key),
  KEY idx_fund_transfer_source_time (source_account_id, create_time),
  KEY idx_fund_transfer_target_time (target_account_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE account_ledger_entry (
  id BIGINT NOT NULL AUTO_INCREMENT,
  transfer_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  direction VARCHAR(16) NOT NULL,
  amount_cent BIGINT NOT NULL,
  balance_after_cent BIGINT NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ledger_transfer_account_direction (transfer_id, account_id, direction),
  KEY idx_ledger_account_time (account_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_transaction (
  id BIGINT NOT NULL AUTO_INCREMENT,
  payment_no VARCHAR(64) NOT NULL,
  order_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  channel VARCHAR(32) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  amount_cent BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  gateway_trade_no VARCHAR(64) DEFAULT NULL,
  callback_event_id VARCHAR(64) DEFAULT NULL,
  expires_at DATETIME NOT NULL,
  succeeded_at DATETIME DEFAULT NULL,
  failure_code VARCHAR(64) DEFAULT NULL,
  version INT NOT NULL DEFAULT 0,
  success_order_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'SUCCEEDED' THEN order_id ELSE NULL END),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payment_no (payment_no),
  UNIQUE KEY uk_payment_idempotency (idempotency_key),
  UNIQUE KEY uk_payment_order_success_guard (success_order_id),
  UNIQUE KEY uk_payment_gateway_trade_no (gateway_trade_no),
  UNIQUE KEY uk_payment_callback_event_id (callback_event_id),
  KEY idx_payment_order_time (order_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_timeline_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_no VARCHAR(64) NOT NULL,
  order_id BIGINT NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  business_no VARCHAR(64) NOT NULL,
  display_message VARCHAR(255) NOT NULL,
  operator_type VARCHAR(32) NOT NULL,
  operator_id BIGINT DEFAULT NULL,
  payload_json TEXT DEFAULT NULL,
  event_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_timeline_event_no (event_no),
  KEY idx_order_timeline_order_time (order_id, event_time),
  KEY idx_order_timeline_business_no (business_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_security_audit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  detail_json TEXT DEFAULT NULL,
  ip_address VARCHAR(64) DEFAULT NULL,
  user_agent VARCHAR(512) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_security_audit_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO category (id, type, name, sort, status, create_time, update_time)
VALUES (1, 1, '验收分类', 1, 1, NOW(), NOW());

INSERT INTO dish (id, name, category_id, price, description, status, create_time, update_time)
VALUES (1, 'E2E 测试餐', 1, 12.00, '用于移动端核心流程验收', 1, NOW(), NOW());

INSERT INTO mock_account (account_no, account_type, owner_id, available_cent, frozen_cent, version)
VALUES ('PLATFORM_PENDING', 'PLATFORM_PENDING', 0, 0, 0, 0),
       ('MERCHANT_DEFAULT', 'MERCHANT', 0, 0, 0, 0);
