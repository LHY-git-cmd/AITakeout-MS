# 用户端 Agent 产品实施计划书

## 1. 项目目标

基于现有苍穹外卖项目，建设面向普通用户的综合餐饮助手，首发于现有用户端 Web。Agent 覆盖菜品问答、智能推荐、购物车辅助、订单查询和售后状态查询，并通过结构化卡片和 SSE 流式事件与用户交互。

第一阶段不允许 Agent 绕过业务服务直接访问数据库，也不允许 Agent 自动完成支付。下单、取消订单、退款和售后申请等高风险操作进入第二阶段，并统一采用用户确认机制。

## 2. 现状与前置改造结论

当前项目已经具备：

- `frontend/project-sky-user-web` 用户端 Web；
- Spring Boot 用户业务接口；
- FastAPI `agent-service`；
- Qdrant、bge-m3、RAG 和 SSE 任务队列；
- Agent 会话、任务、消息、事件和工具确认数据表。

当前 Agent 数据模型和权限模型偏向管理端：`actor_role` 仅支持 `SUPER_ADMIN/ADMIN`，工具确认表使用 `employee_id`。因此必须先完成多主体模型改造，不能直接把管理端 Agent 暴露给普通用户。

## 3. 技术选型声明

1. Spring Boot 作为用户 Agent Gateway，负责 JWT、用户归属、业务工具代理、幂等、确认和审计；FastAPI 作为 Agent Runtime，负责模型编排和流式输出。
2. LangGraph 只用于有状态、多步骤、可暂停恢复的工作流；普通问答不强制引入 LangGraph，不采用旧式自由执行 AgentExecutor。
3. 业务事实由 Spring Boot 提供，RAG 只负责知识解释；价格、库存、订单状态、售后资格和事务不交给模型或向量库决定。

## 4. 目标架构

```text
用户端 Web
    ↓ JWT
Spring Boot User Agent Gateway
    ├── 身份与权限
    ├── 会话、任务、消息、事件持久化
    ├── 工具代理与幂等
    ├── 用户确认
    └── SSE 转发与断线恢复
            ↓ 内部服务令牌
FastAPI User Agent Runtime
    ├── LangGraph 用户工作流
    ├── 意图识别与参数收集
    ├── RAG 与推荐编排
    ├── User Tool Adapter
    └── LLM Gateway
            ├── Spring Boot 业务服务
            ├── Qdrant / bge-m3
            ├── Redis 工作流状态
            └── 现有 OpenAI-compatible LLM
```

数据权威划分：

| 数据 | 权威来源 |
|---|---|
| 用户身份 | Spring Boot JWT |
| 价格、库存、上下架 | Spring Boot 业务服务 |
| 购物车、订单、售后 | Spring Boot/MySQL |
| 会话、消息、审计事件 | Spring Boot/MySQL |
| 活跃工作流状态 | Redis |
| 菜品说明、配送规则、售后政策 | Qdrant/RAG |
| 自然语言理解与回答 | LLM |

## 5. 实施阶段与模块

### 模块一：多主体身份和数据模型

目标：让同一套 Agent 基础设施安全支持管理员和普通用户。

主要工作：

- 将 Agent 主体抽象为 `actor_type`、`actor_id`、`actor_role`；
- 保留管理员兼容逻辑，新增 `USER/CUSTOMER` 主体；
- 调整 `agent_task`、`agent_tool_audit`、`agent_tool_confirmation` 的主体字段和索引；
- 确认凭证以任务主体为准，不信任 Python 或前端提交的用户 ID；
- 为用户任务增加会话归属、请求哈希和幂等校验；
- 检查现有管理员数据迁移的兼容性。

交付物：

- 数据库迁移脚本；
- Java 主体模型和权限策略；
- 管理端回归测试；
- 用户主体的任务、确认和审计契约。

### 模块二：用户 Agent Gateway

目标：在 Spring Boot 中建立用户侧安全入口。

建议接口：

```text
POST /user/agent/sessions
GET  /user/agent/sessions
GET  /user/agent/sessions/{sessionId}
POST /user/agent/tasks
GET  /user/agent/tasks/{taskId}
GET  /user/agent/tasks/{taskId}/events
POST /user/agent/tasks/{taskId}/cancel
POST /user/agent/confirmations/{confirmationId}/approve
POST /user/agent/confirmations/{confirmationId}/reject
```

主要工作：

- 从 JWT 获取用户身份，不接受请求体中的可信 `userId`；
- 校验 session、task、order、cart 的用户归属；
- 创建任务并调用 FastAPI Runtime；
- 保存用户消息、Agent 事件和结构化响应；
- 代理 SSE，并支持 `Last-Event-ID` 断线恢复；
- 管理用户确认状态和过期状态。

### 模块三：用户工具目录与权限策略

目标：让 Agent 只能通过受控工具访问业务系统。

只读工具：

- `get_shop_status`
- `search_products`
- `get_product_detail`
- `get_cart`
- `list_user_orders`
- `get_order_detail`
- `get_order_timeline`
- `get_after_sale_status`
- `search_policy_knowledge`

写工具：

- `add_cart_item`
- `change_cart_quantity`
- `clear_cart`
- `send_order_reminder`
- `submit_order`
- `cancel_order`
- `create_after_sale`

权限策略：

- 明确添加购物车可以低风险直接执行；
- 清空购物车、催单、下单、取消订单、售后申请必须确认；
- 支付只生成原有支付入口，不由 Agent 执行；
- 所有写操作带 `tool_call_id` 和 `idempotency_key`；
- Python 只提交任务和工具调用信息，Java 根据任务绑定主体。

### 模块四：LangGraph 用户工作流

