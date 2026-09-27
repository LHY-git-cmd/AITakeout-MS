# Agent 双域存储完全隔离实施计划书

## 1. 需求结论

本次改造将用户端 Agent 与管理端 Agent 从共享的多主体数据模型改为两个独立业务域。

已确认前提：

- `V20260922_02__generalize_agent_actor_identity.sql` 已经执行，不允许修改、删除或回滚该迁移；
- 现有 `agent_*` 表中的业务数据全部属于管理端；
- 用户端和管理端需要完整隔离，包括 MySQL、Java 持久化与服务、Python 任务状态、Redis/LangGraph 命名空间和 Qdrant 集合；
- 两端仍共享 LLM Gateway、SSE 协议基础组件、上下文裁剪算法、通用指标与底层工具调用框架；
- 当前工作区存在未提交修改，实施时必须保留并兼容这些修改。

## 2. 技术选型声明

采用“同一 MySQL Schema 下的双表族”作为当前阶段的物理边界：

```text
sky_take_out
├── admin_agent_*    管理端 Agent 数据
└── user_agent_*     用户端 Agent 数据
```

，暂不拆成两个数据库实例原因是当前项目仍为同一 Spring Boot 应用，使用同库可以保持部署、备份和事务管理简单，同时已经能消除业务表、Mapper 和查询条件的交叉访问。未来如果需要独立扩缩容，可在领域 Repository 已拆分的基础上迁移数据源。

三条最关键的改进建议：

1. **表名即安全边界**：不再依赖 `actor_type` 作为主要隔离条件；用户代码只能注入 `UserAgent*Mapper`，管理代码只能注入 `AdminAgent*Mapper`。
2. **迁移只向前追加**：保留所有已执行迁移，通过新 Flyway 版本完成管理表重命名、用户表创建和数据校验；不得编辑 `V20260922_02`。
3. **存储隔离必须贯穿运行时**：MySQL 分表的同时隔离 Redis key、LangGraph checkpoint、Python 任务状态和 Qdrant collection，避免数据库之外重新发生上下文串域。

## 3. 目标数据模型

### 3.1 管理端表族

现有数据原地迁移到以下表：

```text
admin_agent_session
admin_agent_message
admin_agent_session_summary
admin_agent_task
admin_agent_event
admin_agent_message_citation
admin_agent_tool_confirmation
admin_agent_tool_audit
admin_agent_knowledge_base
admin_agent_knowledge_document
admin_agent_knowledge_index_task
```

管理端使用明确字段：

- `employee_id`：管理员主体；
- `role_snapshot`：任务或确认创建时的角色快照；
- `kb_id`：管理端内部知识库；
- `model`：管理端允许选择的模型。

管理端代码不再通过 `actor_type = ADMIN` 查询数据。

### 3.2 用户端表族

新建以下空表：

```text
user_agent_session
user_agent_message
user_agent_session_summary
user_agent_task
user_agent_event
user_agent_message_citation
user_agent_tool_confirmation
user_agent_tool_audit
user_agent_knowledge_base
user_agent_knowledge_document
user_agent_knowledge_index_task
```

用户端使用明确字段：

- `user_id`：普通用户主体；
- `intent`：推荐、购物车、订单、售后、知识问答等用户意图；
- `client_context_json`：经过大小限制的页面提示信息，不参与鉴权；
- `public_kb_version` 或发布状态：绑定经过审核的公共知识版本；
- 用户确认和审计表只允许记录用户工具操作。

### 3.3 标识与关联约束

- `session_id`、`task_id`、`message_id` 和 `confirmation_id` 继续使用全局随机业务 ID；
- 业务代码不得跨表族关联会话、任务或消息；
- 每个子域内部建立 `session_id + seq_no`、`task_id + seq_no`、`task_id + role` 等唯一约束；
- 不在迁移第一阶段引入跨表族外键，避免历史数据和异步事件处理被锁表影响；完整性由唯一索引、服务事务和迁移测试保证；
- 统一运维查询通过只读 `agent_task_overview` 视图完成，业务代码禁止依赖该视图写入。

## 4. Flyway 迁移策略

新增迁移，不修改任何已执行文件。预定迁移版本从当前日期开始，例如：

```text
V20260926_01__split_admin_user_agent_storage.sql
V20260926_02__create_agent_operational_views.sql
```

第一条迁移负责：

