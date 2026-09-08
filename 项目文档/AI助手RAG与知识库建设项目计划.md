# AI 助手 RAG 与知识库建设项目计划

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | AI 助手 RAG 与知识库建设项目计划 |
| 文档版本 | V1.0 |
| 编写日期 | 2026-09-04 |
| Java 项目 | `E:\code\web-sky-project\skyproject\sky-take-out` |
| Python 项目 | `E:\Pyhon\FastAPIProject1` |
| 当前能力 | AI 多轮对话、会话历史摘要、上下文裁剪、异步任务与 SSE |
| 目标能力 | 知识库管理、文档索引、RAG 检索问答及引用溯源 |

## 2. 建设目标

在现有 AI 对话链路上增加企业知识库能力，使用户能够选择一个有权限的知识库进行提问，并获得基于文档证据生成、可追溯来源的回答。

第一版目标：

- 支持知识库创建、编辑、停用和删除。
- 支持 PDF、DOCX、TXT、Markdown 文档上传。
- 支持文档解析、清洗、分块、向量化和索引状态跟踪。
- 支持会话显式绑定一个知识库。
- 支持基于向量检索的 RAG 问答。
- 回答携带文件名、页码、引用片段和相关度信息。
- 知识不足时明确拒答，不允许模型伪造知识库内容。
- 确保知识库、文档和原文件访问均经过 Java 权限校验。

## 3. 范围边界

### 3.1 第一版包含

- 管理端知识库和文档管理。
- 单次对话显式选择一个知识库。
- PDF、DOCX、TXT、Markdown 文档解析。
- 结构化分块和基础向量检索。
- 文档版本、重复文件检测、重新索引和失败重试。
- RAG 回答及引用来源展示。
- 文档停用或删除后停止召回。
- 会话摘要、最近消息和 RAG 证据的统一上下文编排。

### 3.2 第一版不包含

- 扫描 PDF OCR。
- 图片、音频和视频知识抽取。
- 知识图谱。
- 多知识库自动路由。
- Agent 自主决定访问哪个知识库。
- 根据聊天内容自动写入知识库。
- Elasticsearch 全文检索、复杂混合检索和 Reranker。
- Python 多实例和分布式索引调度。
- 对知识库文档执行自动修改或审批。

## 4. 前提检查

- 会话历史摘要不等同于知识库。摘要描述“对话谈过什么”，知识库描述“企业文档中有什么事实”。
- RAG 不能保证答案天然正确，必须通过召回阈值、引用校验和无证据拒答降低幻觉。
- 向量数据库不是业务权威数据源。知识库、文档、版本、权限和处理状态以 Java/MySQL 为准。
- 原始文件不能由前端直接通过永久公开地址访问，应由 Java 鉴权后返回临时地址或代理下载。
- 文档更新不能原地覆盖旧索引，应先完成新版本索引，再切换有效版本。

## 5. 技术选型声明

### 5.1 核心选型

| 领域 | 选型 | 说明 |
| --- | --- | --- |
| 业务管理 | Java + Spring Boot | 复用现有认证、权限和管理端接口 |
| 元数据 | MySQL | 保存知识库、文档、版本、索引任务和引用记录 |
| 原始文件 | 现有 OSS，或本地开发目录 | 生产环境使用私有对象存储 |
| RAG 编排 | Python + FastAPI | 负责解析、切分、Embedding、检索和提示词拼装 |
| 向量数据库 | Qdrant | 部署简单，支持 payload 过滤和版本隔离 |
| 异步协议 | Java 调用 Python 任务接口 + SSE/状态查询 | 延续现有 Agent 任务模式 |

### 5.2 三条关键改进建议

1. 第一版由用户显式选择一个知识库，不做模型自动路由，降低越权检索和错误召回风险。
2. 所有检索结果必须携带 `kb_id`、`document_id`、`version` 和 `chunk_id`，生成答案后保留引用链路。
3. 文档新版本必须先完整建索引并通过校验，再原子切换 `active_version`，避免用户检索到半成品数据。

