CREATE TABLE `agent_knowledge_base` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kb_id` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `embedding_model` varchar(64) NOT NULL,
  `chunk_strategy` varchar(32) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 1,
  `create_user` bigint NOT NULL,
  `update_user` bigint NOT NULL,
  `create_time` datetime NOT NULL,
  `update_time` datetime NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_agent_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `agent_knowledge_document` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `document_id` varchar(64) NOT NULL,
  `kb_id` varchar(64) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `file_type` varchar(32) NOT NULL,
  `file_url` varchar(1000) NOT NULL,
  `file_hash` varchar(64) NOT NULL,
  `version` int NOT NULL,
  `active_version` int DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `chunk_count` int NOT NULL DEFAULT 0,
  `error_msg` varchar(1000) DEFAULT NULL,
  `create_user` bigint NOT NULL,
  `create_time` datetime NOT NULL,
  `update_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_document_version` (`document_id`,`version`),
  UNIQUE KEY `uk_agent_document_hash_version` (`kb_id`,`file_hash`,`version`),
  KEY `idx_agent_document_kb_status` (`kb_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `agent_knowledge_index_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) NOT NULL,
  `document_id` varchar(64) NOT NULL,
  `document_version` int NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0,
  `progress` int NOT NULL DEFAULT 0,
  `request_hash` varchar(64) NOT NULL,
  `error_msg` varchar(1000) DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL,
  `update_time` datetime NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_agent_index_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `agent_message_citation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `message_id` varchar(64) NOT NULL,
  `kb_id` varchar(64) NOT NULL,
  `document_id` varchar(64) NOT NULL,
  `document_version` int NOT NULL,
  `chunk_id` varchar(64) NOT NULL,
  `file_name` varchar(255) NOT NULL,
  `page_no` int DEFAULT NULL,
  `score` decimal(8,6) NOT NULL,
  `quote` text NOT NULL,
  `create_time` datetime NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_agent_citation_message_chunk` (`message_id`,`chunk_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `agent_session` ADD COLUMN `kb_id` varchar(64) DEFAULT NULL AFTER `user_id`;
