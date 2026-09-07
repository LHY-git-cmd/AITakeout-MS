ALTER TABLE `agent_message`
    ADD UNIQUE KEY `uk_agent_message_session_seq` (`session_id`, `seq_no`);
