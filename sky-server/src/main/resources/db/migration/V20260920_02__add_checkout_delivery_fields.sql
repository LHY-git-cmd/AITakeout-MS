-- 阶段二结算迁移：增加权威分金额、地址配送校验快照与下单幂等记录。
ALTER TABLE orders ADD COLUMN goods_amount_cent BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN pack_amount_cent BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN delivery_fee_cent BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN discount_amount_cent BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN amount_cent BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN delivery_distance_meters INT DEFAULT NULL;
ALTER TABLE orders ADD COLUMN map_provider VARCHAR(16) DEFAULT NULL;
ALTER TABLE orders ADD COLUMN delivery_mode VARCHAR(16) NOT NULL DEFAULT 'IMMEDIATE';
ALTER TABLE orders ADD COLUMN delivery_slot_start DATETIME DEFAULT NULL;
ALTER TABLE orders ADD COLUMN delivery_slot_end DATETIME DEFAULT NULL;
ALTER TABLE orders ADD COLUMN address_latitude DECIMAL(10,7) DEFAULT NULL;
ALTER TABLE orders ADD COLUMN address_longitude DECIMAL(10,7) DEFAULT NULL;
ALTER TABLE orders ADD COLUMN expires_at DATETIME DEFAULT NULL;
ALTER TABLE orders ADD COLUMN pricing_rule_version VARCHAR(32) NOT NULL DEFAULT 'delivery-v1';
ALTER TABLE orders ADD COLUMN version INT NOT NULL DEFAULT 0;

UPDATE orders SET amount_cent = ROUND(amount * 100),
                  goods_amount_cent = GREATEST(ROUND(amount * 100) - COALESCE(pack_amount, 0) * 100, 0),
                  pack_amount_cent = COALESCE(pack_amount, 0) * 100
WHERE amount_cent = 0;

ALTER TABLE address_book ADD COLUMN latitude DECIMAL(10,7) DEFAULT NULL;
ALTER TABLE address_book ADD COLUMN longitude DECIMAL(10,7) DEFAULT NULL;
ALTER TABLE address_book ADD COLUMN geocode_status VARCHAR(24) NOT NULL DEFAULT 'PENDING';
ALTER TABLE address_book ADD COLUMN map_provider VARCHAR(16) DEFAULT NULL;
ALTER TABLE address_book ADD COLUMN distance_meters INT DEFAULT NULL;
ALTER TABLE address_book ADD COLUMN deliverable TINYINT(1) DEFAULT NULL;
ALTER TABLE address_book ADD COLUMN validation_message VARCHAR(255) DEFAULT NULL;
ALTER TABLE address_book ADD COLUMN validated_at DATETIME DEFAULT NULL;
ALTER TABLE address_book ADD COLUMN delivery_rule_version VARCHAR(32) DEFAULT NULL;

CREATE TABLE order_submission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    order_id BIGINT DEFAULT NULL,
    request_hash VARCHAR(64) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_order_submit_user_key UNIQUE (user_id, idempotency_key)
);
CREATE INDEX idx_order_submission_order ON order_submission(order_id);
