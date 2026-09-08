# RAG语义检索与解耦改造实施报告

## 1. 基本信息

- 实施日期：2026-09-07
- 项目：苍穹外卖智能助手
- 实施范围：BGE-M3独立Embedding服务、Qdrant V2集合、multipart文档传输、RAG配置统一、旧接口兼容处理、端到端测试
- 原集合：`sky_knowledge_v1`（384维，保留且未修改）
- 新集合：`sky_knowledge_v2`（1024维，Cosine）

## 2. 实施结果

本次改造已完成。RAG链路由字符级Hash向量升级为BGE-M3语义向量，Java与Python之间的索引调用由共享绝对路径改为multipart文件传输。新集合已创建并完成现有菜品文档重建，真实Qdrant与Mock LLM端到端测试通过。

改造后的链路为：

```text
前端 query
  → Java会话、用户和知识库权限校验
  → Python Agent任务队列
  → 独立BGE-M3 Embedding服务
  → Qdrant sky_knowledge_v2
  → LLM或测试用Mock LLM
  → Python结构化SSE
  → Java事件持久化和SSE转发
  → 前端回答及引用展示
```

## 3. 模型及运行环境

### 3.1 模型

- Hugging Face模型：`BAAI/bge-m3`
- 固定Revision：`5617a9f61b028005a4858fdac845db406aefb181`
- 本地目录：`E:\Pyhon\sky-embedding\models\bge-m3`
- 实际占用空间：约4.27 GB
- 输出维度：1024
- 向量归一化：使用BGE-M3 dense embedding默认归一化结果
- 当前验证设备：CPU

模型目录已加入Git忽略规则，避免将4 GB以上二进制文件提交到代码仓库。为与现有 `sky-agent → E:\Pyhon\FastAPIProject1` 布局一致，`sky-embedding` 实体目录位于 `E:\Pyhon\sky-embedding`，主项目下保留同名目录联接。

### 3.2 独立Embedding服务

新增 `sky-embedding` 服务，默认监听 `8001`，提供OpenAI风格接口：

```http
GET  /health
POST /v1/embeddings
```

主要配置：

```text
EMBEDDING_MODEL=BAAI/bge-m3
EMBEDDING_MODEL_PATH=sky-embedding/models/bge-m3
EMBEDDING_DEVICE=cpu
EMBEDDING_MAX_BATCH_SIZE=16
```

验证结果：服务健康检查成功，批量请求返回2个1024维向量。

## 4. 代码改造

### 4.1 Python Agent

- 新增 `HttpEmbeddingProvider`，批量调用独立Embedding服务。
- 默认Qdrant集合改为 `sky_knowledge_v2`。
- Qdrant集合启动时自动创建，并强制校验集合维度。
- 索引请求改为 `multipart/form-data`，包含JSON metadata和文件二进制。
- Python将接收文件保存到自身受控目录，不再读取Java绝对路径。
- 分块参数由1200/200调整为400/60，现有菜品文档由1块拆分为2块。
- 索引时校验知识库声明的Embedding模型与实际Provider一致。
- 同步查询和旧流式查询接口已修复RAG处理，并在OpenAPI中标记为Deprecated。
- 主链路仍为 `POST /agent/submit + GET /agent/stream/{taskId}`。

### 4.2 Java

- `AgentClient.indexKnowledge` 改为multipart上传文件和metadata。
- 不再向Python发送 `file_path`。
- 新知识库默认模型改为 `BAAI/bge-m3`。
- 创建和更新知识库时拒绝与当前Provider不一致的Embedding模型。
- `rag-enabled`、`rag-top-k`、`rag-score-threshold` 已用于实际构建Python检索范围，移除硬编码值。
- 新增Flyway迁移，将旧知识库的 `hash-384` 标识迁移为 `BAAI/bge-m3`。

Java端默认业务配置：

```yaml
sky:
  agent:
    rag-enabled: true
    rag-top-k: 8
    rag-score-threshold: 0.35
    embedding-model: BAAI/bge-m3
```

Python端负责Embedding服务地址、维度和Qdrant集合等技术配置；Java负责是否启用RAG、召回数量、阈值和授权文档版本等业务配置。

### 4.3 文件解耦

改造前：

```text
Java保存文件 → 将E:\...绝对路径传给Python → Python直接读取Java磁盘
```

改造后：

```text
Java保存文件 → multipart发送metadata和文件流 → Python保存到自身目录并解析
```

因此Java与Python可以使用不同工作目录、不同操作系统或分开部署。

