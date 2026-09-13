ALTER TABLE `employee`
    ADD COLUMN `role` VARCHAR(32) NOT NULL DEFAULT 'ADMIN' COMMENT '管理端角色：SUPER_ADMIN/ADMIN' AFTER `status`;

-- 仅用于首次迁移既有数据；迁移完成后，授权只读取role字段，不再依据用户名。
UPDATE `employee`
SET `role` = 'SUPER_ADMIN'
WHERE `username` = 'admin';

ALTER TABLE `employee`
    ADD CONSTRAINT `ck_employee_role` CHECK (`role` IN ('SUPER_ADMIN', 'ADMIN'));

ALTER TABLE `agent_task`
    ADD COLUMN `actor_role` VARCHAR(32) NOT NULL DEFAULT 'ADMIN' COMMENT '任务创建时管理员角色快照' AFTER `user_id`;

UPDATE `agent_task` task
LEFT JOIN `employee` employee ON employee.`id` = task.`user_id`
SET task.`actor_role` = COALESCE(employee.`role`, 'ADMIN');

ALTER TABLE `agent_task`
    ADD CONSTRAINT `ck_agent_task_actor_role` CHECK (`actor_role` IN ('SUPER_ADMIN', 'ADMIN'));
