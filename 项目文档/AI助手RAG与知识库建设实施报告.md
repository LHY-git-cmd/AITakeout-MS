# AI 助手 RAG 与知识库建设实施报告

## 1. 报告信息

| 项目 | 内容 |
| --- | --- |
| 报告名称 | AI 助手 RAG 与知识库建设实施报告 |
| 报告版本 | V1.0 |
| 编写日期 | 2026-09-05 |
| Java 项目 | `E:\code\web-sky-project\skyproject\sky-take-out` |
| Python 项目 | `E:\Pyhon\FastAPIProject1` |
| 建设范围 | 知识库管理、文档解析与索引、RAG 检索问答、引用溯源 |
| 当前结论 | 基础模块已建立，但 Java 对话主链路尚未闭环，暂不具备完整端到端上线条件 |

## 2. 建设背景

现有 AI 助手已经具备多轮对话、会话历史摘要、上下文裁剪、异步任务、任务幂等和 SSE 流式响应能力，但模型回答主要依赖通用模型知识和会话上下文，无法稳定使用企业内部文档中的事实。

本次建设在现有对话链路上增加 RAG（Retrieval-Augmented Generation，检索增强生成）和知识库能力，目标是让管理员能够上传企业文档，并让用户在对话时显式选择一个有权限的知识库。系统先检索相关文档片段，再将证据交给模型生成答案，同时返回可追溯的引用来源。

该能力主要解决以下问题：

- 企业制度、业务手册等私有信息无法直接被通用模型获取。
- 模型仅凭自身知识回答时容易产生事实错误或过期内容。
- 回答缺少文件、页码和原文片段，用户无法验证依据。
- 文档更新、停用或删除后，旧内容可能继续参与回答。
- 网络波动和任务重试可能导致重复索引或任务状态失踪。

## 3. 建设目标

第一版 RAG 与知识库建设目标如下：

1. 支持知识库的创建、查询、编辑、停用和删除。
2. 支持 PDF、DOCX、TXT 和 Markdown 文档上传。
3. 支持文档解析、清洗、分块、向量化和索引状态跟踪。
4. 支持文档哈希去重、失败重试和版本管理。
5. 支持会话显式绑定一个知识库，禁止对话过程中越权切换。
6. 检索时强制限定知识库、文档和有效版本范围。
7. 模型回答携带文件名、页码、引用片段和相关度分数。
8. 无可靠证据时明确拒答，不允许降级为无依据回答。
9. 原始文件必须通过 Java 权限校验后访问。
10. RAG 故障不能影响不使用知识库的普通 AI 对话。

## 4. 总体架构

系统采用 Java、Python、MySQL 和 Qdrant 分工协作的方式建设。

```text
管理端上传文档
    -> Java 校验用户权限、文件类型、大小和哈希
    -> Java 保存原始文件及 MySQL 元数据
    -> Java 创建索引任务并调用 Python
    -> Python 解析、清洗和分块
    -> Python 生成 Embedding
    -> Python 将向量和元数据写入 Qdrant
    -> Java轮询任务状态并激活有效文档版本

用户发起知识库对话
    -> Java 校验会话和知识库权限
    -> Java 确定允许检索的文档及有效版本
    -> Python 按 kb_id、document_id、version 检索 Qdrant
    -> Python 过滤低相关度证据并构建 RAG Prompt
    -> 模型依据证据生成回答
    -> Python 返回回答和 citations
    -> Java 持久化消息及引用
    -> 前端展示答案、来源和原文入口
```

各服务职责如下：

| 服务 | 主要职责 |
| --- | --- |
| Vue 管理端 | 知识库管理、文档上传、索引状态展示、知识库选择和引用展示 |
| Java 服务 | 身份认证、权限控制、元数据管理、文件访问、索引调度、会话绑定和引用持久化 |
| Python 服务 | 文档解析、分块、Embedding、向量写入、向量检索和 RAG Prompt 编排 |
| MySQL | 保存知识库、文档版本、索引任务、会话绑定和引用记录 |
| Qdrant | 保存文档分块向量及检索过滤所需的 Payload |

