# 用户端 Agent 第二阶段上线与回退手册

## 发布顺序

1. 先执行 `V20260927_01__add_user_public_knowledge_release.sql`，确认 Flyway 成功并检查用户/管理端表族隔离。
2. 开启管理端公共知识发布控制面，创建草稿、绑定文档、审核后发布并创建 `USER_CHAT` 绑定；创建人与审核人可以相同。
3. 仅打开 `USER_AGENT_PUBLIC_RAG_ENABLED`，观察检索命中率、无答案率、过期版本命中数和引用正确率。
4. 打开 `USER_AGENT_REMINDER_ENABLED`，观察催单限频、重复受理和通知失败。
5. 小流量打开 `USER_AGENT_CANCELLATION_ENABLED` 与 `USER_AGENT_AFTER_SALE_ENABLED`，重点观察确认过期、资源版本冲突和幂等重放。
6. Redis、Qdrant、Embedding 和 MySQL readiness 全部健康后，再将 `USER_AGENT_REDIS_RUNTIME_ENABLED` 切换为 `true` 并扩容 Worker。

## 回退

- 关闭单项 Flag 只停止新任务使用该能力，不删除发布版本、checkpoint、确认和审计数据。
- Redis 异常时关闭 `USER_AGENT_REDIS_RUNTIME_ENABLED`，保留 MySQL 任务事实和 SQLite 本地开发恢复路径。
- 知识污染或引用错误时下线当前绑定并回滚到上一审核发布版本；不得直接修改已发布 release 的文档绑定。
- 高风险业务出现重复执行告警时，立即关闭取消/售后 Flag，保留 Java 幂等键和业务唯一约束，待核验后重新灰度。

## 监控门槛

- 过期版本命中数必须为 0；跨用户拒绝和跨 Profile 拒绝不得下降。
- 取消、催单、售后成功率、确认接受/拒绝/过期比例和资源版本冲突数纳入发布看板。
- Redis Stream backlog、pending、租约丢失、SSE 回放和 checkpoint 保存/恢复失败需要持续采集。
