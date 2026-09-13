# 第二阶段 Python AI 工具调用方案

## 1. 方案结论

采用“Python 编排、Java 授权与执行”的双层架构：

- Python 使用 OpenAI 兼容协议的原生 `tools/tool_calls` 完成意图识别、工具选择、多轮调用和最终回答。
- Python 是工具目录、角色工具集和多步工作流的实现端。
- Java 是管理员身份、最终权限、业务状态、事务、幂等和审计的唯一权威端。
- Python 不直连业务数据库，不保存管理员 JWT，不依据模型输出决定是否授权。
- 查询工具可在鉴权后直接执行；写工具必须经过参数预检和用户确认后执行。

该方案复用现有 `AsyncOpenAI`、Pydantic、`httpx`、异步任务队列和 SSE 链路，不在本阶段引入 LangChain、LangGraph 或多 Agent 框架。

## 2. 技术选型声明

| 能力 | 选型 | 原因 |
| --- | --- | --- |
| LLM 工具调用 | OpenAI Python SDK 原生 `tools/tool_calls` | 当前项目已使用 `openai.AsyncOpenAI`，改造范围最小 |
| 工具参数模型 | Pydantic v2 + JSON Schema | 强类型校验，可直接生成模型工具 Schema |
| Java 内部调用 | `httpx.AsyncClient` | 当前项目已有依赖，支持异步、连接池和分层超时 |
| 编排状态 | 现有 TaskQueue + 可持久化工具执行记录 | 与现有 task_id、SSE、取消机制一致 |
| 工具注册与编排 | Python本地 Registry | 工具名称、描述、Pydantic参数、角色工具集和多步流程统一由Python管理 |
| 授权与业务执行 | Java原子业务接口 | 复用Java身份、权限和领域服务，避免Python直写数据库 |
| 用户确认 | Java 生成一次性确认凭证 | 防止模型伪造确认或篡改参数 |

三条关键改进建议：

1. Java创建任务时传入服务端确定的角色；Python按角色过滤本地工具集合，而Java在每次实际执行时仍重新鉴权。
2. 写操作采用“预检—确认—执行”三段式协议，确认凭证绑定管理员、工具、参数哈希和有效期。
3. 工具调用和模型文本使用不同 SSE 事件，前端不得把模型声称的“已执行”当成真实业务结果。

## 3. 总体架构

```text
管理端前端
   |
   | JWT
   v
Java sky-server
   |- 验证管理员身份和会话归属
   |- 创建 agent_task，保存 actor_id 和角色快照
   |- 提供原子业务操作接口
   `- 逐次鉴权、业务校验、事务、幂等、审计
   |
   | 内部服务认证 + task_id
   v
Python sky-agent
   |- 保存工具定义、Pydantic Schema和角色工具集
   |- 按Java传入的角色筛选暴露给LLM的工具
   |- 调用 LLM 原生 tools/tool_calls
   |- 校验工具名和参数结构
   |- 编排单工具或多工具工作流
   |- 调用 Java 原子业务接口
   |- 将结构化结果作为 tool message 回送 LLM
   `- 通过现有 SSE 输出进度、确认请求和最终回答
```

Java 不把管理员原始 JWT 转发给 Python。Java提交任务时携带由服务端确定的角色，供Python筛选工具，但该角色只影响工具可见性，不构成最终授权。Python使用独立的服务间凭证访问内部端点，Java通过 `task_id` 找到原始管理员，并按数据库中的当前角色重新判断。

## 4. 完整调用流程

### 4.1 查询工具

1. 管理员向现有 `/admin/agent/tasks/submit` 提交问题。
2. Java验证 JWT，将 `employee_id` 绑定到 `agent_task`，再向 Python 提交任务。
3. Python根据Java传入的角色，从本地工具注册表筛选本任务允许暴露的工具 Schema。
4. Python将消息和筛选后的工具定义传入 LLM。
5. LLM返回一个或多个 `tool_calls`。
6. Python只接受本地注册表和当前角色工具集中同时存在的工具，并用Pydantic校验参数。
7. Python执行对应工具函数；工具函数通过HTTP调用Java原子业务接口。
8. Java根据 `task_id` 重新解析管理员，校验工具权限、数据范围和参数，然后调用现有领域服务。
9. Java返回脱敏、限量的结构化结果；Python将其作为 `role=tool` 消息追加到对话。
10. LLM生成最终自然语言回答，Python通过 SSE 输出。

### 4.2 写工具

