-- 菜品适配属性：用于感冒、时令和个性化场景的硬过滤与软排序。
CREATE TABLE `dish_diet_adaptation_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `dish_id` BIGINT NOT NULL,
    `profile_version` INT NOT NULL DEFAULT 1,
    `spicy_level` TINYINT DEFAULT NULL,
    `oil_level` TINYINT DEFAULT NULL,
    `salt_level` TINYINT DEFAULT NULL,
    `temperature_type` VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    `digestibility` VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    `alcohol_content` VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    `pickled_food` VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    `soup_base_type` VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    `simulation_version` VARCHAR(64) DEFAULT NULL,
    `verification_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    `source_reference` VARCHAR(500) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dish_diet_profile_version` (`dish_id`, `profile_version`),
    KEY `idx_dish_diet_profile_active` (`dish_id`, `verification_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品饮食适配属性版本';
