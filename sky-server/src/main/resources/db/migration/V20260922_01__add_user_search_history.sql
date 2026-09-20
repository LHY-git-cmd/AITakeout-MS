-- 阶段四商品搜索历史：登录用户按标准化关键词去重，最多保留最近二十条。
CREATE TABLE `user_search_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `keyword` VARCHAR(64) NOT NULL,
    `normalized_keyword` VARCHAR(64) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_search_history_user_keyword` (`user_id`, `normalized_keyword`),
    KEY `idx_search_history_user_time` (`user_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户商品搜索历史';