## 6. 总体架构

```text
管理端上传文档
    -> Java 鉴权并保存知识库、文档和索引任务元数据
    -> 原始文件写入私有对象存储
    -> Java 请求 Python 创建索引任务
    -> Python 下载文件、解析、清洗和分块
    -> Python 生成 Embedding 并写入 Qdrant
    -> Python 返回索引结果
    -> Java 更新文档状态和有效版本

用户发起知识库问答
    -> Java 校验用户、会话和知识库权限
    -> Java 组装会话摘要与最近消息
    -> Python 规范化问题并检索 Qdrant
    -> Python 根据阈值选择证据并构建 RAG Prompt
    -> 模型生成回答和引用
    -> Java 持久化回答、事件和引用记录
    -> 前端展示回答及可打开的来源
```

## 7. 服务职责

### 7.1 Java 服务

- 知识库、文档和版本 CRUD。
- 上传文件类型、大小、文件名和哈希校验。
- 用户、员工、租户及知识库权限校验。
- 保存索引任务状态和错误信息。
- 会话绑定知识库。
- 调用 Python 索引与 RAG 接口。
- 持久化回答引用。
- 签发原文临时访问地址或代理文件下载。
- 处理文档停用、删除和重新索引。

### 7.2 Python 服务

- 下载并解析文档。
- 文本清洗、结构识别和分块。
- 生成 Embedding。
- 写入、查询和删除 Qdrant 向量。
- 根据 `kb_id`、文档版本和启用状态进行过滤。
- 组装 RAG Prompt。
- 输出答案、引用和检索诊断信息。
- 提供索引任务状态与失败原因。

## 8. 数据模型

### 8.1 `agent_knowledge_base`

```text
id                  bigint PK
kb_id               varchar(64) UNIQUE
name                varchar(128)
description         varchar(500)
embedding_model     varchar(64)
chunk_strategy      varchar(32)
status              tinyint
create_user         bigint
update_user         bigint
create_time         datetime
update_time         datetime
```

状态建议：`1 ENABLED / 2 DISABLED / 3 DELETED`。

### 8.2 `agent_knowledge_document`

```text
id                  bigint PK
document_id         varchar(64) UNIQUE
kb_id               varchar(64)
file_name           varchar(255)
file_type           varchar(32)
file_url            varchar(1000)
file_hash           varchar(64)
version             int
active_version      int
status              tinyint
chunk_count         int
error_msg           varchar(1000)
create_user         bigint
create_time         datetime
update_time         datetime
```

建议唯一约束：`(kb_id, file_hash, version)`。

文档处理状态：

```text
UPLOADED -> PARSING -> EMBEDDING -> READY
                     -> FAILED
READY -> DISABLED -> DELETED
```

### 8.3 `agent_knowledge_index_task`

```text
id                  bigint PK
task_id             varchar(64) UNIQUE
document_id         varchar(64)
document_version    int
status              tinyint
progress            int
request_hash        varchar(64)
error_msg           varchar(1000)
started_at          datetime
finished_at         datetime
create_time         datetime
update_time         datetime
```

### 8.4 `agent_message_citation`

```text
id                  bigint PK
message_id          varchar(64)
kb_id               varchar(64)
document_id         varchar(64)
document_version    int
chunk_id            varchar(64)
file_name           varchar(255)
page_no             int
score               decimal(8,6)
quote               text
create_time         datetime
```

建议唯一约束：`(message_id, chunk_id)`。

### 8.5 Qdrant Payload

```json
{
  "kb_id": "kb-uuid",
  "document_id": "doc-uuid",
  "document_version": 2,
  "chunk_id": "chunk-uuid",
  "file_name": "员工手册.pdf",
  "title_path": ["员工手册", "考勤制度"],
  "page_no": 12,
  "content": "...",
  "content_hash": "sha256",
  "enabled": true
}
```

## 9. 文档处理方案

### 9.1 上传校验

