-- 通用主体使用actor_type/actor_id标识归属；旧user_id仅兼容管理端历史数据。
-- 用户端Agent没有对应的管理员ID，因此会话和任务的旧字段必须允许为空。
ALTER TABLE `agent_session`
    MODIFY COLUMN `user_id` BIGINT NULL COMMENT '历史管理端主体ID，用户端主体为空';

ALTER TABLE `agent_task`
    MODIFY COLUMN `user_id` BIGINT NULL COMMENT '历史管理端主体ID，用户端主体为空';