1. 校验现有核心数据不存在 `actor_type = USER`；若存在则中止迁移，禁止错误归档；
2. 将现有 11 张 `agent_*` 表原地重命名为 `admin_agent_*`，保留全部主键和历史数据；
3. 将管理端主体字段规范为 `employee_id`、`role_snapshot`，移除不再需要的通用主体索引和约束；
4. 创建 11 张全新的 `user_agent_*` 表；
5. 重建双表族各自的唯一索引、普通索引和 CHECK 约束；
6. 对迁移后的管理端表执行行数与关键业务 ID 一致性校验。

第二条迁移只创建只读运维视图，不参与业务写入。

`V20260922_03__allow_null_legacy_agent_user_id.sql` 当前是未提交迁移。实施时不假设其已执行：如果保留在迁移链中，新迁移必须兼容它先将旧字段改为可空，再完成管理端字段规范化；不得通过修改 `V20260922_02` 绕过该问题。

迁移不自动删除数据，不使用 `DROP TABLE agent_*` 搬迁数据；优先采用 `RENAME TABLE`，降低复制失败和数据遗漏风险。

## 5. Java 领域拆分

### 5.1 包结构

```text
com.sky.service.agent
├── shared
│   ├── ContextTrimmer
│   ├── AgentSseCodec
│   └── AgentRuntimeClient
├── admin
│   ├── AdminAgentService
│   ├── AdminAgentContextService
│   ├── AdminAgentSummaryService
│   ├── AdminAgentEventProcessor
│   └── AdminAgentToolExecutor
└── user
    ├── UserAgentService
    ├── UserAgentContextService
    ├── UserAgentSummaryService
    ├── UserAgentEventProcessor
    └── UserAgentToolExecutor
```

### 5.2 持久化层

为两个子域分别建立 Entity、Mapper 和 XML：

- `AdminAgentSessionMapper` 等 Mapper 只能访问 `admin_agent_*`；
- `UserAgentSessionMapper` 等 Mapper 只能访问 `user_agent_*`；
- Controller 不再共同调用包含主体分支的单一 `AgentServiceImpl`；
- 可共享纯算法类，但不得共享会访问业务表的 Repository 或 Service；
- 用户身份从用户 JWT 上下文获取，管理员身份从管理员 JWT 和员工角色获取。

### 5.3 事件与 SSE

- 用户和管理端分别持久化事件；
- 两套 EventProcessor 使用相同事件协议基础类型，但分别查询自己的 Task、Session 和 Message 表；
- SSE URL 保持现有外部接口兼容；
- `Last-Event-ID` 只在所属子域事件表中回放。

## 6. Python Runtime 隔离

### 6.1 Agent Profile

任务协议增加明确的 `agent_profile`：

```text
USER_ASSISTANT
ADMIN_ASSISTANT
```

Python 根据 Profile 选择：

- 独立 system prompt；
- 独立 Tool Registry；
- 独立 LangGraph workflow；
- 独立 checkpoint namespace；
- 独立任务存储。

不得继续使用一套“餐饮管理助手”基础提示词后再追加用户提示词。

### 6.2 任务状态

本地开发默认拆成：

```text
data/user_agent_tasks.sqlite3
data/admin_agent_tasks.sqlite3
```

任务队列实例、幂等哈希、事件历史和恢复逻辑分别管理。生产环境迁移到 Redis 或其他任务系统时使用独立 key prefix/queue。

### 6.3 LangGraph 与 Redis

```text
sky:user-agent:messages:{sessionId}
sky:user-agent:checkpoint:{sessionId}

sky:admin-agent:messages:{sessionId}
sky:admin-agent:checkpoint:{sessionId}
```

首期如果仍使用 `InMemorySaver`，也必须创建两个独立实例；生产部署前替换为持久化 checkpoint。

## 7. 知识库隔离

管理端现有知识数据迁移到：

```text
admin_agent_knowledge_base
admin_agent_knowledge_document
admin_agent_knowledge_index_task
Qdrant: sky_admin_internal_knowledge
```

用户端创建独立公共知识域：

```text
user_agent_knowledge_base
user_agent_knowledge_document
user_agent_knowledge_index_task
Qdrant: sky_user_public_knowledge
```

用户不能提交任意 `kb_id`，由服务端绑定已发布的公共知识版本。管理端知识库不得通过用户 API 查询或检索。

Qdrant 向新集合迁移时采用“重建索引 + 验证 + 切换配置”，不直接移动或删除旧集合；旧集合清理属于单独的运维动作，不纳入本次自动迁移。

## 8. 分模块实施步骤

### 模块一：数据库迁移与迁移测试

产出：

- 新 Flyway 迁移；
- 管理端历史数据校验；
- 用户端空表及约束；
- 运维只读视图；
- Testcontainers/MySQL 迁移集成测试。

验收：