- 仅允许白名单扩展名和 MIME 类型。
- 校验文件头，不能只相信文件后缀。
- 第一版建议单文件上限 20 MB，可配置。
- 计算 SHA-256，识别同知识库内重复文件。
- 文件名展示时转义，存储路径使用系统生成 ID。
- 原始文件默认私有访问。

### 9.2 解析与清洗

| 格式 | 建议解析方式 |
| --- | --- |
| PDF | PyMuPDF 或 pypdf，保留页码 |
| DOCX | python-docx，保留标题与段落层级 |
| TXT | 编码检测后读取 |
| Markdown | 保留标题、列表和代码块结构 |

清洗内容包括重复页眉页脚、异常空白、无意义控制字符和连续重复段落。表格需要转换为带表头的可读文本，避免丢失列关系。

### 9.3 分块策略

- 优先按标题、段落、列表和表格边界切分。
- 单块目标大小：400～800 tokens。
- 重叠大小：80～120 tokens。
- 不把标题和其第一段拆开。
- 过长表格按行分块，每块重复表头。
- 每个 chunk 计算内容哈希，便于去重和增量更新。

## 10. 检索与生成方案

### 10.1 第一版检索流程

```text
用户当前问题
    -> 结合最近对话进行问题规范化
    -> 按 kb_id + active_version + enabled 过滤
    -> 向量召回 Top 12
    -> 最低相关度过滤
    -> 相邻 chunk 合并与去重
    -> 选取 Top 4～8 作为证据
    -> 构建 RAG Prompt
    -> 生成回答及引用
```

### 10.2 上下文结构

```text
系统规则
+ 会话历史摘要
+ 最近对话消息
+ RAG 检索证据
+ 当前用户问题
```

上下文预算建议：

| 内容 | 建议占比 |
| --- | --- |
| 系统规则 | 10% |
| 会话摘要与最近消息 | 20% |
| RAG 证据 | 45% |
| 当前问题 | 5% |
| 输出预留和安全余量 | 20% |

RAG 证据不得写入会话摘要成为永久事实。摘要只记录用户目标、已确认决定和对话状态。

### 10.3 无证据处理

出现以下情况时不应强行回答：

- 没有召回结果。
- 所有结果低于相关度阈值。
- 证据之间存在明显冲突且无法确认有效版本。
- 用户问题超出所选知识库范围。

统一回答应说明知识库中没有足够信息，并建议用户更换知识库、补充问题或联系管理员。

## 11. 接口规划

### 11.1 Java 管理端接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/admin/agent/knowledge-bases` | 创建知识库 |
| GET | `/admin/agent/knowledge-bases` | 查询知识库列表 |
| PUT | `/admin/agent/knowledge-bases/{kbId}` | 更新知识库 |
| DELETE | `/admin/agent/knowledge-bases/{kbId}` | 删除知识库 |
| POST | `/admin/agent/knowledge-bases/{kbId}/documents` | 上传文档 |
| GET | `/admin/agent/knowledge-bases/{kbId}/documents` | 查询文档列表 |
| POST | `/admin/agent/documents/{documentId}/reindex` | 重新索引 |
| DELETE | `/admin/agent/documents/{documentId}` | 删除文档 |
| GET | `/admin/agent/index-tasks/{taskId}` | 查询索引状态 |
| GET | `/admin/agent/documents/{documentId}/content` | 鉴权访问原文 |

现有对话提交请求增加可选字段：

```json
{
  "taskId": "task-uuid",
  "sessionId": "session-uuid",
  "kbId": "kb-uuid",
  "query": "员工每月可以申请几次调休？",
  "model": "deepseek-v4-pro"
}
```

### 11.2 Java 调用 Python 接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/v1/knowledge/index` | 创建文档索引任务 |
| GET | `/api/v1/knowledge/index/{task_id}` | 查询索引任务状态 |
| DELETE | `/api/v1/knowledge/documents/{document_id}` | 删除指定版本向量 |
| POST | `/api/v1/knowledge/search` | 检索诊断接口 |
| POST | `/api/v1/agent/submit` | 扩展现有请求，携带 RAG 参数 |

Python RAG 请求建议：