1. LLM提出写工具调用后，Python先调用 Java的 `prepare` 接口。
2. Java鉴权并验证对象、参数、状态转换和当前版本，只生成变更预览，不修改业务数据。
3. Python发布 `tool_confirmation_required` SSE 事件，包含面向用户的变更摘要和 `confirmation_id`。
4. 管理员在前端明确确认；确认请求直接发送给 Java。
5. Java校验确认人与原任务一致，确认凭证未过期且工具名、参数哈希、对象版本未变化。
6. Java执行写操作并记录幂等与审计结果。
7. Python收到或查询到真实执行结果后，将其作为 `tool` 消息交给 LLM生成最终回答。

模型文本中的“确认”“同意”或“已经执行”不具备确认效力。

## 5. Java 原子业务接口契约

内部端点不属于公开管理端 API，只允许 `sky-agent` 使用服务间认证访问。Java不实现LLM工具选择和多步工作流，只接收一个明确的原子业务操作。

### 5.1 执行原子操作

```http
POST /internal/agent/operations/execute
```

```json
{
  "task_id": "task-id",
  "tool_call_id": "call-id-from-model",
  "operation": "order.query",
  "arguments": {
    "page": 1,
    "page_size": 20
  }
}
```

Java根据 `task_id` 获取原始管理员，根据 `operation` 映射所需权限并执行对应领域服务。Java返回统一操作信封：

```json
{
  "tool_call_id": "call-id-from-model",
  "status": "success",
  "data": {},
  "error": null,
  "trace_id": "trace-id"
}
```

### 5.2 准备写操作

```http
POST /internal/agent/operations/prepare
```

响应示例：

```json
{
  "status": "confirmation_required",
  "confirmation_id": "opaque-id",
  "expires_at": "2026-09-13T13:00:00-04:00",
  "summary": "将订单 12345 从待接单变更为已接单",
  "argument_hash": "sha256",
  "object_version": 7
}
```

### 5.3 用户确认与执行

```http
POST /admin/agent/tool-confirmations/{confirmationId}/confirm
```

该请求必须携带管理员 JWT。Java确认成功后执行或触发执行，返回最终业务结果。拒绝操作使用独立的 `/reject` 端点，并记录审计。

## 6. Python 模块设计

建议新增以下模块，不把工具逻辑堆叠在现有 `agent.py`：

```text
sky-agent/app/tools/
  models.py          # ToolDefinition、ToolCall、ToolResult等Pydantic模型
  registry.py        # Python工具注册、Schema生成和角色工具集
  context.py         # task_id、role、trace_id等可信任务上下文
  java_client.py     # httpx 连接池、服务认证、超时和错误映射
  orchestrator.py    # LLM -> tool_calls -> Java -> tool message 循环
  events.py          # tool_start/result/confirmation_required 等事件结构
  definitions/
    employee.py      # 员工查询工具定义与Java调用包装
    knowledge.py     # 知识库只读工具定义与Java调用包装
    order.py         # 订单查询和状态工作流
    dish.py          # 菜品工具
    setmeal.py       # 套餐工具
    shop.py          # 店铺工具
    report.py        # 经营数据工具
```

现有模块的修改职责：

- `app/llm/gateway.py`：支持向 `chat.completions.create` 传入 `tools`、`tool_choice`，并正确汇聚流式 `tool_calls` 参数片段。
- `app/core/agent.py`：把文本生成入口委托给 `ToolOrchestrator`，保留 RAG 上下文拼装。
- `app/core/task_queue.py`：发布工具事件、支持等待确认和取消。
- `app/models/schemas.py`：增加工具能力上下文和确认状态 DTO，但不接收客户端自报角色。

## 7. Python 编排伪代码

```python
messages = build_messages(query, history, rag_context)
tools = registry.schemas_for_role(context.role)

for step in range(MAX_TOOL_STEPS):
    response = await llm.complete(
        messages=messages,
        tools=tools,
        tool_choice="auto",
    )
    assistant_message = response.choices[0].message
    messages.append(assistant_message)

    if not assistant_message.tool_calls:
        return assistant_message.content

    for call in assistant_message.tool_calls:
        result = await registry.execute(call, context)
        messages.append({
            "role": "tool",
            "tool_call_id": call.id,
            "content": result.model_dump_json(),
        })

raise ToolLoopLimitError()
```

必须设置最大工具步数、最大单次返回大小和总执行时限。第一版建议一个任务最多 5 个工具步骤；写操作一次只允许准备一个，进入确认后暂停继续规划。

## 8. SSE 事件扩展

在现有任务事件链路增加：

| 事件 | 用途 |
| --- | --- |
| `tool_call_start` | 展示正在查询或准备操作，不暴露内部推理 |
| `tool_call_result` | 返回成功、拒绝或错误摘要 |
| `tool_confirmation_required` | 请求管理员确认写操作 |
| `tool_confirmation_rejected` | 管理员拒绝或确认过期 |
| `tool_call_error` | 工具调用失败 |

