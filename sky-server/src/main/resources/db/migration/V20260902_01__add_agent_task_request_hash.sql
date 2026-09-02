ALTER TABLE `agent_task`
    ADD COLUMN `request_hash` varchar(64) NULL COMMENT '任务请求参数SHA-256指纹' AFTER `model`;