## 5. 数据迁移结果

`sky_knowledge_v2` 已创建：

```text
状态：green
维度：1024
距离：Cosine
向量点数：2
```

现有菜品文档迁移信息：

```text
kb_id: 8adeba1c-2f2c-4f62-a033-2b4ea69801ee
document_id: 9f7b4acc-e8c7-4f6f-adb3-3b1b8eeabb7e
document_version: 1
索引状态：completed
分块数量：2
```

生产阈值0.35下的检索验证：

| 查询 | Top分数 | 是否召回 |
|---|---:|---|
| 宫保鸡丁多少钱 | 0.60 | 是 |
| 有哪些适合下饭的菜 | 0.62 | 是 |
| 宫保鸡丁 | 0.55 | 是 |
| 米饭 | 0.52 | 是 |

改造前以上查询在阈值0.2下全部为0条结果。

## 6. 测试结果

### 6.1 Python单元测试

```text
执行：python -m unittest discover -s tests -v
结果：15项执行通过，1项显式跳过
```

端到端测试默认跳过，只有设置 `RUN_RAG_E2E=1` 后才访问真实外部服务，避免普通单元测试污染Qdrant。

### 6.2 Java Agent/RAG测试

```text
测试数：14
失败：0
错误：0
```

覆盖内容包括：

- Java/Python字段契约。
- multipart文件和metadata上传。
- HTTP冲突错误映射。
- 知识库权限和激活版本。
- RAG开关、TopK和阈值配置生效。
- Agent事件、回答和引用持久化。

### 6.3 真实Qdrant + Mock LLM端到端测试

测试过程：

```text
生成隔离测试知识库和文档
→ 调用真实BGE-M3 Embedding服务
→ 写入真实Qdrant sky_knowledge_v2
→ 查询“宫保鸡丁多少钱？”
→ 将RAG证据注入Mock LLM
→ 通过任务队列生成token和task_end事件
→ 校验回答与citation
→ 删除测试向量
```

结果：通过。测试结束后自动清理测试文档向量。

可重复执行命令：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-rag-e2e.ps1
```

## 7. 启动方式

### 7.1 启动Embedding服务

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-embedding.ps1
```

健康检查：

```powershell
Invoke-RestMethod http://127.0.0.1:8001/health
```

### 7.2 启动Python Agent

确认 `sky-agent/.env` 包含：

```text
VECTOR_STORE=qdrant
QDRANT_URL=http://localhost:6333
QDRANT_COLLECTION=sky_knowledge_v2
EMBEDDING_BASE_URL=http://127.0.0.1:8001
EMBEDDING_MODEL=BAAI/bge-m3
EMBEDDING_DIMENSION=1024
```

然后启动：

```powershell
cd sky-agent
python run.py
```

### 7.3 重启Java

Java重启后Flyway会执行 `V20260907_01__migrate_rag_embedding_model.sql`，随后Java的新multipart客户端与统一RAG配置才会在运行态生效。

## 8. 当前运行态说明

- Qdrant `6333`：在线。
- BGE-M3 Embedding `8001`：在线并完成验证。
- 新版Python Agent在 `8002` 完成multipart接口验证。
- 原Python Agent的reload子进程仍占用 `8000`，Windows显示监听PID但无法解析对应进程，因此本次未强制处理系统级残留端口。
- Java `8080` 仍是本次改造前启动的进程。

正式切换时应关闭原Python开发进程并在8000启动新版Agent，然后重启Java，使Flyway迁移和新Java代码生效。该运行态切换不影响本次代码、真实向量检索和端到端测试结论。

## 9. 回滚方式

如需回滚：

1. 将Python的 `QDRANT_COLLECTION` 改回 `sky_knowledge_v1`。
2. 将Python Provider切回旧Hash实现仅可恢复旧行为，不建议作为生产方案。
3. `sky_knowledge_v1` 本次未删除、未覆盖，可直接用于数据级回退。
4. `sky_knowledge_v2` 为独立集合，删除它不会影响旧集合。

## 10. 验收结论

本次指定范围已完成并通过验证：

- BGE-M3已从Hugging Face下载并可用。
- 独立Embedding服务可稳定生成1024维向量。
- `sky_knowledge_v2` 创建成功，旧集合保持不变。
- Java到Python已改为multipart文件传输。
- RAG业务配置已统一并实际生效。
- 旧同步接口已修复并标记废弃。
- 真实Qdrant + Mock LLM端到端测试通过。
- 现有菜品知识数据完成V2重建，生产阈值下可以正常召回。
