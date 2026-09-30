-- 用户公共知识发布控制面：发布版本不可变，用户数据面只读取当前有效绑定。
ALTER TABLE `user_agent_knowledge_base` ADD COLUMN `category` VARCHAR(64) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE `user_agent_knowledge_base` ADD COLUMN `lifecycle_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE `user_agent_knowledge_base` ADD COLUMN `valid_from` DATETIME DEFAULT NULL;
ALTER TABLE `user_agent_knowledge_base` ADD COLUMN `valid_until` DATETIME DEFAULT NULL;

ALTER TABLE `user_agent_knowledge_document` ADD COLUMN `category` VARCHAR(64) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE `user_agent_knowledge_document` ADD COLUMN `lifecycle_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE `user_agent_knowledge_document` ADD COLUMN `content_hash` CHAR(64) DEFAULT NULL;
ALTER TABLE `user_agent_knowledge_document` ADD COLUMN `review_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE `user_agent_knowledge_document` ADD COLUMN `reviewed_by` BIGINT DEFAULT NULL;
ALTER TABLE `user_agent_knowledge_document` ADD COLUMN `reviewed_at` DATETIME DEFAULT NULL;

CREATE TABLE `user_agent_knowledge_release` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `release_id` VARCHAR(64) NOT NULL,
    `kb_id` VARCHAR(64) NOT NULL,
    `release_version` INT NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    `effective_from` DATETIME DEFAULT NULL,
    `effective_until` DATETIME DEFAULT NULL,
    `created_by` BIGINT NOT NULL,
    `approved_by` BIGINT DEFAULT NULL,
    `published_by` BIGINT DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `approved_at` DATETIME DEFAULT NULL,
    `published_at` DATETIME DEFAULT NULL,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_agent_release_id` (`release_id`),
    UNIQUE KEY `uk_user_agent_release_version` (`kb_id`, `release_version`),
    KEY `idx_user_agent_release_effective` (`kb_id`, `status`, `effective_from`, `effective_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户端Agent公共知识不可变发布版本';

CREATE TABLE `user_agent_knowledge_release_document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `release_id` VARCHAR(64) NOT NULL,
    `document_id` VARCHAR(64) NOT NULL,
    `document_version` INT NOT NULL,
    `category` VARCHAR(64) NOT NULL DEFAULT 'GENERAL',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_agent_release_document` (`release_id`, `document_id`),
    KEY `idx_user_agent_release_document_version` (`document_id`, `document_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共知识发布版本包含的文档版本';

CREATE TABLE `user_agent_knowledge_binding` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `binding_id` VARCHAR(64) NOT NULL,
    `scene` VARCHAR(64) NOT NULL,
    `release_id` VARCHAR(64) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `effective_from` DATETIME DEFAULT NULL,
    `effective_until` DATETIME DEFAULT NULL,
    `created_by` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_agent_binding_id` (`binding_id`),
    UNIQUE KEY `uk_user_agent_binding_scene_release` (`scene`, `release_id`),
    KEY `idx_user_agent_binding_scene_effective` (`scene`, `status`, `effective_from`, `effective_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户Agent场景与公共知识发布版本绑定';

CREATE TABLE `user_agent_knowledge_review` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `release_id` VARCHAR(64) NOT NULL,
    `reviewer_id` BIGINT NOT NULL,
    `decision` VARCHAR(32) NOT NULL,
    `comment` VARCHAR(1000) DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_agent_review_release` (`release_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共知识发布审核记录';

CREATE TABLE `user_agent_knowledge_publish_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `release_id` VARCHAR(64) NOT NULL,
    `action` VARCHAR(32) NOT NULL,
    `operator_id` BIGINT NOT NULL,
    `from_status` VARCHAR(32) DEFAULT NULL,
    `to_status` VARCHAR(32) NOT NULL,
    `detail_json` JSON DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_agent_publish_audit_release` (`release_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共知识发布下线回滚不可变审计';