目标：实现可暂停、可恢复、可重试、可审计的用户业务流程。

核心状态：

```text
task_id
session_id
actor_type
intent
messages
context_summary
page_context
slots
tool_results
pending_confirmation
response_blocks
error
```

主流程：

```text
加载上下文
  ↓
安全检查
  ↓
意图识别
  ├── 知识问答 → RAG → 回答
  ├── 菜品推荐 → 结构化筛选 → 语义召回 → 实时校验 → 卡片
  ├── 购物车 → 参数收集 → 工具调用
  ├── 订单查询 → 订单工具 → 订单卡片
  ├── 售后 → 订单和资格检查 → 状态或申请预览
  └── 信息不足 → 追问
  ↓
结构化响应
  ↓
保存事件
```

涉及确认的流程使用 LangGraph interrupt；活跃状态存 Redis，长期会话、事件和审计记录存 MySQL。

### 模块五：用户端交互和结构化卡片

目标：让 Agent 输出能被前端可靠执行的 UI 数据，而不是只输出 Markdown。

SSE 事件类型：

- `task_started`
- `message_delta`
- `recommendation_cards`
- `tool_started`
- `tool_completed`
- `confirmation_required`
- `business_state_changed`
- `task_completed`
- `task_failed`

前端组件：

- Agent 悬浮入口；
- 全屏对话页；
- 菜品推荐卡片；
- 购物车变更卡片；
- 订单状态卡片；
- 确认弹窗；
- 错误和人工客服降级组件。

价格、商品 ID、订单 ID、操作按钮等必须通过结构化字段传递，禁止前端从 Markdown 中解析。

### 模块六：评测、可观测性和运营

目标：保证 Agent 上线后可观测、可回归和可控成本。

主要工作：

- OpenTelemetry trace 串联用户请求、任务、模型调用和工具调用；
- Prometheus 记录延迟、错误率、工具成功率和 token 成本；
- 建立问答、推荐、订单查询、越权和确认流程评测集；
- 建立模型输出和工具参数的回归测试；
- 增加敏感信息脱敏和提示词注入检测；
- 增加模型、温度、超时、限流和降级配置；
- 建立知识库版本和运营发布流程。

## 6. MVP范围

### MVP包含

- 会话创建、历史会话和连续对话；
- 菜品、配送和售后政策问答；
- 按预算、口味、人数和忌口推荐菜品；
- 菜品结构化卡片；
- 查询和添加购物车；
- 查询历史订单和订单详情；
- 查询订单时间线；
- 查询售后状态；
- SSE 流式输出和断线恢复；
- 用户身份隔离、工具审计和幂等；
- 用户确认基础设施。

### 第二阶段

- 提交订单；
- 取消订单；
- 催单；
- 创建售后申请；
- 人工客服转接；
- 长期口味偏好；
- 更复杂的个性化推荐。

## 7. 测试策略

### Java/Spring Boot

- Controller 鉴权测试；
- 用户资源归属测试；
- 工具权限矩阵测试；
- 幂等和重复请求测试；
- 确认状态机测试；
- SSE 事件顺序和断线恢复测试；
- 管理端兼容回归测试。

### Python/FastAPI

- LangGraph 节点单元测试；
- 意图路由测试；
- 工具 schema 测试；
- 确认中断和恢复测试；
- RAG 检索和拒答测试；
- LLM Gateway 超时、重试和降级测试；
- 工具调用评测集。

### 前端

- SSE 事件解析测试；
- 卡片渲染测试；
- 确认弹窗操作测试；
- 断线重连测试；
- 未登录和过期登录态测试。

运行测试时，Maven 输出写入日志文件，只读取末尾统计和失败报告；Python 和前端只返回失败摘要或最终统计，避免重复执行同一套测试。

## 8. 验收指标

| 指标 | 目标 |
|---|---:|
| 用户越权成功数 | 0 |
| 需要确认的写操作覆盖率 | 100% |
| 可售菜品推荐率 | 100% |
| 订单归属校验率 | 100% |
| 重复写操作 | 0 |
| 工具参数结构化成功率 | ≥95% |
| SSE 断线恢复成功率 | ≥99% |
| 常见问题回答正确率 | ≥90% |
| 首个可见响应 P95 | ≤2.5 秒 |
| 只读工具成功率 | ≥99% |
| 无法完成时的明确降级率 | 100% |

## 9. 主要风险与应对

| 风险 | 应对 |
|---|---|
| 管理端和用户端主体混淆 | 先完成多主体模型，再开放用户入口 |
| 模型编造价格或库存 | 所有实时事实必须调用业务工具 |
| 用户误触高风险操作 | 统一预览、确认、过期和幂等机制 |
| SSE 断线丢消息 | MySQL 事件序列号 + `Last-Event-ID` 回放 |
| LangGraph 状态丢失 | Redis checkpoint + MySQL 任务状态兜底 |
| 工具重复执行 | `task_id + tool_call_id + idempotency_key` 唯一约束 |
| RAG 内容过期 | 知识库版本、发布状态和运营审核 |
| LLM 成本失控 | 模型白名单、上下文上限、限流和 token 统计 |

## 10. 计划执行顺序

```text
模块一：多主体模型
   ↓
模块二：用户 Agent Gateway
   ↓
模块三：用户工具目录
   ↓
模块四：LangGraph 工作流
   ↓
模块五：用户端 UI 和事件协议
   ↓
模块六：评测、观测和上线验收
```

每个模块完成后需要输出：

1. 当前模块的实现产出；
2. 接口或数据模型变化；
3. 测试结果摘要；
4. 已知风险和下一模块前置条件。

只有模块一至模块五完成并通过安全、业务和交互验收后，才进入第二阶段的高风险业务操作。

