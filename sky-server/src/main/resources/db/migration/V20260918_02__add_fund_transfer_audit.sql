ALTER TABLE `fund_transfer` ADD COLUMN `operator_id` BIGINT DEFAULT NULL;
ALTER TABLE `fund_transfer` ADD COLUMN `reason` VARCHAR(255) DEFAULT NULL;
ALTER TABLE `fund_transfer` ADD COLUMN `adjusted_account_id` BIGINT DEFAULT NULL;
ALTER TABLE `fund_transfer` ADD COLUMN `balance_before_cent` BIGINT DEFAULT NULL;
ALTER TABLE `fund_transfer` ADD COLUMN `balance_after_cent` BIGINT DEFAULT NULL;
