-- 将原先绑定管理员user_id/employee_id的Agent数据模型升级为通用主体模型。
-- 使用20260922.02，避免与既有售后迁移V20260921.01发生版本冲突。
ALTER TABLE `agent_session`
    ADD COLUMN `actor_type` VARCHAR(16) NOT NULL DEFAULT 'ADMIN' COMMENT '主体类型：ADMIN/USER/SYSTEM' AFTER `user_id`,
    ADD COLUMN `actor_id` BIGINT DEFAULT NULL COMMENT '主体ID' AFTER `actor_type`;

UPDATE `agent_session`
SET `actor_type` = CASE WHEN `user_id` IS NULL THEN 'SYSTEM' ELSE 'ADMIN' END,
    `actor_id` = COALESCE(`user_id`, 0);

ALTER TABLE `agent_session`
    MODIFY COLUMN `actor_id` BIGINT NOT NULL COMMENT '主体ID',
    ADD CONSTRAINT `ck_agent_session_actor_type`
        CHECK (`actor_type` IN ('ADMIN', 'USER', 'SYSTEM')),
    ADD KEY `idx_agent_session_actor_time` (`actor_type`, `actor_id`, `update_time`);

ALTER TABLE `agent_task`
    ADD COLUMN `actor_type` VARCHAR(16) NOT NULL DEFAULT 'ADMIN' COMMENT '主体类型：ADMIN/USER/SYSTEM' AFTER `user_id`,
    ADD COLUMN `actor_id` BIGINT DEFAULT NULL COMMENT '主体ID' AFTER `actor_type`;

UPDATE `agent_task`
SET `actor_type` = CASE WHEN `user_id` IS NULL THEN 'SYSTEM' ELSE 'ADMIN' END,
    `actor_id` = COALESCE(`user_id`, 0);

ALTER TABLE `agent_task`
    MODIFY COLUMN `actor_id` BIGINT NOT NULL COMMENT '主体ID',
    DROP CHECK `ck_agent_task_actor_role`,
    ADD CONSTRAINT `ck_agent_task_actor_type`
        CHECK (`actor_type` IN ('ADMIN', 'USER', 'SYSTEM')),
    ADD CONSTRAINT `ck_agent_task_actor_role`
        CHECK (`actor_role` IN ('SUPER_ADMIN', 'ADMIN', 'CUSTOMER', 'SYSTEM')),
    ADD KEY `idx_agent_task_actor_time` (`actor_type`, `actor_id`, `create_time`);

ALTER TABLE `agent_tool_audit`
    ADD COLUMN `actor_type` VARCHAR(16) DEFAULT NULL COMMENT '主体类型：ADMIN/USER/SYSTEM' AFTER `employee_id`,
    ADD COLUMN `actor_id` BIGINT DEFAULT NULL COMMENT '主体ID' AFTER `actor_type`;

UPDATE `agent_tool_audit`
SET `actor_type` = CASE WHEN `employee_id` IS NULL THEN NULL ELSE 'ADMIN' END,
    `actor_id` = `employee_id`;

ALTER TABLE `agent_tool_audit`
    ADD CONSTRAINT `ck_agent_tool_audit_actor_type`
        CHECK (`actor_type` IS NULL OR `actor_type` IN ('ADMIN', 'USER', 'SYSTEM')),
    ADD KEY `idx_agent_tool_audit_generic_actor_time`
        (`actor_type`, `actor_id`, `create_time`);

ALTER TABLE `agent_tool_confirmation`
    ADD COLUMN `actor_type` VARCHAR(16) NOT NULL DEFAULT 'ADMIN' COMMENT '主体类型：ADMIN/USER/SYSTEM' AFTER `employee_id`,
    ADD COLUMN `actor_id` BIGINT DEFAULT NULL COMMENT '主体ID' AFTER `actor_type`;

UPDATE `agent_tool_confirmation`
SET `actor_type` = 'ADMIN',
    `actor_id` = `employee_id`;

ALTER TABLE `agent_tool_confirmation`
    MODIFY COLUMN `actor_id` BIGINT NOT NULL COMMENT '主体ID',
    ADD CONSTRAINT `ck_agent_tool_confirmation_actor_type`
        CHECK (`actor_type` IN ('ADMIN', 'USER', 'SYSTEM')),
    ADD KEY `idx_agent_tool_confirmation_generic_actor`
        (`actor_type`, `actor_id`, `status`, `expires_at`);
