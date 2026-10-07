-- 取消用户公共知识文档的人工审核：历史可用文档直接具备加入发布版本的资格。
UPDATE `user_agent_knowledge_document`
SET `review_status` = 'APPROVED',
    `reviewed_by` = NULL,
    `reviewed_at` = COALESCE(`reviewed_at`, `update_time`)
WHERE `status` <> 6
  AND `review_status` <> 'APPROVED';
