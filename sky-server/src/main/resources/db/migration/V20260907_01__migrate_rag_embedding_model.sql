UPDATE `agent_knowledge_base`
SET `embedding_model` = 'BAAI/bge-m3', `update_time` = NOW()
WHERE `embedding_model` = 'hash-384';