```json
{
  "task_id": "task-uuid",
  "session_id": "session-uuid",
  "user_id": 1,
  "query": "员工每月可以申请几次调休？",
  "knowledge": {
    "kb_id": "kb-uuid",
    "document_versions": {"doc-1": 2},
    "top_k": 8
  },
  "context": {
    "summary": "...",
    "history": []
  }
}
```

## 12. 引用返回与展示

Python 任务完成事件应扩展为：

```json
{
  "status": "completed",
  "result": "员工每月最多可以申请一次调休。",
  "citations": [
    {
      "chunk_id": "chunk-123",
      "document_id": "doc-9",
      "document_version": 2,
      "file_name": "员工手册.pdf",
      "page_no": 12,
      "quote": "员工每月最多可申请一次调休……",
      "score": 0.87
    }
  ]
}
```

前端展示要求：

- 回答正文下方显示来源列表。
- 显示文件名、页码和短引用片段。
- 点击来源时通过 Java 鉴权接口打开原文。
- 无页码格式不显示虚假页码。
- 引用数量建议限制为 3～5 个。

## 13. 索引一致性与恢复

文档更新流程：

```text
上传新文件
-> 创建 document_version = N + 1
-> 解析和向量化新版本
-> 校验 chunk_count > 0
-> MySQL 原子切换 active_version
-> 新请求只检索新版本
-> 异步清理旧版本向量
```

失败处理：

- 解析或 Embedding 失败时保留旧有效版本。
- 索引任务使用 `task_id` 和 `request_hash` 幂等。
- Python 重启后可以根据 Java/MySQL 中未结束任务恢复或重新提交。
- 删除文档时先在 MySQL 标记禁用，再异步删除向量。
- 向量删除失败可重试，但禁用文档不得继续参与检索。

## 14. 权限与安全

- Java 在任何知识库、文档和引用接口中校验当前用户权限。
- Python 不直接信任前端传入的 `kb_id`。
- Java 调用 Python 时传递经过鉴权的知识库与文档版本范围。
- Qdrant 查询必须带 `kb_id` 和版本过滤，禁止无过滤全库检索。
- 防御文档提示注入：知识库文本只作为资料，不得覆盖系统指令。
- 上传文件进行恶意类型、压缩炸弹和路径穿越校验。
- 日志不得记录完整文档、敏感引用或模型密钥。
- 删除知识库时保留审计记录，物理文件和向量异步清理。

## 15. 实施阶段

### 阶段 1：数据与知识库管理

产出：

- 数据库迁移脚本。
- Java Entity、DTO、VO、Mapper、Service 和 Controller。
- 知识库与文档管理页面。
- 文件上传、哈希去重和权限校验。

验收：知识库和文档可管理，未授权用户不能访问。

### 阶段 2：文档解析与索引

产出：

- Python 解析器接口和四类格式实现。
- 结构化分块器。
- Embedding Provider 接口。
- Qdrant VectorStore 接口。
- 索引任务状态、进度和错误记录。

验收：文档成功生成可追踪 chunk，失败任务可重试。

### 阶段 3：RAG 对话

产出：

- 对话请求增加 `kbId`。
- Java 权限校验和版本解析。
- Python 检索、阈值过滤及 Prompt 编排。
- 无证据拒答。

验收：真实问题能召回正确文档，越权知识库无法查询。

### 阶段 4：引用溯源

产出：

- 完成事件返回 citations。
- Java 持久化 `agent_message_citation`。
- 前端来源展示和原文访问。

验收：引用能够定位原文，且引用内容确实支持回答。

### 阶段 5：版本、恢复与质量评测

产出：

- 文档版本原子切换。
- 删除与重建索引流程。
- 索引恢复任务。
- RAG 离线评测集和监控指标。

验收：更新、停用和删除文档后，召回结果符合最新状态。

### 后续阶段

- BM25 与向量混合检索。
- RRF 候选合并。
- Reranker 重排。
- 多知识库授权检索。
- OCR 和复杂表格处理。
- Redis/消息队列及 Python 多实例。

