# Agent 双域存储完全隔离实施报告

## 1. 实施结论

用户端 Agent 与管理端 Agent 已完成双域隔离。现有数据按已确认前提全部保留为管理端数据，用户端从空表和独立运行时存储开始。已执行的 `V20260922_02__generalize_agent_actor_identity.sql` 未被修改。

隔离边界覆盖：MySQL 表族、Java Mapper/Service/事件处理、Python Profile/队列/SQLite、Redis 消息前缀、LangGraph 实例、工具 Registry、知识任务状态、知识源目录和 Qdrant 集合。

## 2. 数据库结果

- 新迁移 `V20260926_01__split_admin_user_agent_storage.sql` 将 11 张原 `agent_*` 表重命名为 `admin_agent_*`，保留原数据、主键和业务标识。
- 迁移执行前通过临时 CHECK 守卫验证旧数据全部是管理端数据，发现非管理数据时会中止。
- 同次迁移创建 11 张空的 `user_agent_*` 表及各自索引和约束。
- `V20260926_02__create_agent_operational_views.sql` 创建只读运维视图 `agent_task_overview`；业务写入不依赖该视图。
- 迁移兼容尚未执行的 `V20260922_03__allow_null_legacy_agent_user_id.sql`。
- H2 迁移测试验证：管理端历史会话、任务各保留 1 行，用户端为 0 行，旧表名消失，运维视图可读。

## 3. Java 边界

- 管理端 Mapper 只访问 `admin_agent_*`，用户端 `mapper/user` 只访问 `user_agent_*`。
- 管理 Controller 依赖 `AdminAgentService`，用户 Controller 依赖 `UserAgentService`。
- 两端分别使用事件处理器、SSE 服务、摘要服务和消息缓存前缀。
- 普通用户工具的任务、确认和审计路由到用户表；管理工具继续路由到管理表。
- Python 状态、取消和 SSE 订阅均携带 `agent_profile`，即使两个域出现同名 task ID 也不会跨队列命中。
- 管理端拒绝普通用户主体；用户主体只能使用 `USER_ASSISTANT/CUSTOMER` 组合。

## 4. Python 运行时边界

- 两个 Profile：`ADMIN_ASSISTANT`、`USER_ASSISTANT`。
- 两套基础 system prompt、Tool Registry、ToolOrchestrator 和 LangGraph 工作流实例。
- 两个任务状态库：`admin_agent_tasks.sqlite3`、`user_agent_tasks.sqlite3`。
- Profile、actor type 和角色不匹配时，在任务进入队列前拒绝。
- 状态、SSE、取消和任务列表只查询显式指定的 Profile 队列。
- 用户 Registry 物理排除管理工具；管理 Registry 不注册购物车、用户订单等用户专属工具。

## 5. 缓存与知识库边界

- Redis 消息前缀：`sky:admin-agent:messages:` 与 `sky:user-agent:messages:`。
- 知识任务 SQLite、知识源目录和 KnowledgeService 均为双实例。
- Qdrant 集合：管理端 `sky_admin_internal_knowledge`，用户端 `sky_user_public_knowledge`。
- 提供 `scripts/copy_legacy_qdrant_to_admin.py`，默认仅预检，必须显式传 `--execute` 才复制旧集合；脚本不会删除或修改源集合。
- 用户公共知识存储已经独立，但公共知识发布入口未开放。上线用户知识问答前，应由受控管理流程发布内容并由 Java 绑定允许的公共 `kb_id`；不得允许用户请求直接指定任意知识库。

## 6. 自动化验证

- Python：94 passed、1 skipped、5 subtests passed。
- Java/Maven：137 tests，0 failures，0 errors，`BUILD SUCCESS`。
- 用户前端：14 个测试文件、23 项测试全部通过；生产构建成功。
- 管理前端：8 个测试套件、70 项测试全部通过；生产构建成功。
- 用户 Agent 确定性评测：12/12 通过。
- `git diff --check` 无内容错误；仅存在工作区换行符提示。

## 7. 部署顺序

1. 备份 MySQL、Qdrant 和 Agent SQLite 数据；暂停新 Agent 任务并等待运行中任务结束。
2. 确认线上 Flyway 已记录 `V20260922_02`，按顺序执行 `V20260922_03`、`V20260926_01`、`V20260926_02`。
3. 使用复制脚本把旧 `sky_knowledge_v2` 内容复制到管理集合并核对点数；保留旧集合。
4. 同一发布窗口部署 Java 服务、Python Agent 和两端前端，并应用新的双域环境变量。
5. 分别验证管理端历史会话、用户端新会话、SSE 回放、取消、工具确认和审计。

旧应用仍访问 `agent_*` 表，因此数据库迁移后不能单独回退到旧应用版本。若新表已经产生业务数据，只允许前滚修复，不执行破坏性表名回退。

## 8. 尚未开放的能力

- 用户公共知识发布与版本绑定入口；当前只完成安全的独立存储基础设施。
- 多实例持久化 LangGraph checkpoint；当前双实例已隔离，但生产多副本仍建议接入两个 Redis namespace。
- 用户支付、下单、取消订单和创建售后申请；后续开放时必须接入确认、审计和幂等门禁。