## 5. 已完成建设内容

### 5.1 数据库与数据模型

数据库迁移脚本：

`sky-server/src/main/resources/db/migration/V20260904_01__add_rag_knowledge_base.sql`

迁移已由 Flyway 成功执行，当前数据库存在以下四张表：

- `agent_knowledge_base`：知识库基本信息和启停状态。
- `agent_knowledge_document`：文档、文件哈希、版本、有效版本和处理状态。
- `agent_knowledge_index_task`：索引任务、请求哈希、进度和错误信息。
- `agent_message_citation`：AI 消息与知识库证据之间的引用关系。

同时，`agent_session` 已增加可选字段 `kb_id`，用于记录会话绑定的知识库。

数据模型包含以下关键约束：

- `kb_id` 唯一。
- `(document_id, version)` 唯一，支持同一文档保存多个版本。
- `(kb_id, file_hash, version)` 唯一，限制重复文档版本。
- 索引任务 `task_id` 唯一。
- `(message_id, chunk_id)` 唯一，避免同一引用重复落库。

### 5.2 Java 知识库管理模块

已新增以下主要代码：

- `AgentKnowledgeBase`、`AgentKnowledgeDocument`、`AgentKnowledgeIndexTask`、`AgentMessageCitation` 实体。
- `KnowledgeBaseDTO` 请求模型。
- `AgentKnowledgeMapper` 和 `AgentCitationMapper` 数据访问接口。
- `AgentKnowledgeService` 知识库业务服务。
- `AgentKnowledgeController` 管理端接口。

当前管理模块已经实现：

- 按当前登录用户创建和查询知识库。
- 更新知识库名称、描述、模型、分块策略和状态。
- 逻辑删除知识库和文档。
- PDF、DOCX、TXT、Markdown 文件类型白名单。
- 可配置的 20 MB 单文件大小限制。
- PDF `%PDF-` 和 DOCX ZIP 文件头校验。
- 文本文件二进制 NUL 字节检查。
- SHA-256 文件哈希和知识库内重复文件检测。
- 系统生成的文件存储路径，避免直接使用客户端文件路径。
- 文档原文通过 Java 鉴权接口下载。
- 索引任务创建、状态轮询、失败记录和启动恢复。
- 新版本索引成功后切换 `active_version`。
- 文档或知识库删除后异步清理向量。

