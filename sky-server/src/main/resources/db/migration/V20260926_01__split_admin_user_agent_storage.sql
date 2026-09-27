-- 将已执行通用主体迁移后的既有 Agent 数据完整归档为管理端数据。
-- 防御性断言：只要发现用户主体，CHECK约束就会中止迁移，避免误归档。
CREATE TEMPORARY TABLE `_agent_split_guard` (
    `invalid_count` BIGINT NOT NULL,
    CONSTRAINT `ck_agent_split_only_admin` CHECK (`invalid_count` = 0)
);
INSERT INTO `_agent_split_guard` (`invalid_count`)
SELECT
    (SELECT COUNT(*) FROM `agent_session`
     WHERE `actor_type` IS NULL OR `actor_type` <> 'ADMIN')
    + (SELECT COUNT(*) FROM `agent_task`
       WHERE `actor_type` IS NULL OR `actor_type` <> 'ADMIN')
    + (SELECT COUNT(*) FROM `agent_tool_confirmation`
       WHERE `actor_type` IS NULL OR `actor_type` <> 'ADMIN')
    + (SELECT COUNT(*) FROM `agent_tool_audit`
       WHERE `actor_type` IS NOT NULL AND `actor_type` <> 'ADMIN');
DROP TABLE `_agent_split_guard`;

-- 使用逐表ALTER语法，同时兼容MySQL生产环境和H2迁移测试。
ALTER TABLE `agent_session` RENAME TO `admin_agent_session`;
ALTER TABLE `agent_message` RENAME TO `admin_agent_message`;
ALTER TABLE `agent_session_summary` RENAME TO `admin_agent_session_summary`;
ALTER TABLE `agent_task` RENAME TO `admin_agent_task`;
ALTER TABLE `agent_event` RENAME TO `admin_agent_event`;
ALTER TABLE `agent_message_citation` RENAME TO `admin_agent_message_citation`;
ALTER TABLE `agent_tool_confirmation` RENAME TO `admin_agent_tool_confirmation`;
ALTER TABLE `agent_tool_audit` RENAME TO `admin_agent_tool_audit`;
ALTER TABLE `agent_knowledge_base` RENAME TO `admin_agent_knowledge_base`;
ALTER TABLE `agent_knowledge_document` RENAME TO `admin_agent_knowledge_document`;
ALTER TABLE `agent_knowledge_index_task` RENAME TO `admin_agent_knowledge_index_task`;

