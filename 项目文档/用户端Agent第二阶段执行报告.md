# 用户端 Agent 第二阶段执行报告

## 已交付

- 公共知识发布数据模型：分类、生命周期、有效期、不可变 release、文档版本绑定、场景绑定、审核和发布审计。
- 管理端公共知识控制面：`PUBLIC_KB_READ/EDIT/REVIEW/PUBLISH` 权限，以及草稿、单管理员审核、发布、下线、回滚和场景绑定接口。
- 公共知识内容导入控制面：公共知识库创建/查询/更新、文档上传与新版本、索引状态、重建和草稿删除均使用 `user_agent_knowledge_*` 表族，文件与内部知识库存储目录隔离；文档索引成功即可进入发布版本，无需单独人工审核。
- 管理端独立页面：新增“用户公共知识库”菜单，提供知识库、文档/索引和发布版本三层工作区，覆盖上传、发布版本审核、发布、场景绑定、下线与回滚，并包含加载、错误和响应式状态。
- 用户 RAG 数据面：Java 根据 `USER_CHAT` 有效绑定生成 `KnowledgeScope`；Python/Qdrant 按发布版本、文档版本、分类和有效期过滤，缺少证据时拒答；前端展示引用。
- Profile 与发布元数据隔离：知识索引 API 显式选择 `ADMIN_ASSISTANT` 或 `USER_ASSISTANT`；公共文档固定写入 `sky_user_public_knowledge`，向量使用可并存的 `release_ids` 元数据，发布和下线同步增删发布范围。
- 发布安全约束：文档完成索引即可进入 release；普通管理员可维护内容和发布草稿，超级管理员可审核和发布本人创建的版本；一个场景只保留一个活动绑定，下线不自动回落旧版本，回滚恢复原绑定。
- 用户业务工具：取消订单、催单、售后申请、操作预览；取消和售后使用用户专属确认表、参数哈希、资源版本、单次执行和审计；催单使用 Redis 五分钟原子冷却键。
- 工作流：用户意图置信度、槽位缺失、实时工具/公共RAG/HYBRID计划，以及澄清、操作预览和结果核验事件。
- Redis 运行时：任务状态 Hash、任务 Stream、事件 Stream、消费组读取接口、租约、取消信号和可选 readiness 检查；默认关闭，兼容本地 SQLite/内存模式。
- 前端和运营：引用、澄清、确认卡、灰度开关、上线与回退手册。

## 验证结果

- Java：`mvn -am -pl sky-server test`，144 tests，0 failures，0 errors。
- Java 重点迁移/契约/用户工具测试：通过。
- Python：98 passed，1 skipped，5 subtests passed（覆盖上传→索引→发布→检索→下线链路；另有 1 条第三方 TestClient 弃用警告）。
- 管理端前端：9 suites / 73 tests 全部通过；兼容包生产构建成功（仅保留 Element UI/Sass 既有弃用与包体积警告）。
- 用户端前端：14 test files，23 tests passed；生产构建成功。
- Flyway H2 迁移：`V20260927_01__add_user_public_knowledge_release.sql` 验证通过。

## 部署注意

- 生产安装 `agent-service/requirements.txt` 中新增的 `redis` 依赖。
- 先执行数据库迁移，再按上线手册逐项打开 Feature Flag。
- `USER_AGENT_REDIS_RUNTIME_ENABLED` 只有在 Redis、MySQL、Qdrant 和 Embedding readiness 全部健康后再打开。
- 当前业务主体仍为单主体模型；多主体隔离未纳入本阶段。