已规划的 Java 管理端接口包括：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/admin/agent/knowledge-bases` | 创建知识库 |
| GET | `/admin/agent/knowledge-bases` | 查询知识库列表 |
| PUT | `/admin/agent/knowledge-bases/{kbId}` | 更新知识库 |
| DELETE | `/admin/agent/knowledge-bases/{kbId}` | 删除知识库 |
| POST | `/admin/agent/knowledge-bases/{kbId}/documents` | 上传文档 |
| GET | `/admin/agent/knowledge-bases/{kbId}/documents` | 查询文档列表 |
| POST | `/admin/agent/documents/{documentId}/versions` | 上传文档新版本 |
| POST | `/admin/agent/documents/{documentId}/reindex` | 重建索引 |
| DELETE | `/admin/agent/documents/{documentId}` | 删除文档 |
| GET | `/admin/agent/index-tasks/{taskId}` | 查询索引状态 |
| GET | `/admin/agent/documents/{documentId}/content` | 鉴权下载原文 |

### 5.3 Python 文档解析与索引模块

Python 项目新增 `app/knowledge` 模块，主要包含：

- `parser.py`：文档解析。
- `chunker.py`：结构化分块。
- `embedding.py`：Embedding Provider。
- `vector_store.py`：内存和 Qdrant 向量存储适配器。
- `service.py`：索引任务、检索和恢复服务。

已实现的解析能力：

| 格式 | 实现方式 | 保留信息 |
| --- | --- | --- |
| PDF | `pypdf` | 页码和页面文本 |
| DOCX | `python-docx` | 标题层级和段落 |
| TXT | UTF-8、UTF-8 BOM、GB18030 依次尝试 | 原始文本结构 |
| Markdown | 标题语法识别 | 标题层级和正文 |

分块器当前以结构边界为优先，默认目标长度约 1,200 字符，重叠约 200 字符。每个分块均生成：

- 稳定的 `chunk_id`。
- SHA-256 `content_hash`。
- `kb_id`。
- `document_id`。
- `document_version`。
- 文件名、标题路径、页码和正文。
- `enabled` 状态。

索引任务通过本地 SQLite 保存状态，支持：

- `task_id + request_hash` 幂等校验。
- 相同任务重复提交时复用原任务。
- 相同任务 ID 携带不同参数时拒绝执行。
- Python 重启后恢复未完成索引任务。
- 索引前删除同一文档版本的旧向量，避免重复数据。
- 记录任务状态、进度、分块数和失败原因。

### 5.4 Python RAG 检索与生成

Python 对话请求模型已增加可选 `knowledge` 字段，任务队列可以保存并传递知识库检索范围。

当前 RAG 处理流程包括：

1. 根据用户问题生成查询向量。
2. 强制按 `kb_id`、文档 ID、文档版本和 `enabled` 过滤。
3. 最多召回 12 个候选分块。
4. 按相关度阈值过滤低质量结果。
5. 按内容哈希去重。
6. 最多选择 8 个证据分块进入 Prompt。
7. 在系统提示中声明知识库内容只能作为资料，不能覆盖系统指令。
8. 没有可靠证据时返回统一拒答信息。
9. 知识库服务异常时返回“知识库服务暂时不可用”，不生成无依据回答。
10. 最多返回 5 条引用，每条引用保留文件、页码、片段和分数。

Python 已提供以下接口：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/v1/knowledge/index` | 创建索引任务 |
| GET | `/api/v1/knowledge/index/{task_id}` | 查询索引状态 |
| DELETE | `/api/v1/knowledge/documents/{document_id}` | 删除文档向量 |
| POST | `/api/v1/knowledge/search` | 检索诊断 |
| POST | `/api/v1/agent/submit` | 接收带知识库范围的异步对话任务 |

### 5.5 前端页面

管理端已增加：

- 知识库列表和详情布局。
- 新建、启用、停用和删除知识库操作。
- 文档上传、文档列表和索引状态展示。
- 新版本上传、重新索引和删除操作。
- AI 对话页面的知识库选择框。
- 回答下方的引用文件、页码和原文片段展示。
- 通过 Java 鉴权接口打开引用原文。
- 实时 SSE 引用字段的 snake_case 与 camelCase 兼容转换。

前端已通过 TypeScript 检查和生产构建。

## 6. 当前完成状态

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| 数据库迁移 | 已完成 | Flyway `20260904.01` 已执行成功 |
| Java 知识库 CRUD | 基本完成 | Controller、Service、Mapper 和数据模型已存在 |
| 文件上传与安全校验 | 基本完成 | 已包含类型、大小、文件头、哈希和路径检查 |
| Python 四类文档解析 | 已完成基础版本 | 未包含扫描 PDF OCR 和复杂表格增强 |
| 分块与稳定 Chunk ID | 已完成基础版本 | 当前按字符近似 Token 长度 |
| Embedding 抽象 | 已完成接口 | 当前仅有 Hash Embedding 基线实现 |
| Qdrant 适配器 | 已完成代码 | 当前环境未启动 Qdrant，尚未真实联调 |
| 索引幂等与恢复 | 已完成基础版本 | Python 使用 SQLite，Java具有恢复调度代码 |
| Python RAG 编排 | 已完成基础版本 | 包含阈值、去重、拒答和提示注入防护 |
| Java 会话绑定知识库 | 未闭环 | 数据库有 `kb_id`，Java会话实体和提交 DTO 尚未同步 |
| Java 下发检索范围 | 未完成 | `AgentSubmitRequest` 尚无 `knowledge` 字段 |
| 引用持久化 | 未闭环 | 表和 Mapper 已建立，事件处理器尚未写入引用 |
| 前端引用展示 | 已完成界面 | 依赖 Java 对话链路返回引用后才能完整工作 |
| 端到端 RAG 验收 | 未完成 | Java 编译缺口、Qdrant 和真实 Embedding 尚未解决 |
| 离线效果评测 | 未开始 | 尚未提供 50～100 条标注业务问题 |