-- 用户会话只保存普通用户主体，不保留可产生歧义的通用 actor 字段。
CREATE TABLE `user_agent_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `session_id` VARCHAR(64) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `public_kb_version` VARCHAR(64) DEFAULT NULL,
    `title` VARCHAR(255) DEFAULT NULL,
    `status` INT NOT NULL DEFAULT 1,
    `last_task_id` VARCHAR(64) DEFAULT NULL,
    `message_count` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `create_user` BIGINT DEFAULT NULL,
    `update_user` BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_agent_session_id` (`session_id`),
    KEY `idx_user_agent_session_owner_time` (`user_id`, `update_time`),
    CONSTRAINT `ck_user_agent_session_status` CHECK (`status` IN (1, 2, 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户端Agent会话';

CREATE TABLE `user_agent_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `message_id` VARCHAR(64) NOT NULL,
    `session_id` VARCHAR(64) NOT NULL,
    `task_id` VARCHAR(64) DEFAULT NULL,
    `role` INT NOT NULL,
    `content` TEXT,
    `content_type` VARCHAR(50) DEFAULT NULL,
    `token_count` INT DEFAULT NULL,
    `seq_no` INT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_agent_message_id` (`message_id`),
    UNIQUE KEY `uk_user_agent_message_session_seq` (`session_id`, `seq_no`),
    UNIQUE KEY `uk_user_agent_message_task_role` (`task_id`, `role`),
    KEY `idx_user_agent_message_session_time` (`session_id`, `create_time`),
    CONSTRAINT `ck_user_agent_message_role` CHECK (`role` IN (1, 2, 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户端Agent消息';

CREATE TABLE `user_agent_session_summary` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `session_id` VARCHAR(64) NOT NULL,
    `summary` TEXT NOT NULL,
    `summary_until_seq` INT NOT NULL DEFAULT 0,
    `version` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_agent_summary_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户端Agent会话摘要';

CREATE TABLE `user_agent_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` VARCHAR(64) NOT NULL,
    `session_id` VARCHAR(64) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `query` TEXT NOT NULL,
    `intent` VARCHAR(32) DEFAULT NULL,
    `client_context_json` JSON DEFAULT NULL,
    `status` INT NOT NULL DEFAULT 0,
    `progress` INT NOT NULL DEFAULT 0,
    `model` VARCHAR(255) DEFAULT NULL,
    `request_hash` CHAR(64) DEFAULT NULL,
    `assistant_message_id` VARCHAR(64) DEFAULT NULL,
    `started_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `error_msg` TEXT,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_agent_task_id` (`task_id`),
    KEY `idx_user_agent_task_owner_time` (`user_id`, `create_time`),
    KEY `idx_user_agent_task_session_time` (`session_id`, `create_time`),
    CONSTRAINT `ck_user_agent_task_status` CHECK (`status` IN (0, 1, 2, 3, 4))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户端Agent任务';

CREATE TABLE `user_agent_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `event_id` VARCHAR(255) NOT NULL,
    `task_id` VARCHAR(64) NOT NULL,
    `seq_no` INT NOT NULL,
    `event_type` VARCHAR(64) NOT NULL,
    `data` JSON DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_agent_event_id` (`event_id`),
    UNIQUE KEY `uk_user_agent_event_task_seq` (`task_id`, `seq_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户端Agent事件';

CREATE TABLE `user_agent_message_citation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `message_id` VARCHAR(64) NOT NULL,
    `kb_id` VARCHAR(64) NOT NULL,
    `document_id` VARCHAR(64) NOT NULL,
    `document_version` INT NOT NULL,
    `chunk_id` VARCHAR(64) NOT NULL,
    `file_name` VARCHAR(255) NOT NULL,
    `page_no` INT DEFAULT NULL,
    `score` DECIMAL(8,6) NOT NULL,
    `quote` TEXT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_agent_citation_message_chunk` (`message_id`, `chunk_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户端Agent消息引用';

CREATE TABLE `user_agent_tool_confirmation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `confirmation_id` VARCHAR(64) NOT NULL,
    `task_id` VARCHAR(64) NOT NULL,
    `tool_call_id` VARCHAR(128) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `operation` VARCHAR(64) NOT NULL,
    `arguments_json` JSON NOT NULL,
    `argument_hash` CHAR(64) NOT NULL,
    `resource_version` VARCHAR(128) NOT NULL,
    `summary` VARCHAR(255) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `expires_at` DATETIME NOT NULL,
    `confirmed_at` DATETIME DEFAULT NULL,
    `executed_at` DATETIME DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_agent_confirmation_id` (`confirmation_id`),
    UNIQUE KEY `uk_user_agent_confirmation_call` (`task_id`, `tool_call_id`),
    KEY `idx_user_agent_confirmation_owner` (`user_id`, `status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户端Agent工具确认';

CREATE TABLE `user_agent_tool_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `request_id` VARCHAR(64) NOT NULL,
    `task_id` VARCHAR(64) NOT NULL,
    `tool_call_id` VARCHAR(128) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `operation` VARCHAR(64) NOT NULL,
    `required_permission` VARCHAR(64) NOT NULL,
    `argument_hash` CHAR(64) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `error_code` VARCHAR(64) DEFAULT NULL,
    `duration_ms` BIGINT NOT NULL,
    `trace_id` VARCHAR(64) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_agent_audit_call_status` (`task_id`, `tool_call_id`, `status`),
    KEY `idx_user_agent_audit_owner_time` (`user_id`, `create_time`),
    KEY `idx_user_agent_audit_operation_time` (`operation`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户端Agent工具审计';

CREATE TABLE `user_agent_knowledge_base` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `kb_id` VARCHAR(64) NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `description` VARCHAR(500) DEFAULT NULL,
    `embedding_model` VARCHAR(64) NOT NULL,
    `chunk_strategy` VARCHAR(32) NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 0,
    `published_version` INT DEFAULT NULL,
    `published_by_employee_id` BIGINT DEFAULT NULL,
    `published_at` DATETIME DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_agent_kb_id` (`kb_id`),
    KEY `idx_user_agent_kb_status` (`status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户端Agent公共知识库';

CREATE TABLE `user_agent_knowledge_document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `document_id` VARCHAR(64) NOT NULL,
    `kb_id` VARCHAR(64) NOT NULL,
    `file_name` VARCHAR(255) NOT NULL,
    `file_type` VARCHAR(32) NOT NULL,
    `file_url` VARCHAR(1000) NOT NULL,
    `file_hash` VARCHAR(64) NOT NULL,
    `version` INT NOT NULL,
    `active_version` INT DEFAULT NULL,
    `status` TINYINT NOT NULL DEFAULT 0,
    `chunk_count` INT NOT NULL DEFAULT 0,
    `error_msg` VARCHAR(1000) DEFAULT NULL,
    `create_user` BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_agent_document_version` (`document_id`, `version`),
    UNIQUE KEY `uk_user_agent_document_hash_version` (`kb_id`, `file_hash`, `version`),
    KEY `idx_user_agent_document_kb_status` (`kb_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户端Agent公共知识文档';

CREATE TABLE `user_agent_knowledge_index_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` VARCHAR(64) NOT NULL,
    `document_id` VARCHAR(64) NOT NULL,
    `document_version` INT NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 0,
    `progress` INT NOT NULL DEFAULT 0,
    `request_hash` VARCHAR(64) NOT NULL,
    `error_msg` VARCHAR(1000) DEFAULT NULL,
    `started_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_agent_index_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户端Agent公共知识索引任务';
