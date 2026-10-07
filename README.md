# 饱饱智能点餐平台

<<<<<<< HEAD
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
=======
面向真实点餐业务的全栈项目，包含用户端、管理端、Java 业务服务、Python Agent、RAG、向量检索、订单支付售后，以及个性化饮食推荐能力。

## 核心能力

- 用户注册登录、菜单、搜索、购物车、结算、支付、订单、退款、售后和通知；
- 管理端菜品、套餐、员工、订单、报表、知识库和用户账户管理；
- 管理端与用户端 Agent 数据隔离；
- 公共知识上传、索引、发布、下线、回滚和用户会话绑定；
- Agent 实时工具调用、流式响应、操作确认和审计；
- 菜品营养、食材、过敏原、地域时令和用户饮食档案；
- 基于结构化硬约束和版本化规则的个性化饮食推荐；
- Docker Compose、健康检查、Prometheus、Grafana和备份脚本。

## 个性化饮食推荐架构

```text
用户自然语言
→ Python Agent 识别需求、风险和必要槽位
→ Java 查询实时可售菜单
→ 过敏原/忌口硬过滤
→ 营养、健康目标、地域、时令、预算评分
→ Java 返回结构化理由、警告和规则版本
→ Agent 解释并展示可直接加购的推荐卡片
```

安全边界：LLM 负责理解与表达，Java 负责事实、营养计算和安全裁决。系统不诊断疾病，不生成治疗方案；复杂疾病和急性高风险症状会降级并提示咨询专业人员。

详细设计见[个性化健康饮食推荐实施计划书](项目文档/饱饱助手个性化健康饮食推荐实施计划书.md)。

## 技术栈

- Java 21、Spring Boot 3、MyBatis、Flyway；
- MySQL、Redis、Qdrant；
- Python、FastAPI、LangGraph；
- Vue 3 用户端、Vue 2 + Element UI 管理端；
- Docker Compose、Prometheus、Grafana；
- JUnit、Mockito、unittest、Vitest、Jest、Playwright。

## 本地启动

1. 从 `.env.example` 复制本地 `.env`，配置数据库、JWT、Redis、LLM 和内部服务令牌；
2. 准备 `sky-embedding/models/bge-m3` 模型目录；
3. 使用 `docker compose up -d --build` 启动完整环境；
4. 通过各服务健康检查确认 MySQL、Redis、Qdrant、Embedding、Agent 和 Java 均已就绪；
5. 构建并部署用户端与管理端静态文件。

不要提交真实密钥、数据库密码或生产令牌。

## 质量验证

```powershell
# Java
mvn test

# Python Agent
cd agent-service
python -m unittest discover -s tests -p 'test_*.py'

# 用户端
cd frontend/project-sky-user-web
npm test
npm run build

# 管理端
cd frontend/project-sky-admin-vue-ts
>>>>>>> github/master
yarn test:unit --runInBand
yarn build
```

<<<<<<< HEAD
## 手工验收

自动测试通过后，按[交付验收清单](项目文档/项目交付与手工验收清单.md)完成最后的业务验证。

## 已知边界

- LLM与Embedding需要有效模型或外部服务配置；
- BGE-M3模型不提交到Git；
- OSS配置仅保留给未来云存储切换，当前商品图片使用本地持久卷；
- 全新数据库不预置菜品、套餐、用户和订单，需由管理员在手工验收时创建；
- 微信支付、短信和地图在开发环境可以使用模拟实现。
=======
GitHub Actions 会对四个模块分别执行测试和构建。

## 饮食推荐上线前置条件

代码部署并不等于健康推荐内容可直接上线。必须先在管理端完成：

1. 按真实配方录入标准份量、食材和营养指标；
2. 为每个目标过敏原明确标记 `FREE/CONTAINS/MAY_CONTAIN/CROSS_CONTACT_RISK/UNKNOWN`；
3. 审核启用菜品营养版本；
4. 所有组成菜品审核后生成套餐营养快照；
5. 根据权威来源创建、校验并发布食养规则；
6. 通过安全评测集后再开启对应慢病场景。

未知数据不会被系统当作生产安全数据。开发环境可以显式启用 `SIMULATED` 一人份估算数据，生产环境默认禁止使用。

已生成的待审核数据包位于 [data/diet-seed](data/diet-seed/README.md)，可发布知识文件位于 [知识库/个性化饮食](知识库/个性化饮食/00-使用边界与来源说明.md)。运行 `scripts/validate-diet-content.ps1` 可以检查文件结构和来源链接。
>>>>>>> github/master