## 16. 测试计划

### 16.1 单元测试

- 文件类型和文件头校验。
- 文本清洗和分块边界。
- chunk 哈希稳定性。
- 请求幂等和参数冲突。
- 相关度阈值和无证据拒答。
- 引用去重与序列化。
- 文档提示注入防护。

### 16.2 集成测试

- Java 上传文档到 Python 索引完成的全链路。
- 同一索引任务并发提交只执行一次。
- Qdrant 按知识库和版本过滤。
- 文档新版本切换及旧版本清理。
- Python 重启后的任务恢复。
- RAG 回答、SSE 事件和引用持久化。

### 16.3 权限测试

- 用户不能查询无权知识库。
- 用户不能下载无权文档。
- 修改请求中的 `kbId` 不能绕过 Java 权限。
- 删除或禁用文档后不能继续召回。
- 引用接口不能泄露其他知识库内容。

### 16.4 效果评测

建立至少 50～100 个真实业务问题，标注：

- 标准答案或关键事实。
- 应命中的文档和页码。
- 是否应拒答。
- 允许的答案差异。

核心指标：

| 指标 | 第一版建议目标 |
| --- | --- |
| 文档解析成功率 | ≥ 95% |
| 正确证据进入 Top 5 | ≥ 85% |
| 引用支持答案比例 | ≥ 90% |
| 无答案问题正确拒答率 | ≥ 85% |
| 越权检索 | 0 |
| 禁用文档召回 | 0 |

## 17. 监控与日志

建议记录：

- 文档解析耗时、页数和 chunk 数。
- Embedding 调用次数、耗时和失败率。
- 检索耗时、候选数量和最终证据数量。
- RAG 首 token 延迟和总响应时间。
- 无证据拒答率。
- 引用点击率。
- 索引失败率和重试次数。
- Qdrant 查询失败率。

检索诊断日志只保存 ID、分数和耗时，不默认记录完整敏感文本。

## 18. 风险与应对

| 风险 | 应对措施 |
| --- | --- |
| 文档解析质量不稳定 | 限定格式，保留页码和标题结构，建立样本文档回归测试 |
| 错误召回导致幻觉 | 设置阈值、无证据拒答、引用校验 |
| 跨知识库数据泄露 | Java 权限校验和 Qdrant 强制 payload 过滤 |
| 文档更新期间数据不一致 | 新版本完整索引后原子切换 |
| Embedding 模型更换 | 记录模型和向量维度，使用新 collection 重建索引 |
| Python 单实例故障 | 索引任务持久化、幂等重试，后续再引入队列和多实例 |
| 大文档处理耗时 | 异步索引、进度展示、文件大小限制 |
| 文档提示注入 | 明确资料层级，过滤高风险指令，系统指令优先 |

## 19. 上线与回滚

上线步骤：

1. 部署 Qdrant 并创建版本化 collection。
2. 执行 MySQL 迁移脚本。
3. 发布 Python 解析、索引和检索能力。
4. 发布 Java 知识库管理接口。
5. 先向管理员开放知识库管理和检索诊断。
6. 导入小规模知识库并执行评测。
7. 发布 RAG 对话和引用前端。
8. 灰度开放并观察召回、拒答和延迟指标。

回滚原则：

- RAG 功能通过配置开关关闭，现有普通 AI 对话继续工作。
- 数据库新增表不影响现有会话表。
- 文档新版本失败时继续使用旧有效版本。
- Qdrant 不可用时返回明确的知识库暂不可用提示，不降级为无依据回答。

## 20. 完成定义

第一版满足以下条件才视为完成：

- 知识库和文档具备完整管理与权限校验。
- 四类文档能够异步解析、分块并写入 Qdrant。
- 对话可以显式绑定一个知识库。
- 回答基于检索证据，并持久化可访问的引用。
- 无可靠证据时能够拒答。
- 文档更新、停用、删除和重新索引行为正确。
- 幂等、恢复、权限和效果评测通过。
- RAG 故障不影响不使用知识库的普通 AI 对话。

