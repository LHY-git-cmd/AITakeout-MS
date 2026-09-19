# 模块三：Docker Compose 统一编排

## 本地完整模式

准备 `.env`（数据库、Redis、JWT 和 LLM 必填项不得使用示例值）以及 BGE-M3 模型目录后执行。模型默认放在项目相对目录 `sky-embedding/models/bge-m3`，其他位置通过 `BGE_MODEL_HOST_PATH` 配置：

```powershell
docker compose -f compose.yml up -d --build
docker compose -f compose.yml ps
```

`compose.yml` 启动 `sky-server`、`sky-agent`、`sky-embedding`、MySQL、Redis 和 Qdrant。应用容器之间使用 Compose 服务名通信，不使用 `localhost`。MySQL 的 Flyway 迁移由 Java 应用启动时执行。

命名卷如下：

- `sky-take-out-mysql-data`：MySQL 数据。
- `sky-take-out-redis-data`：Redis AOF 数据。
- `sky-take-out-qdrant-data`：Qdrant 向量数据。
- `sky-take-out-agent-data`：Agent 任务 SQLite、知识 SQLite 和知识源。
- `sky-take-out-server-data`：Java 服务上传的知识文档临时/持久化文件。

Embedding 模型通过 `${BGE_MODEL_HOST_PATH}` 以只读方式挂载到 `/models/bge-m3`，不会写入镜像。首次加载可能需要数分钟，并需要约 4.27 GB 模型磁盘空间及足够内存；以 `sky-embedding` readiness 变为 healthy 作为加载完成标志。

## 外部基础设施模式

已有 MySQL、Redis、Qdrant 时，使用覆盖文件并提供外部地址：

```powershell
$env:DB_HOST = 'mysql.example.internal'
$env:REDIS_HOST = 'redis.example.internal'
$env:QDRANT_URL = 'http://qdrant.example.internal:6333'
docker compose -f compose.yml -f compose.external.yml up -d --build sky-embedding sky-agent sky-server
```

覆盖文件通过非默认 profile 禁用本地 MySQL、Redis、Qdrant；外部实例的网络连通性、凭据、集合和数据库初始化由部署方负责。三个应用默认继续使用 Compose 服务名互联。如额外使用外部 Embedding 或 Agent，分别配置 `EXTERNAL_EMBEDDING_BASE_URL`、`EXTERNAL_AGENT_BASE_URL`，并只启动实际需要的本地服务。

## 健康检查与重启

所有服务配置了 `restart: unless-stopped` 和 JSON 日志轮转（单文件 10 MB、保留 3 个）。应用依赖通过健康状态等待，应用自身仍需对启动期间的连接失败进行重试。可用以下命令观察故障：

```powershell
docker compose ps
docker compose logs --tail=100 sky-server sky-agent sky-embedding
```

重启验收：

```powershell
docker compose restart
docker compose ps
```

确认 MySQL、Qdrant 及 Agent 数据仍存在后再进行业务回归。停止并删除容器时不要使用 `docker compose down -v`，该命令会删除命名卷。