## 7. 测试与验证结果

### 7.1 已通过项目

- Python `unittest` 共 15 项测试通过。
- Python `compileall` 语法检查通过。
- 前端 Prettier 格式检查通过。
- 前端 TypeScript 类型检查通过。
- 前端生产构建通过。
- Java `sky-pojo` 和 `sky-common` 模块编译通过。
- Flyway 迁移记录为 `20260904.01 / success=1`。

Python 测试覆盖：

- Chunk ID 稳定性。
- 索引任务幂等。
- 同一任务 ID 参数冲突。
- 知识库过滤。
- 文档版本过滤。
- 空有效版本范围禁止全库检索。
- 无证据拒答。
- 引用数量限制。
- 文档提示注入防护提示。
- 检索服务故障隔离。
- Agent 任务重复提交、并发重试和重启恢复。

### 7.2 尚未通过项目

完整执行 `sky-server` 编译时，存在以下三个缺失方法：

```text
AgentClient.indexKnowledge(...)
AgentClient.getKnowledgeIndexStatus(...)
AgentClient.deleteKnowledgeDocument(...)
```

这意味着 Java 知识库服务与 Python 知识库接口之间的 HTTP 客户端尚未闭环，当前不能将 Java 管理端视为可发布状态。

### 7.3 尚未执行的验证

- Java 上传文档到 Qdrant 索引完成的真实全链路。
- Qdrant 按知识库和文档版本过滤的真实接口测试。
- 文档新版本完成后切换和旧版本清理测试。
- RAG 回答通过 SSE 返回并在 Java 持久化引用的测试。
- 未授权知识库查询和引用下载的完整权限测试。
- 使用真实 Embedding 模型的召回效果评测。
- 50～100 条真实业务问题的离线评测。

## 8. 当前关键缺口

### 8.1 Java HTTP 客户端缺口

`AgentKnowledgeService` 已调用 Python 索引、状态和删除接口，但 `AgentClient` 尚未实现对应方法。需要补齐接口地址、请求序列化、超时策略和异常映射。

### 8.2 Java 对话请求未携带知识库范围

当前存在以下不一致：

- 前端提交了 `kbId`。
- Python `AgentRequest` 支持 `knowledge`。
- 数据库 `agent_session` 存在 `kb_id`。
- Java `AgentSubmitDTO` 尚无 `kbId`。
- Java `AgentSubmitRequest` 尚无 `knowledge`。
- Java `AgentSession` 和 `AgentSessionVO` 尚无 `kbId`。

因此，前端选择的知识库目前不能通过 Java 权限校验后安全地下发到 Python。

### 8.3 引用持久化链路未接通

数据库表、引用实体和 Mapper 已存在，但 Java `AgentEventProcessor` 尚未解析 `task_end.citations` 并写入 `agent_message_citation`；`AgentMessageVO` 也尚未携带引用列表。

### 8.4 缺少真实语义 Embedding

当前 `HashEmbeddingProvider` 适合本地流程测试，不具备生产级语义召回质量。若直接用于业务知识库，无法可靠达到 Top 5 命中率等效果目标。

### 8.5 Qdrant 尚未部署和联调

