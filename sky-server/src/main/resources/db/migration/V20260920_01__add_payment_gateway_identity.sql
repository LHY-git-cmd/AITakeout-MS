-- 支付渠道交易号和回调事件号用于渠道结果追踪与双重幂等。
ALTER TABLE `payment_transaction`
    ADD COLUMN `gateway_trade_no` VARCHAR(64) DEFAULT NULL AFTER `status`;

ALTER TABLE `payment_transaction`
    ADD COLUMN `callback_event_id` VARCHAR(64) DEFAULT NULL AFTER `gateway_trade_no`;

CREATE UNIQUE INDEX `uk_payment_gateway_trade_no`
    ON `payment_transaction` (`gateway_trade_no`);

CREATE UNIQUE INDEX `uk_payment_callback_event_id`
    ON `payment_transaction` (`callback_event_id`);