- `V20260922_02` 后可以顺序升级；
- 管理端历史行数、业务 ID 和事件序列不变；
- 用户端表初始为空；
- 再次启动 Flyway 不重复迁移。

### 模块二：Java Entity、Mapper 与领域 Service 拆分

产出：

- 双表族实体和 Mapper；
- 用户/管理 Service 分离；
- 两套摘要、缓存和事件处理器；
- 旧通用主体分支退出业务主链路。

验收：

- 用户 API 的 SQL 只访问 `user_agent_*`；
- 管理 API 的 SQL 只访问 `admin_agent_*`；
- 两端原有 HTTP 契约保持兼容。

### 模块三：工具确认、审计和权限隔离

产出：

- 用户/管理确认表访问分离；
- 用户/管理审计表访问分离；
- 两套 Tool Executor 和明确工具白名单；
- 跨域资源访问拒绝测试。

验收：

- 用户 Agent 无法发现或执行管理工具；
- 管理端确认不会写入用户表；
- 所有写工具继续保持幂等和审计。

### 模块四：Python Agent Profile 与运行时状态隔离

产出：

- 两套基础提示词和 Tool Registry；
- 两套任务队列/任务存储；
- 两套 LangGraph checkpoint namespace；
- Profile 协议及回归测试。

验收：

- 同名 session/task 在两个 Profile 中不会读取彼此状态；
- 服务重启恢复只恢复对应队列；
- 用户工作流与管理工具编排互不暴露。

### 模块五：Redis 与知识库隔离

产出：

- 双 Redis key prefix；
- 用户公共知识库数据模型；
- 双 Qdrant collection 配置；
- 管理知识索引重建方案和用户公共知识发布入口边界。

验收：

- 用户检索请求只命中公共集合；
- 管理端只命中内部集合；
- 缓存清理不会影响另一个 Agent。

### 模块六：全链路回归、文档与上线检查

产出：

- Java、Python、前端测试摘要；
- 数据迁移核对报告；
- 回滚与故障处置说明；
- 最终实施报告。

验收：

- 用户端与管理端现有功能回归通过；
- 跨域读取和写入测试全部拒绝；
- SSE 回放、摘要、确认、审计和知识引用分别落入正确表族；
- 构建和确定性评测通过。

## 9. 测试策略

采用 TDD：先补充失败测试验证表族和主体边界，再实现迁移与代码。

重点测试：

- 从已执行 `V20260922_02` 的数据库快照升级；
- 管理端历史数据原地保留；
- 用户/管理会话 ID 碰撞时仍不串域；
- 两端任务、事件、摘要和引用独立；
- Redis key 和 checkpoint namespace 独立；
- 工具白名单与 Java 二次鉴权；
- 用户公共知识与管理内部知识隔离；
- SSE 断点续传和任务恢复。

Maven 完整输出写入 `runtime-logs`，只读取最终统计和失败对应的 Surefire 报告；Python、前端测试只返回最终统计或失败摘要，避免重复运行相同测试。

## 10. 上线与回退原则

上线前：

1. 对数据库和 Qdrant 集合做可验证备份；
2. 在数据库副本上执行迁移并核对表行数、唯一约束和关键 ID；
3. 暂停 Agent 新任务提交，等待运行中任务进入终态；
4. 执行 Flyway 迁移并部署同版本 Java/Python 服务；
5. 分别执行用户端和管理端冒烟测试后恢复流量。

回退原则：

- 应用回退必须配套兼容新表名的回退版本，不能简单部署仍访问 `agent_*` 的旧包；
- 不在自动回退脚本中删除用户端新表或 Qdrant 新集合；
- 如迁移后尚未产生新数据，可通过受控维护脚本把 `admin_agent_*` 重命名回旧表名；
- 如迁移后已经产生新数据，只允许前滚修复，不执行破坏性表名回退。

## 11. 非目标

本次不包括：

- 拆分为两个独立 MySQL 实例或两个 Spring Boot 应用；
- 开放用户端支付、下单、取消订单或售后申请；
- 自动删除旧 Qdrant 集合；
- 重做现有前端视觉设计；
- 改变对外 API URL 和主要 SSE 事件协议。

## 12. 完成定义

只有同时满足以下条件才视为完成：

- MySQL 11 类数据对象已经形成用户/管理双表族；
- Java 两端不存在共享业务 Mapper 或依靠 `actor_type` 过滤的主路径；
- Python Prompt、Registry、任务状态和 checkpoint 已隔离；
- Redis 和 Qdrant 使用独立命名空间；
- 管理端历史数据完整，用户端从独立空表开始；
- 自动化测试、迁移测试、构建和安全边界测试通过；
- 生成最终实施报告和上线核对清单。
