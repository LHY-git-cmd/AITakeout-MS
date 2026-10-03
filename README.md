# 饱饱智能点餐平台

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

### 菜品图片存储

菜品和套餐图片当前使用本地相对目录，不依赖OSS：

```text
物理目录：data/uploads/products
数据库地址：/api/uploads/products/{id}?ext=png
配置项：PRODUCT_IMAGE_LOCAL_ROOT=data/uploads/products
```

Docker部署时该目录位于已挂载的 `server-data` 数据卷中。备份数据库时应同时备份图片目录；旧OSS地址不会自动迁移，需要在管理端重新上传或单独执行资源迁移。

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
yarn test:unit --runInBand
yarn build
```

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
