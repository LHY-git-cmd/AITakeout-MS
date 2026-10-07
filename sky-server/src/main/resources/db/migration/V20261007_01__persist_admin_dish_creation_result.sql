-- 菜品批量创建结果和确认状态在同一事务持久化，防止重试重复创建。
ALTER TABLE admin_agent_tool_confirmation ADD COLUMN result_json JSON NULL;
