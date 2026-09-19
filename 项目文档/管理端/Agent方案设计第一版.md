# Agent 方案设计第一版

## 1. 文档目的

本文档定义 Java 业务服务与 Python Agent 服务之间的第一版协作方案，覆盖多轮会话、短期上下文、历史持久化、模型调用、RAG、知识库检索和流式输出。

本文档的核心目标是：

- Java 管理用户、会话、权限和业务数据；
- Python 负责模型推理、流式输出以及后续 RAG/知识库能力；
- 两个项目通过稳定的 HTTP/JSON 协议通信，不直接依赖对方的数据库表结构；
- 上下文长度可控，避免随着会话增长导致请求过大和模型超出上下文窗口。

## 2. 总体架构

```text
前端
  |
  | HTTP / SSE
  v
Java 会话服务
  |- 用户鉴权与 session 权限校验
  |- Redis 短期上下文与并发控制
  |- MySQL 完整会话和消息持久化
  |- 上下文裁剪、摘要和 Token 预算控制
  |- Agent 请求编排与错误转换
  `- Python SSE 事件转发
          |
          | HTTP / JSON / SSE
          v
Python Agent 服务
  |- 接收标准化 query + context
  |- 大模型调用
  |- 流式 token 输出
  |- RAG 检索
  |- 知识库访问
  `- 返回任务事件、结果和错误
```

前端只调用 Java 服务，不直接依赖 Python 服务地址、模型供应商或 Python 内部接口。

## 3. 服务职责

### 3.1 Java 服务

Java 是会话和业务数据的管理方，负责：

- 用户身份认证和权限校验；
- 校验 `session_id` 是否属于当前用户；
- 创建、查询、归档和删除会话；
- 保存用户消息和 AI 消息；
- 从 Redis 读取短期上下文；
- Redis 未命中时从 MySQL 恢复历史；
- 控制上下文 Token 预算；
- 执行消息裁剪和历史摘要；
- 管理任务状态、幂等、额度和会话并发锁；
- 调用 Python Agent；
- 将 Python 的流式事件转发给前端；
- 接收最终结果并持久化。

Java 是会话消息的唯一业务写入方。

### 3.2 Python Agent 服务

Python 是计算和模型能力服务，负责：

- 接收 Java 传入的标准化请求；
- 将 `query`、历史上下文和系统提示词组装为模型消息；
- 调用 DeepSeek/OpenAI 兼容模型；
- 返回同步结果或 SSE 流式事件；
- 根据当前问题执行 RAG 检索；
- 查询知识库和向量数据库；
- 后续扩展只读 Tool Calling；
- 返回模型错误、RAG 错误和任务状态。

Python 不直接访问 Java 的 MySQL 业务表，不负责用户权限，也不负责会话历史持久化。

### 3.3 Redis

Redis 用于高频、短期和并发控制数据：

- 最近若干轮有效消息；
- 会话摘要缓存；
- 上下文版本；
- 同一会话生成锁；
- 请求幂等状态；
- Agent 任务临时状态和事件；
- 用户额度和限流计数。

Redis 不是完整历史的最终存储。

### 3.4 MySQL

MySQL 保存最终业务数据：

- 会话基本信息；
- 完整消息历史；
- 消息角色、状态、顺序和时间；
- 任务记录；
- 摘要版本和生成时间；
- 必要的 Token、耗时和错误信息。

## 4. 核心请求链路

```text
1. 前端向 Java 提交问题和 session_id
2. Java 校验登录用户和会话归属
3. Java 保存用户消息或创建待处理任务
4. Java 从 Redis 读取短期上下文
5. Redis 未命中时，Java 从 MySQL 查询并回填 Redis
6. Java 按 Token 预算裁剪上下文，必要时生成摘要
7. Java 组装标准 Agent 请求
8. Java 调用 Python /api/v1/agent/submit
9. Java 根据 task_id 订阅 Python SSE
10. Python 使用 context 调用模型并执行 RAG
11. Python 持续返回 task_start/token/task_end 事件
12. Java 将事件转发给前端
13. Java 收到 task_end 后保存 AI 回复到 MySQL
14. Java 更新 Redis 中的短期上下文和摘要
15. Java 向前端结束 SSE 响应
```

## 5. Java 与 Python 请求协议

### 5.1 请求地址

```text
POST /api/v1/agent/submit
Content-Type: application/json
```

### 5.2 请求体

```json
{
  "user_id": "1",
  "session_id": "abc123",
  "query": "继续刚才的话题",
  "context": {
    "summary": "用户正在咨询配送范围，已确认所在区域支持配送。",
    "history": [
      {
        "role": "user",
        "content": "之前的问题"
      },
      {
        "role": "assistant",
        "content": "之前的回答"
      }
    ]
  },
  "temperature": 0.7
}
```

字段约定：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `user_id` | string | 是 | Java 传递的用户标识，统一使用字符串 |
| `session_id` | string | 否 | 会话标识，首轮为空 |
| `query` | string | 是 | 当前用户问题 |
| `context` | object | 否 | Java 组装的上下文 |
| `context.summary` | string | 否 | 较早历史的摘要 |
| `context.history` | array | 否 | 最近有效消息 |
| `temperature` | number | 否 | 模型采样参数 |

