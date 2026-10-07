-- 只读运维视图统一展示双域任务；业务代码不得通过该视图写入。
CREATE OR REPLACE VIEW `agent_task_overview` AS
SELECT 'ADMIN' AS `agent_profile`, `task_id`, `session_id`,
       COALESCE(`actor_id`, `user_id`) AS `subject_id`, `status`, `create_time`, `finished_at`
FROM `admin_agent_task`
UNION ALL
SELECT 'USER' AS `agent_profile`, `task_id`, `session_id`,
       `user_id` AS `subject_id`, `status`, `create_time`, `finished_at`
FROM `user_agent_task`;