当前本机未监听 6333 端口。Qdrant REST 请求结构虽然已经实现，但仍需结合实际部署版本验证 Collection 创建、Payload Filter、Upsert、Search 和 Delete 行为。

## 9. 安全与一致性设计

现有设计已经体现以下原则：

- Java/MySQL 是知识库权限、文档状态和有效版本的权威来源。
- Python 不应直接接受前端传入的任意知识库范围。
- Qdrant 查询必须带 `kb_id` 和文档版本过滤。
- 有效版本范围为空时禁止退化为历史数据全量检索。
- 知识库资料不得覆盖系统指令。
- 原始文件通过 Java 鉴权接口访问。
- 删除文档时先修改 MySQL 状态，再异步清理向量。
- 新版本索引成功前继续保留旧有效版本。
- 索引任务通过 `task_id + request_hash` 防止重复执行。

仍需通过集成测试确认：

- 用户不能绑定或查询其他用户的知识库。
- 修改请求中的 `kbId` 不能绕过 Java 权限校验。
- 删除或停用文档后不能继续召回。
- 引用下载接口不能访问其他知识库的文件。
- Java 重启和 Python 重启期间不会重复建立同一索引。

## 10. 后续实施建议

建议按照以下顺序闭环：

1. 在 `AgentClient` 中补齐 Python 知识库索引、状态查询和向量删除方法，恢复 Java 全量编译。
2. 为 `AgentSubmitDTO`、`AgentSubmitRequest`、`AgentSession` 和相关 VO 增加知识库字段。
3. 在 `AgentServiceImpl` 中完成知识库归属、启用状态和会话绑定校验。
4. Java 只向 Python 下发 READY 文档的 `document_id -> active_version` 范围。
5. 在 `AgentEventProcessor` 中解析并持久化 citations，同时让会话详情返回引用。
6. 增加 Java 的权限、版本切换、引用持久化和故障隔离测试。
7. 部署 Qdrant，并完成真实索引、检索和删除联调。
8. 接入生产级 Embedding Provider，并记录模型名称和向量维度。
9. 建立 50～100 条真实业务问题评测集，验证召回、引用和拒答效果。
10. 完成灰度开关、监控指标和回滚演练后再开放生产使用。

## 11. 建议验收标准

功能完成后，应至少满足以下标准：

- 知识库和文档管理接口可以正常使用，未授权用户访问被拒绝。
- 四类文档均能异步解析、分块并写入 Qdrant。
- 同一个索引 `task_id` 并发提交多次时最多执行一次。
- 会话首次可以绑定一个知识库，后续不能切换到其他知识库。
- Java 下发的检索范围只包含 READY 文档及其有效版本。
- 无检索证据时明确拒答。
- Qdrant 不可用时返回明确错误，不生成无依据回答。
- 回答引用能够定位文件、页码和原文片段。
- 删除、停用和更新文档后，检索结果符合 MySQL 最新状态。
- Java、Python、前端单元测试和集成测试全部通过。
- 正确证据进入 Top 5 的比例达到约定目标。
- 越权检索和禁用文档召回数量均为 0。

## 12. 结论

本次建设已经形成了知识库数据模型、Java 管理模块、Python 文档处理与 RAG 基础能力，以及前端知识库管理和引用展示页面。整体架构方向合理，索引幂等、文档版本、无证据拒答和权限边界等关键设计也已经纳入实现。

但当前仍属于“基础模块已完成、主链路待闭环”的阶段。Java 对话请求、知识库权限范围下发、引用持久化和 `AgentClient` 接口尚未完整连接；Qdrant 与真实 Embedding 也未完成环境联调和效果评测。因此，当前版本适合继续开发和集成验证，不建议直接作为完整 RAG 功能发布到生产环境。

完成第 10 节列出的关键闭环工作，并通过真实 Qdrant、权限和效果评测后，才能达到第一版 RAG 与知识库功能的正式完成标准。
