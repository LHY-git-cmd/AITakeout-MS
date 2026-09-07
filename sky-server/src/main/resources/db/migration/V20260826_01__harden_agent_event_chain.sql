ALTER TABLE `agent_event`
    ADD UNIQUE KEY `uk_agent_event_task_seq` (`task_id`, `seq_no`);

ALTER TABLE `agent_message`
    ADD UNIQUE KEY `uk_agent_message_task_role` (`task_id`, `role`);
