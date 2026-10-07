-- 公共知识文档采用软删除；文件哈希只用于拦截当前有效文档的重复上传。
-- 原唯一索引会让已删除文件永远无法重新上传，因此改为普通查询索引。
ALTER TABLE `user_agent_knowledge_document`
    DROP INDEX `uk_user_agent_document_hash_version`;

CREATE INDEX `idx_user_agent_document_hash`
    ON `user_agent_knowledge_document` (`kb_id`, `file_hash`);
