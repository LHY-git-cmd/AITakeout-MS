
-- Agent Tables

--
-- Table structure for table `agent_event`
--
CREATE TABLE `agent_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_id` VARCHAR(255) NOT NULL COMMENT '事件ID',
  `task_id` VARCHAR(255) NOT NULL COMMENT '任务ID',
  `seq_no` INT COMMENT '序列号',
  `event_type` VARCHAR(50) COMMENT '事件类型 (message/tool_call/tool_result/progress/done/error)',
  `data` JSON COMMENT 'JSON格式的事件数据',
  `create_time` DATETIME COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent事件';

--
-- Table structure for table `agent_knowledge_base`
--
CREATE TABLE `agent_knowledge_base` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键ID',
  `kb_id` VARCHAR(255) NOT NULL COMMENT '知识库的唯一业务标识符',
  `name` VARCHAR(255) NOT NULL COMMENT '知识库的名称',
  `description` TEXT COMMENT '知识库的详细描述',
  `embedding_model` VARCHAR(255) COMMENT '用于生成向量嵌入的模型名称',
  `chunk_strategy` VARCHAR(255) COMMENT '文档分块策略的标识符',
  `status` INT COMMENT '知识库状态 (0: 初始化中, 1: 可用, 2: 索引中, 3: 失败)',
  `create_user` BIGINT COMMENT '创建该知识库的用户ID',
  `update_user` BIGINT COMMENT '最后更新该知识库的用户ID',
  `create_time` DATETIME COMMENT '记录创建时间',
  `update_time` DATETIME COMMENT '记录最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent知识库';

--
-- Table structure for table `agent_knowledge_document`
--
CREATE TABLE `agent_knowledge_document` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键ID',
  `document_id` VARCHAR(255) NOT NULL COMMENT '文档的唯一业务标识符',
  `kb_id` VARCHAR(255) NOT NULL COMMENT '所属知识库的唯一标识符',
  `file_name` VARCHAR(255) NOT NULL COMMENT '文档的原始文件名',
  `file_type` VARCHAR(50) COMMENT '文档的文件类型',
  `file_url` VARCHAR(255) COMMENT '文档的存储URL',
  `file_hash` VARCHAR(255) COMMENT '最新版本文件的内容哈希值',
  `version` INT COMMENT '文档的版本号',
  `active_version` INT COMMENT '当前在线生效的版本号',
  `status` INT COMMENT '文档的索引状态 (0: 待索引, 1: 索引中, 2: 已完成, 3: 失败)',
  `chunk_count` INT COMMENT '文档被切分成的块的数量',
  `error_msg` TEXT COMMENT '如果索引失败，记录错误信息',
  `create_user` BIGINT COMMENT '创建该文档的用户ID',
  `create_time` DATETIME COMMENT '记录创建时间',
  `update_time` DATETIME COMMENT '记录最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_id` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent知识库文档';

--
-- Table structure for table `agent_knowledge_index_task`
--
CREATE TABLE `agent_knowledge_index_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键ID',
  `task_id` VARCHAR(255) NOT NULL COMMENT '任务的唯一业务标识符',
  `document_id` VARCHAR(255) NOT NULL COMMENT '关联的文档ID',
  `document_version` INT COMMENT '正在索引的文档版本号',
  `status` INT COMMENT '任务状态 (0: 排队中, 1: 处理中, 2: 成功, 3: 失败)',
  `progress` INT COMMENT '任务进度，百分比（0-100）',
  `request_hash` VARCHAR(255) COMMENT '索引请求的哈希值',
  `error_msg` TEXT COMMENT '如果任务失败，记录错误信息',
  `started_at` DATETIME COMMENT '任务开始处理的时间',
  `finished_at` DATETIME COMMENT '任务完成或失败的时间',
  `create_time` DATETIME COMMENT '记录创建时间',
  `update_time` DATETIME COMMENT '记录最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent知识库文档索引任务';

--
-- Table structure for table `agent_message`
--
CREATE TABLE `agent_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `message_id` VARCHAR(255) NOT NULL COMMENT '消息ID',
  `session_id` VARCHAR(255) NOT NULL COMMENT '会话ID',
  `task_id` VARCHAR(255) COMMENT '任务ID',
  `role` INT COMMENT '角色 (1-user, 2-assistant, 3-system)',
  `content` TEXT COMMENT '内容',
  `content_type` VARCHAR(50) COMMENT '内容类型 (text/markdown/tool_call)',
  `token_count` INT COMMENT 'Token数量',
  `seq_no` INT COMMENT '序列号',
  `create_time` DATETIME COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent消息';

--
-- Table structure for table `agent_message_citation`
--
CREATE TABLE `agent_message_citation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `message_id` VARCHAR(255) NOT NULL COMMENT '消息ID',
  `kb_id` VARCHAR(255) COMMENT '知识库ID',
  `document_id` VARCHAR(255) COMMENT '文档ID',
  `document_version` INT COMMENT '文档版本',
  `chunk_id` VARCHAR(255) COMMENT '块ID',
  `file_name` VARCHAR(255) COMMENT '文件名',
  `page_no` INT COMMENT '页码',
  `score` DECIMAL(19, 4) COMMENT '分数',
  `quote` TEXT COMMENT '引用内容',
  `create_time` DATETIME COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent消息引用';

--
-- Table structure for table `agent_session`
--
CREATE TABLE `agent_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` VARCHAR(255) NOT NULL COMMENT '会话ID',
  `user_id` BIGINT COMMENT '用户ID',
  `kb_id` VARCHAR(255) COMMENT '知识库ID',
  `title` VARCHAR(255) COMMENT '标题',
  `status` INT COMMENT '状态（1-进行中，2-已归档，3-已删除）',
  `last_task_id` VARCHAR(255) COMMENT '最新任务ID',
  `message_count` INT COMMENT '消息数量',
  `create_time` DATETIME COMMENT '创建时间',
  `update_time` DATETIME COMMENT '更新时间',
  `create_user` BIGINT COMMENT '创建人',
  `update_user` BIGINT COMMENT '修改人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent会话实体';

--
-- Table structure for table `agent_session_summary`
--
CREATE TABLE `agent_session_summary` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `session_id` varchar(64) NOT NULL,
    `summary` text NOT NULL,
    `summary_until_seq` int NOT NULL DEFAULT 0,
    `version` int NOT NULL DEFAULT 0,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_session_summary_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent会话历史摘要';

--
-- Table structure for table `agent_task`
--
CREATE TABLE `agent_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` VARCHAR(255) NOT NULL COMMENT '任务ID',
  `session_id` VARCHAR(255) NOT NULL COMMENT '会话ID',
  `user_id` BIGINT COMMENT '用户ID',
  `query` TEXT COMMENT '查询内容',
  `status` INT COMMENT '状态 (0-排队, 1-执行中, 2-完成, 3-失败, 4-取消)',
  `progress` INT COMMENT '进度 (0-100)',
  `model` VARCHAR(255) COMMENT '模型',
  `request_hash` VARCHAR(255) COMMENT '请求哈希',
  `assistant_message_id` VARCHAR(255) COMMENT '助手消息ID',
  `started_at` DATETIME COMMENT '开始时间',
  `finished_at` DATETIME COMMENT '结束时间',
  `error_msg` TEXT COMMENT '错误信息',
  `create_time` DATETIME COMMENT '创建时间',
  `update_time` DATETIME COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent任务';