事件必须包含 `task_id`、`trace_id`、`seq_no` 和 `tool_call_id`，Java继续先幂等持久化再广播。

## 9. Python工具注册与权限实现

- Python工具注册表为每个工具声明名称、描述、Pydantic参数模型、允许角色、操作类型、超时和确认策略。
- Java提交任务时传入数据库中确定的 `SUPER_ADMIN` 或 `ADMIN`；Python按角色过滤工具集合，减少模型选择无权限工具的概率。
- Python拒绝未注册工具、当前角色不可见工具以及不满足Pydantic Schema的参数。
- Java为每个原子 `operation` 映射所需权限，并在执行时根据 `task_id` 重新查询当前管理员身份和角色。Python过滤不能替代Java最终鉴权。
- 普通管理员允许员工和知识库读取，但禁止 `employee:write`、`knowledge:write`。
- 超级管理员 `admin` 拥有全部已登记权限。
- 数据库角色字段是唯一授权依据，建议值为 `SUPER_ADMIN` 和 `ADMIN`；不得使用用户名等于 `admin` 作为授权依据。

角色工具集由Python集中维护，例如：

```python
ROLE_ALLOWED_TOOLS = {
    "SUPER_ADMIN": set(ALL_TOOL_NAMES),
    "ADMIN": {
        "query_employees", "query_knowledge_bases",
        "query_orders", "get_order_detail", "update_order_status",
        "query_dishes", "update_dish",
        "query_setmeals", "update_setmeal",
        "get_shop_status", "update_shop_status",
        "get_business_overview", "get_business_report",
    },
}
```

### 9.1 敏感字段输出模型

密码、密码摘要和认证字段不得进入Python，也不得依赖提示词要求模型自行删除。Java为AI工具建立专用VO，只返回字段白名单：

```text
EmployeeToolVO
  id
  username
  name
  maskedPhone
  maskedIdNumber
  status
```

手机号、身份证号在Java映射VO时完成脱敏。数据库实体不直接序列化为工具结果；Python的Pydantic结果模型再做一次字段约束。

## 10. 安全与可靠性

- Java与Python使用独立服务凭证；凭证仅用于证明调用来自 Agent，不代表管理员权限。
- `task_id` 必须绑定原始管理员，Python传入的 `user_id`、角色和权限均不作为授权依据。
- Java拒绝未知原子操作、未知字段、越界分页、非法日期和非法状态转换。
- 工具结果进入 LLM前进行字段白名单和长度限制；数据库实体不可直接序列化给模型。
- 外部文档和工具结果都视为不可信内容，不能改变系统提示或工具授权规则。
- 查询工具允许有限安全重试；写工具由 Java幂等控制，Python不得盲目重试未知结果。
- 任务取消必须传播到进行中的 Java工具请求；已经提交的事务按 Java结果为准。

## 11. 测试与验收

### 11.1 Python

- 工具 Schema解析、未知工具和非法 JSON 参数。
- 单工具、多工具、无工具、达到最大步数。
- 流式 `tool_calls` 参数分片重组。
- Java超时、403、404、409、5xx和取消传播。
- 确认暂停、恢复、拒绝和过期。
- Prompt Injection不能新增工具或修改授权上下文。

### 11.2 Java

- 超级管理员和普通管理员权限矩阵。
- 普通管理员员工/知识库可读、CUD拒绝。
- 每个工具执行前重新鉴权。
- 状态转换、对象版本、幂等键和参数哈希。
- 工具结果字段白名单、脱敏和数量限制。
- 审计完整性与敏感信息不落日志。

### 11.3 端到端

- “查询今天订单情况”能够调用查询工具并基于真实结果回答。
- “把订单 123 接单”先展示变更预览，确认后才执行。
- 模型伪造 `admin`、修改工具名或直接声称确认均不能越权。
- 普通管理员可以查看员工或知识库，但不能通过 AI新增、修改或删除。
- SSE断线重连后工具事件顺序和最终状态保持一致。

## 12. 实施顺序

1. Java补充角色/权限权威模型、任务身份绑定和内部服务认证。
2. Python建立本地工具注册表、Pydantic Schema和角色工具集。
3. Java完成原子业务执行接口、Tool专用VO、审计表和统一错误信封。
4. Python扩展 `LLMGateway` 并实现只读工具编排循环。
5. 接入员工、知识库、订单、菜品、套餐和经营数据查询工具并完成端到端测试。
6. 增加写操作 `prepare/confirm/execute` 协议和前端确认交互。
7. 接入订单、菜品、套餐和店铺状态写工具。
8. 完成权限、幂等、故障、注入攻击和质量回归后再开放生产使用。

每个模块完成后单独验收；只读工具稳定前不开放写工具。
