# 饱饱智能点餐平台

一个包含用户端、管理端、订单支付售后、Python Agent、RAG知识库和向量检索的完整点餐项目。

## 交付形态

完整环境通过 Docker Compose 运行：

| 服务 | 默认地址 | 说明 |
|---|---|---|
| 管理端 | `http://localhost` | Vue 2 管理后台 |
| 用户端 | `http://localhost:8081` | Vue 3 用户网站 |
| Java API | `http://localhost:8080` | Spring Boot业务服务 |
| Agent | `http://localhost:8000` | FastAPI/LangGraph |
| Embedding | `http://localhost:8001` | BGE-M3向量服务 |
| Qdrant | `http://localhost:6333` | 向量数据库 |

实际端口可通过 `.env` 调整。

## 首次部署

### 1. 准备软件

- Git
- Docker Desktop或Docker Engine + Compose
- PowerShell 7（Windows脚本）

JDK、Maven、Node和Python只在脱离Docker进行开发时需要，交付部署不需要安装。

### 2. 准备配置

```powershell
Copy-Item .env.example .env
notepad .env
```

把所有 `replace-with-*` 和空的必填密钥替换为真实配置。不要提交 `.env`。

首次管理员由以下配置创建，数据库已有员工时不会重复创建：

```text
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=<至少8位强密码>
```

### 3. 准备Embedding模型

将 BGE-M3 模型放到：

```text
sky-embedding/models/bge-m3
```

模型不进入Git和Docker镜像。

### 4. 一键部署

```powershell
.\scripts\test-delivery-preflight.ps1
.\scripts\deploy-clean.ps1
```

部署脚本会完成：配置检查、模型校验、后端构建、两个前端构建、容器启动、健康等待和自动冒烟测试。

## 数据库初始化

全新MySQL数据卷首次启动时：

```text
deploy/mysql/001-core-schema.sql
→ 创建无演示用户和无隐私数据的核心业务表
→ Spring Flyway执行全部版本迁移
→ AdminBootstrapRunner创建首个超级管理员
```

Flyway迁移使用MySQL root密码，Java业务连接继续使用受限的 `DB_USERNAME`，两类权限不会混用。

不要导入 `sky_take_out_backup.sql`，该文件不是干净交付基线。

## 图片存储

菜品和套餐图片不依赖OSS，保存在：

```text
Docker卷 server-data:/app/data/uploads/products
```

数据库只保存相对访问地址。备份时必须同时备份 `server-data`，现有备份脚本已包含该卷。

## 常用命令

```powershell
# 查看状态
docker compose ps

# 查看日志
docker compose logs --tail 100 sky-server
docker compose logs --tail 100 sky-agent

# 自动冒烟
.\scripts\test-delivery-smoke.ps1

# 停止但保留数据
docker compose down

# 重新构建
docker compose up -d --build

# 备份
.\scripts\backup-ai-runtime.ps1 -BackupDirectory C:\backup\sky

# 验证备份
.\scripts\verify-backup.ps1 -BackupPath <具体备份目录>
```

除非明确要删除全部业务数据，否则不要执行：

```powershell
docker compose down -v
```

## 自动测试

```powershell
mvn test

Set-Location agent-service
python -m unittest discover -s tests -p 'test_*.py'

Set-Location ..\frontend\project-sky-user-web
npm test
npm run build

Set-Location ..\project-sky-admin-vue-ts
yarn test:unit --runInBand
yarn build
```

## 手工验收

自动测试通过后，按[交付验收清单](项目文档/项目交付与手工验收清单.md)完成最后的业务验证。

## 已知边界

- LLM与Embedding需要有效模型或外部服务配置；
- BGE-M3模型不提交到Git；
- OSS配置仅保留给未来云存储切换，当前商品图片使用本地持久卷；
- 全新数据库不预置菜品、套餐、用户和订单，需由管理员在手工验收时创建；
- 微信支付、短信和地图在开发环境可以使用模拟实现。