`user_id` 和 `session_id` 在跨服务协议中统一使用字符串；Java 内部可以继续使用 `Long`，发送前必须显式转换。

### 5.3 提交响应

```json
{
  "task_id": "8b2f7e8c-0a9c-4e32-8b9d-123456789abc",
  "status": "processing",
  "stream_url": "http://localhost:8000/api/v1/agent/stream/8b2f7e8c-0a9c-4e32-8b9d-123456789abc"
}
```

## 6. 流式事件协议

Java 与 Python 统一使用 Submit + SSE 模式：

```text
POST /api/v1/agent/submit
GET  /api/v1/agent/stream/{task_id}
Accept: text/event-stream
```

事件示例：

```text
data: {"event":"task_start","data":{"task_id":"...","status":"running"}}

data: {"event":"token","data":"你好"}

data: {"event":"token","data":"，很高兴认识你。"}

data: {"event":"task_end","data":{"status":"completed","result":"你好，很高兴认识你。"}}

```

事件类型：

| 事件 | 说明 |
|---|---|
| `task_start` | 任务开始执行 |
| `token` | 一段增量文本 |
| `task_end` | 任务完成或失败，包含最终结果或错误 |

Java 不应把事件整体当作普通字符串处理，而应解析事件类型和 `data` 字段后再转发给前端。

## 7. 上下文管理策略

### 7.1 三层控制

```text
Token 预算限制
    ↓
上下文裁剪
    ↓
历史摘要
```

Token 预算是硬限制，裁剪用于删除低价值内容，摘要用于保留较早历史的语义信息。

### 7.2 建议的上下文组成

模型请求上下文由以下部分组成：

```text
系统提示词
+ 会话摘要
+ 最近若干轮消息
+ 当前业务状态
+ 本次 RAG 检索结果
+ 当前用户问题
```

知识库检索结果只服务于当前请求，不应无条件追加到永久会话历史。

### 7.3 初始配置建议

第一版可以采用以下起始规则，后续根据真实 Token 统计调整：

- Redis 保存最近 20 条有效消息；
- Redis 保存一个会话摘要；
- 单次请求设置总 Token 预算；
- 超过预算时优先删除最早的普通消息；
- 摘要保留用户目标、已确认事实、未完成事项和业务约束；
- 系统提示词、当前问题和必要业务状态不可裁剪；
- RAG 只保留少量相关文档片段。

## 8. 数据一致性原则

- Java 是会话消息的唯一写入方；
- Python 只返回推理结果，不修改 Java 会话表；
- 用户消息和任务记录由 Java 事务管理；
- Python 失败时，Java 将任务标记为失败并记录错误；
- AI 回复只有在收到 `task_end` 成功事件后才写入有效消息；
- Redis 更新失败时，以 MySQL 为准并允许后续回填；
- Redis 过期或丢失不能导致完整会话历史丢失；
- 同一会话默认禁止并发生成，避免消息顺序混乱。

## 9. 当前接口问题修复要求

当前 422 的直接原因是 Python 要求 `user_id` 为字符串，而 Java 发送了数字 `1`。

Java 发送请求前必须转换：

```java
params.put("user_id", String.valueOf(userId));
```

同时完成以下协议统一：

- `user_id` 统一为字符串；
- `sessionId` 对外请求统一改为 `session_id`；
- 使用专用请求 DTO，减少 `Map<String, Object>` 类型错误；
- Java 解析 Python JSON 后再返回，避免 JSON 被二次包装成字符串；
- Java 将 Python 的 422、500 和超时转换为明确业务错误；
- 统一使用 Submit + SSE，不再混用旧版普通流式接口。

## 10. 分阶段落地建议

### 阶段一：接口契约修复

- 修复 `user_id` 类型；
- 统一 `session_id` 命名；
- 定义 Java 请求 DTO 和 Python Pydantic 模型；
- 完成同步查询和 Submit + SSE 的联调。

### 阶段二：会话上下文闭环

- Java 从 MySQL 读取历史；
- Java 将最近消息写入 Redis；
- Java 组装 `context.history`；
- Java 保存 AI 回复并更新 Redis；
- 增加上下文 Token 预算。

### 阶段三：上下文优化

- 增加历史摘要；
- 增加上下文裁剪；
- 增加上下文版本和缓存失效处理；
- 增加重复提交和同会话并发控制。

### 阶段四：RAG 与知识库

- Python 增加向量检索和文档检索；
- 将检索结果作为本次请求的 `retrieval_context`；
- 对文档片段进行数量、长度和敏感信息控制；
- 记录检索耗时、命中文档和模型耗时。

### 阶段五：生产能力

- SSE 断线恢复；
- 生成中止；
- Token 和成本统计；
- 超时、重试和熔断；
- 敏感数据脱敏；
- 任务恢复和过期会话清理；
- 多实例部署下的 Redis 事件和锁治理。

## 11. 方案结论

最终采用以下原则：

```text
Java 管理会话和数据
Redis 管理短期上下文与并发状态
MySQL 保存完整历史
Java 通过稳定的 context 协议调用 Python
Python 负责模型、RAG和知识库计算
Java 保存 Python 结果并统一响应前端
```

该方案避免了 Python 对 Java 数据库表结构的直接依赖，同时支持多轮会话、上下文压缩、RAG、知识库和流式响应的持续扩展。
