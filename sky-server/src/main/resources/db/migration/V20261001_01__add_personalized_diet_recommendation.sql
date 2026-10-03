-- 个性化饮食推荐数据底座：营养、过敏原、用户档案、规则版本与推荐轨迹。
CREATE TABLE `food_ingredient` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `ingredient_code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `category` VARCHAR(64) DEFAULT NULL,
    `aliases_json` JSON DEFAULT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_food_ingredient_code` (`ingredient_code`),
    KEY `idx_food_ingredient_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标准食材字典';

CREATE TABLE `food_allergen` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `allergen_code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `description` VARCHAR(500) DEFAULT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_food_allergen_code` (`allergen_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标准过敏原字典';

CREATE TABLE `dish_recipe_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `dish_id` BIGINT NOT NULL,
    `recipe_version` INT NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    `cooking_method` VARCHAR(64) DEFAULT NULL,
    `spicy_level` TINYINT DEFAULT NULL,
    `oil_adjustable` TINYINT NOT NULL DEFAULT 0,
    `salt_adjustable` TINYINT NOT NULL DEFAULT 0,
    `effective_from` DATETIME DEFAULT NULL,
    `effective_until` DATETIME DEFAULT NULL,
    `created_by` BIGINT NOT NULL,
    `verified_by` BIGINT DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dish_recipe_version` (`dish_id`, `recipe_version`),
    KEY `idx_dish_recipe_active` (`dish_id`, `status`, `effective_from`, `effective_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品配方版本';

CREATE TABLE `dish_ingredient` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `dish_id` BIGINT NOT NULL,
    `recipe_version` INT NOT NULL,
    `ingredient_id` BIGINT NOT NULL,
    `amount_g` DECIMAL(10,2) DEFAULT NULL,
    `role_type` VARCHAR(32) NOT NULL DEFAULT 'PRIMARY',
    `replaceable` TINYINT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dish_recipe_ingredient` (`dish_id`, `recipe_version`, `ingredient_id`),
    KEY `idx_dish_ingredient_lookup` (`ingredient_id`, `dish_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品配方食材';

CREATE TABLE `dish_nutrition_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `dish_id` BIGINT NOT NULL,
    `profile_version` INT NOT NULL,
    `recipe_version` INT NOT NULL,
    `serving_size_g` DECIMAL(10,2) NOT NULL,
    `energy_kcal` DECIMAL(10,2) DEFAULT NULL,
    `protein_g` DECIMAL(10,2) DEFAULT NULL,
    `fat_g` DECIMAL(10,2) DEFAULT NULL,
    `carbohydrate_g` DECIMAL(10,2) DEFAULT NULL,
    `dietary_fiber_g` DECIMAL(10,2) DEFAULT NULL,
    `sugar_g` DECIMAL(10,2) DEFAULT NULL,
    `sodium_mg` DECIMAL(10,2) DEFAULT NULL,
    `purine_mg` DECIMAL(10,2) DEFAULT NULL,
    `source_type` VARCHAR(32) NOT NULL,
    `source_reference` VARCHAR(500) NOT NULL,
    `calculation_method` VARCHAR(128) DEFAULT NULL,
    `verification_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    `data_completeness` DECIMAL(5,2) NOT NULL DEFAULT 0,
    `uncertainty_note` VARCHAR(500) DEFAULT NULL,
    `verified_by` BIGINT DEFAULT NULL,
    `verified_at` DATETIME DEFAULT NULL,
    `effective_from` DATETIME DEFAULT NULL,
    `effective_until` DATETIME DEFAULT NULL,
    `version` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dish_nutrition_version` (`dish_id`, `profile_version`),
    KEY `idx_dish_nutrition_active` (`dish_id`, `verification_status`, `effective_from`, `effective_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品营养版本';

CREATE TABLE `dish_allergen_declaration` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `dish_id` BIGINT NOT NULL,
    `recipe_version` INT NOT NULL,
    `allergen_code` VARCHAR(64) NOT NULL,
    `declaration_status` VARCHAR(32) NOT NULL,
    `source_reference` VARCHAR(500) NOT NULL,
    `verified_by` BIGINT DEFAULT NULL,
    `verified_at` DATETIME DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dish_recipe_allergen` (`dish_id`, `recipe_version`, `allergen_code`),
    KEY `idx_dish_allergen_lookup` (`allergen_code`, `declaration_status`, `dish_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品过敏原声明';

CREATE TABLE `setmeal_nutrition_snapshot` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `setmeal_id` BIGINT NOT NULL,
    `snapshot_version` INT NOT NULL,
    `composition_hash` CHAR(64) NOT NULL,
    `energy_kcal` DECIMAL(10,2) DEFAULT NULL,
    `protein_g` DECIMAL(10,2) DEFAULT NULL,
    `fat_g` DECIMAL(10,2) DEFAULT NULL,
    `carbohydrate_g` DECIMAL(10,2) DEFAULT NULL,
    `dietary_fiber_g` DECIMAL(10,2) DEFAULT NULL,
    `sugar_g` DECIMAL(10,2) DEFAULT NULL,
    `sodium_mg` DECIMAL(10,2) DEFAULT NULL,
    `allergens_json` JSON DEFAULT NULL,
    `source_versions_json` JSON NOT NULL,
    `verification_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_setmeal_nutrition_version` (`setmeal_id`, `snapshot_version`),
    KEY `idx_setmeal_nutrition_active` (`setmeal_id`, `verification_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐营养聚合快照';

CREATE TABLE `seasonal_ingredient_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `ingredient_id` BIGINT NOT NULL,
    `region_code` VARCHAR(32) NOT NULL DEFAULT 'CN',
    `start_month` TINYINT NOT NULL,
    `end_month` TINYINT NOT NULL,
    `weight` DECIMAL(5,2) NOT NULL DEFAULT 1,
    `source_reference` VARCHAR(500) NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_seasonal_region_month` (`region_code`, `start_month`, `end_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地域时令食材规则';

CREATE TABLE `user_diet_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `region_code` VARCHAR(32) DEFAULT NULL,
    `dietary_pattern` VARCHAR(32) DEFAULT NULL,
    `consent_version` VARCHAR(32) NOT NULL,
    `consented_at` DATETIME NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `version` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted_at` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_diet_profile_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户饮食档案';

CREATE TABLE `user_diet_constraint` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `constraint_type` VARCHAR(32) NOT NULL,
    `constraint_code` VARCHAR(64) NOT NULL,
    `severity` VARCHAR(16) NOT NULL DEFAULT 'STRICT',
    `source_type` VARCHAR(32) NOT NULL DEFAULT 'USER',
    `effective_until` DATETIME DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_diet_constraint` (`user_id`, `constraint_type`, `constraint_code`),
    KEY `idx_user_diet_constraint_active` (`user_id`, `effective_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户饮食硬约束与偏好';

CREATE TABLE `user_health_goal` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `goal_code` VARCHAR(64) NOT NULL,
    `priority` INT NOT NULL DEFAULT 50,
    `effective_until` DATETIME DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_health_goal` (`user_id`, `goal_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户健康目标';

CREATE TABLE `user_diet_profile_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `action` VARCHAR(32) NOT NULL,
    `detail_json` JSON NOT NULL,
    `trace_id` VARCHAR(64) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_diet_audit_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户饮食档案审计';

CREATE TABLE `diet_rule_set` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `rule_set_id` VARCHAR(64) NOT NULL,
    `rule_code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `applicable_population` VARCHAR(255) NOT NULL,
    `rule_version` INT NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    `effective_from` DATETIME DEFAULT NULL,
    `effective_until` DATETIME DEFAULT NULL,
    `created_by` BIGINT NOT NULL,
    `published_by` BIGINT DEFAULT NULL,
    `published_at` DATETIME DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_diet_rule_set_id` (`rule_set_id`),
    UNIQUE KEY `uk_diet_rule_code_version` (`rule_code`, `rule_version`),
    KEY `idx_diet_rule_set_active` (`rule_code`, `status`, `effective_from`, `effective_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='饮食规则集版本';

CREATE TABLE `diet_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `rule_set_id` VARCHAR(64) NOT NULL,
    `rule_id` VARCHAR(64) NOT NULL,
    `target_field` VARCHAR(64) NOT NULL,
    `operator` VARCHAR(16) NOT NULL,
    `comparison_value` VARCHAR(255) DEFAULT NULL,
    `action` VARCHAR(32) NOT NULL,
    `score_delta` DECIMAL(8,2) DEFAULT NULL,
    `priority` INT NOT NULL DEFAULT 100,
    `reason_code` VARCHAR(64) NOT NULL,
    `message` VARCHAR(500) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_diet_rule_id` (`rule_set_id`, `rule_id`),
    KEY `idx_diet_rule_eval` (`rule_set_id`, `priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='类型化饮食规则';

CREATE TABLE `diet_rule_source` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `rule_set_id` VARCHAR(64) NOT NULL,
    `source_title` VARCHAR(255) NOT NULL,
    `source_url` VARCHAR(1000) NOT NULL,
    `source_section` VARCHAR(255) DEFAULT NULL,
    `published_date` DATE DEFAULT NULL,
    `review_due_date` DATE NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_diet_rule_source_set` (`rule_set_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='饮食规则权威来源';

CREATE TABLE `diet_rule_review` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `rule_set_id` VARCHAR(64) NOT NULL,
    `reviewer_id` BIGINT NOT NULL,
    `decision` VARCHAR(32) NOT NULL,
    `comment` VARCHAR(500) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_diet_rule_review_set` (`rule_set_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='饮食规则审核';

CREATE TABLE `diet_rule_publish_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `rule_set_id` VARCHAR(64) NOT NULL,
    `operator_id` BIGINT NOT NULL,
    `action` VARCHAR(32) NOT NULL,
    `from_status` VARCHAR(32) DEFAULT NULL,
    `to_status` VARCHAR(32) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_diet_rule_audit_set` (`rule_set_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='饮食规则发布审计';

CREATE TABLE `diet_recommendation_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `recommendation_id` VARCHAR(64) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `idempotency_key` VARCHAR(64) NOT NULL,
    `scene` VARCHAR(64) NOT NULL,
    `risk_level` VARCHAR(16) NOT NULL,
    `request_hash` CHAR(64) NOT NULL,
    `request_json` JSON NOT NULL,
    `rule_versions_json` JSON NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `trace_id` VARCHAR(64) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_diet_recommendation_id` (`recommendation_id`),
    UNIQUE KEY `uk_diet_recommendation_user_key` (`user_id`, `idempotency_key`),
    KEY `idx_diet_recommendation_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='饮食推荐请求快照';

CREATE TABLE `diet_recommendation_candidate` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `recommendation_id` VARCHAR(64) NOT NULL,
    `product_type` VARCHAR(16) NOT NULL,
    `product_id` BIGINT NOT NULL,
    `eligible` TINYINT NOT NULL,
    `score` DECIMAL(10,2) NOT NULL DEFAULT 0,
    `reason_codes_json` JSON NOT NULL,
    `score_detail_json` JSON NOT NULL,
    `data_versions_json` JSON NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_diet_candidate_product` (`recommendation_id`, `product_type`, `product_id`),
    KEY `idx_diet_candidate_rank` (`recommendation_id`, `eligible`, `score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='饮食推荐候选轨迹';

CREATE TABLE `diet_recommendation_feedback` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `recommendation_id` VARCHAR(64) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `product_type` VARCHAR(16) NOT NULL,
    `product_id` BIGINT NOT NULL,
    `feedback_type` VARCHAR(32) NOT NULL,
    `reason_code` VARCHAR(64) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_diet_feedback_once` (`recommendation_id`, `user_id`, `product_type`, `product_id`, `feedback_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='饮食推荐反馈';

INSERT INTO `food_allergen` (`allergen_code`, `name`, `description`) VALUES
('PEANUT', '花生', '花生及花生制品'),
('TREE_NUT', '坚果', '树坚果及其制品'),
('MILK', '乳制品', '牛奶及乳制品'),
('EGG', '蛋类', '蛋及蛋制品'),
('WHEAT', '小麦', '小麦及含麸质谷物'),
('SOY', '大豆', '大豆及豆制品'),
('FISH', '鱼类', '鱼及鱼制品'),
('SHELLFISH', '甲壳类', '虾、蟹等甲壳类水产'),
('SESAME', '芝麻', '芝麻及芝麻制品');
