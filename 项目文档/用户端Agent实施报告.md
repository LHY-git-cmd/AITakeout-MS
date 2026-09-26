# 用户端 Agent 实施报告

## 交付范围

本轮在 `codex/user-agent` 分支完成计划书模块一至六，所有修改均未提交、未推送。

1. 多主体身份：统一 `actor_type / actor_id / actor_role`，新增 `USER/CUSTOMER`，保留管理员兼容字段和数据库迁移。
2. 用户 Agent Gateway：新增 `/user/agent/**` 会话、任务、取消、SSE 断点续传和确认入口；用户身份只取 JWT，任务 ID 使用 `Idempotency-Key`。
3. 用户工具与安全：商品、购物车、订单、时间线、售后状态和营业状态工具按角色隔离；Java 根据任务恢复用户主体，加购幂等；订单响应脱敏联系方式和地址。
4. LangGraph 工作流：实现上下文加载、安全检查、意图路由、槽位提取和工具编排约束；MySQL 仍是任务和事件权威来源。
5. 用户端交互：加入全局悬浮入口、`/assistant` 对话页、JWT `fetch` SSE 客户端、`Last-Event-ID` 自动续传、推荐/购物车/订单/确认/降级卡片。
6. 评测与观测：新增用户场景评测集、提示词注入和越权回归、用户 Agent 意图/工具/任务/首响应 Prometheus 指标。

## 结构化事件协议

用户任务使用以下事件，管理员任务继续兼容旧事件名：

`task_started`、`message_delta`、`recommendation_cards`、`tool_started`、`tool_completed`、`confirmation_required`、`business_state_changed`、`task_completed`、`task_failed`。

商品 ID、价格、订单 ID 和操作参数均来自结构化字段，前端不从 Markdown 解析业务事实。

## 验证结果

- Python 全量：94 项通过，1 项跳过，另有 5 个子测试通过。
- Java/Maven 全量：137 项通过，0 失败，`BUILD SUCCESS`。
- Vue 前端：14 个测试文件、23 项通过；`npm run build` 成功。
- 用户 Agent 确定性评测：12/12 通过，包含推荐、购物车、订单、售后、知识路由、槽位、权限隔离、中文/英文提示词注入和敏感信息拒答。
- UI 检查：采用现有宣纸/朱砂设计 token、Lucide 图标、键盘可操作控件、可见焦点、移动端安全区、减少动画和异步错误恢复。

## 技术边界

- Spring Boot 负责 JWT、主体归属、工具白名单、业务事务、幂等和审计；FastAPI 负责 Agent Runtime、LangGraph 和流式编排。
- LangGraph 只管理用户多步骤流程；首期没有高风险用户写工具，因此没有强行启用 interrupt，确认基础设施保留给后续下单、取消和售后申请。
- RAG 只用于知识解释，价格、库存、购物车、订单和售后事实由 Java 业务服务提供。

## 已知限制和后续动作

- `V20260922_02__generalize_agent_actor_identity.sql` 已执行；部署时只允许继续向前执行 `V20260922_03`、`V20260926_01` 和 `V20260926_02`。
- LangGraph 当前使用进程内 `InMemorySaver`，多实例生产环境应替换为 Redis checkpoint，并保留 MySQL 任务事件兜底。
- 用户公共知识库表和 Qdrant 集合已经独立，但尚未开放公共知识发布入口；知识问答上线前仍需由服务端绑定经过审核的公共知识版本，不能接收用户任意 `kb_id`。
- 第一阶段不开放支付、下单、取消订单、催单和创建售后申请；这些操作进入第二阶段后必须接入确认中断、审计和幂等门禁。
