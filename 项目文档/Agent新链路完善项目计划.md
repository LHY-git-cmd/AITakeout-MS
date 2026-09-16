# Agent 新链路完善项目计划

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | Agent 新链路完善项目计划 |
| 文档版本 | V1.0 |
| 编写日期 | 2026-08-26 |
| Java 项目 | `E:\code\web-sky-project\skyproject\sky-take-out` |
| Python 项目 | `E:\Pyhon\FastAPIProject1` |
| 使用主体 | 管理端员工 |
| 认证方式 | Java 管理端 JWT |
| 权威数据源 | Java + MySQL |
| Python 运行方式 | 单实例内存任务队列，暂不引入 Redis |

## 2. 已确认范围

本次完善以下新链路：

```text
管理端提交任务
  -> Java 校验会话归属并组装历史上下文
  -> Python 异步执行模型任务
  -> Java 为该任务建立唯一上游 SSE 订阅
  -> Java 持久化事件、任务状态和最终助手消息
  -> 一个或多个管理端前端从 Java SSE 接收实时事件
  -> 前端断线后使用 Last-Event-ID 从 Java/MySQL 续传
  -> 取消任务时 Java 和 Python 状态同步
```

本次不包含：

- 不删除 `/admin/agent/query`、`/admin/agent/stream` 等旧接口。
- 不迁移到 `/user/agent/**`，不接入普通用户 JWT。
- 不引入 Redis、消息队列或多实例任务调度。
- 不新增 Agent 工具调用、知识库、订单写操作等能力。
- 不重做现有管理端聊天页面。

## 3. 前提检查与限制

### 3.1 已发现的错误前提

- `session_id` 本身不会让模型获得记忆；Java 必须根据它查询历史消息并传递 `context.history`。
- 提交接口只负责返回任务标识；模型输出由独立 SSE 接口返回。
- Python 任务状态不能同时作为业务权威状态，否则会与 Java/MySQL 形成双写冲突。

### 3.2 无 Redis 版本限制

- Python 单实例运行期间可以保存任务、事件历史和取消句柄。
- Java 重启后可以根据 MySQL 中未结束任务重新订阅仍在运行的 Python 任务。
- Python 重启后，内存任务和事件不可恢复；Java 应将无法在 Python 查询到的未结束任务标记为失败。
- 不支持 Python 多实例负载均衡；请求必须固定到同一实例。
- Java 多实例不在本次支持范围内；否则需要分布式任务订阅锁和跨实例事件广播。

## 4. 技术选型声明

### 4.1 核心选型

1. Java/MySQL 是会话、任务、消息和事件的唯一业务权威数据源。
2. Python 仅负责模型执行、短期事件缓存和任务取消，不直接写业务数据库。
3. Java 每个任务只建立一条到 Python 的上游 SSE 连接；前端连接由 Java 本地订阅中心扇出。
4. Java 对前端继续使用 SSE，前端使用支持自定义 JWT 请求头的 `fetch` 流式读取，不把 JWT 放入 URL。
5. Python 使用 `asyncio.Task` 和内存事件列表实现单实例任务生命周期。

### 4.2 三条关键改进建议

1. 统一事件协议：每个事件必须包含 `task_id`、`seq_no`、`event`、`data`，结束事件必须包含完整最终结果。
2. 先持久化再广播：Java 收到 Python 事件后先幂等写入 MySQL并更新业务状态，再推送给前端，避免前端看到数据库中不存在的状态。
3. 所有任务接口执行员工归属校验：任务详情、取消和事件订阅都必须验证 `task.user_id == BaseContext.currentId`。

## 5. 目标接口

### 5.1 Java 对管理端接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/admin/agent/tasks/submit` | 提交任务，返回任务、会话及事件地址 |
| GET | `/admin/agent/tasks/{taskId}` | 查询 MySQL 中的权威任务状态 |
| GET | `/admin/agent/tasks/{taskId}/events` | 订阅 Java SSE，支持 `Last-Event-ID` |
| POST | `/admin/agent/tasks/{taskId}/cancel` | 幂等取消 Java 与 Python 任务 |
| GET | `/admin/agent/sessions/{sessionId}` | 查询会话及已持久化消息 |

提交响应目标结构：

```json
{
  "taskId": "task-uuid",
  "sessionId": "session-uuid",
  "eventsUrl": "/admin/agent/tasks/task-uuid/events",
  "status": 0
}
```

### 5.2 Java 调用 Python 接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/api/v1/agent/submit` | 使用 Java 生成的 `task_id` 提交任务 |
| GET | `/api/v1/agent/stream/{task_id}` | 从 `Last-Event-ID` 后订阅事件 |
| GET | `/api/v1/agent/status/{task_id}` | 恢复和诊断时查询 Python 临时状态 |
| POST | `/api/v1/agent/tasks/{task_id}/cancel` | 取消 Python `asyncio.Task` |

Python 提交请求目标结构：

```json
{
  "task_id": "task-uuid",
  "session_id": "session-uuid",
  "user_id": 1,
  "query": "请用一句话介绍自己",
  "model": "deepseek-v4-pro",
  "temperature": 0.7,
  "context": {
    "history": [
      {"role": "user", "content": "上一轮问题"},
      {"role": "assistant", "content": "上一轮回答"}
    ]
  }
}
```

## 6. 统一事件协议

Python SSE 的每条 `data` 是完整 JSON：

```json
{
  "task_id": "task-uuid",
  "seq_no": 2,
  "event": "token",
  "data": {
    "content": "你好"
  }
}
```

事件类型：

| 事件 | 说明 | Java 状态处理 |
| --- | --- | --- |
| `task_start` | Python 开始执行 | 任务状态改为执行中，进度至少为 1 |
| `token` | 模型文本增量 | 持久化事件并向前端广播 |
| `task_end` | 正常完成 | 保存完整助手消息，任务状态改为完成、进度 100 |
| `task_error` | 执行失败 | 保存错误，任务状态改为失败 |
| `task_cancelled` | 取消完成 | 任务状态改为取消 |

SSE 规则：

- Python 和 Java 都输出 `id: <seq_no>`。
- `seq_no` 在单个任务内从 1 单调递增。
- Java 以 `(task_id, seq_no)` 作为事件幂等键。
- `Last-Event-ID=N` 表示只返回 `seq_no > N` 的事件。
- `task_end.data.result` 必须包含完整回答，Java 不依赖内存 token 拼接生成最终消息。
- Java 对历史事件与实时事件使用相同 JSON 结构，不允许两套响应格式。

## 7. 状态模型

Java 状态保持现有整数定义：

| Java 状态 | 含义 | Python 临时状态 |
| --- | --- | --- |
| 0 | 排队 | `pending` |
| 1 | 执行中 | `running` |
| 2 | 完成 | `completed` |
| 3 | 失败 | `failed` |
| 4 | 取消 | `cancelled` |

状态转换规则：

```text
排队 -> 执行中 -> 完成
  |        |       
  +------> 失败
  |        |
  +------> 取消
```

- 结束状态不可被后到事件覆盖。
- 取消接口幂等；重复取消已经取消的任务返回成功。
- 已完成或失败任务不可再取消。
- 数据库更新使用期望状态条件，避免取消与完成事件并发覆盖。

## 8. 数据库改造

新增 Flyway 迁移，至少完成：

- 为 `agent_event(task_id, seq_no)` 增加唯一索引。
- 为未结束任务恢复扫描补充必要索引，优先复用现有状态和创建时间索引。
- 不存储每个 token 的重复完整回答；`task_end` 保存完整结果，`token` 只保存增量。
- 保持现有表名和实体，避免不必要的数据模型重建。

迁移前先通过 `SHOW CREATE TABLE` 核对当前实际数据库，脚本必须可重复由 Flyway 管理，不能手工修改生产表。

## 9. 实施模块

### 模块一：协议与 Python 执行生命周期

工作内容：

- 扩展 `AgentRequest`：增加 `task_id`、`session_id`、`model`。
- 对模型名设置允许列表或服务端默认值，不直接信任任意模型字符串。
- `TaskQueue` 保存 `asyncio.Task` 句柄、完整事件列表和递增序号。
- 实现带 `Last-Event-ID` 的事件回放。
- 增加真正的取消接口和 `cancelled` 终态。
- 统一结束、失败和取消事件结构。
- 修复 `user_id` 列表过滤的 `str/int` 类型不一致。

主要文件：

- `E:\Pyhon\FastAPIProject1\app\models\schemas.py`
- `E:\Pyhon\FastAPIProject1\app\core\task_queue.py`
- `E:\Pyhon\FastAPIProject1\app\core\agent.py`
- `E:\Pyhon\FastAPIProject1\app\api\routes.py`
- Python 单元测试文件

模块验收：提交、事件顺序、完成、失败、取消、迟到订阅和断线续传测试通过。

### 模块二：Java-Python 强类型客户端与上下文

工作内容：

- 使用明确的请求和响应 DTO 替代主链路中的 `Map` 与裸 JSON 字符串。
- Java 生成 `taskId` 并传给 Python。
- 根据 `sessionId` 查询属于当前员工的最近历史消息，映射为模型 `history`。
- 限制历史消息条数和总字符量，避免上下文无限增长。
- 校验 Python 提交响应中的 `task_id` 与 Java 请求一致。
- 为上游 SSE 客户端增加 `Last-Event-ID` 和结构化事件解析。

主要文件：

- `sky-common/src/main/java/com/sky/agent/AgentClient.java`
- `sky-pojo/src/main/java/com/sky/dto/` 下 Agent DTO
- `sky-pojo/src/main/java/com/sky/vo/AgentSubmitVO.java`
- `sky-server/src/main/java/com/sky/mapper/AgentMessageMapper.java`
- 相关 Mapper XML

模块验收：契约序列化测试、历史上下文测试和 Python 模拟服务集成测试通过。

### 模块三：Java 事件持久化与唯一上游订阅

工作内容：

- 增加 Flyway 事件幂等索引迁移。
- 建立 Java 本地任务订阅协调器，确保同一 `taskId` 只有一个 Python SSE 连接。
- 接收事件后在事务中幂等写入 `agent_event`。
- 根据事件以期望状态更新 `agent_task`。
- 完成时写入唯一助手消息并更新 `assistant_message_id`、会话消息数和更新时间。
- 失败、取消时保存终态和错误信息。
- 应用启动时扫描未结束任务并尝试恢复订阅。

主要文件：

- 新增 Agent 任务事件协调服务
- `AgentServiceImpl.java`
- `AgentTaskMapper.java/.xml`
- `AgentEventMapper.java/.xml`
- `AgentMessageMapper.java/.xml`
- `AgentSessionMapper.java/.xml`
- 新增 Flyway 迁移脚本

模块验收：重复事件不重复入库、结束状态不回退、助手消息只写一次、Java重启恢复测试通过。

### 模块四：Java 对前端 SSE、鉴权与取消

工作内容：

- SSE 建连前校验任务属于当前员工。
- 先从 MySQL 回放 `Last-Event-ID` 后的历史事件，再订阅本地实时事件。
- 消除 Controller 中每连接创建裸线程的实现，使用受控执行器和本地订阅中心。
- 为实时事件设置 SSE `id` 和 `event`，历史与实时结构一致。
- 前端断开时及时移除订阅，不影响唯一上游连接。
- 取消接口先执行权威状态竞争，再调用 Python 取消；失败时进行状态补偿或返回明确错误。
- 任务详情、取消和事件接口全部增加员工归属校验。

主要文件：

- `AgentController.java`
- `AgentService.java`
- `AgentServiceImpl.java`
- 新增本地 SSE 订阅中心
- `AgentClient.java`

模块验收：越权返回拒绝、多个前端只产生一个上游连接、断线续传无重复或遗漏、取消后收到终态事件。

### 模块五：回归、文档与旧接口隔离

工作内容：

- 运行 Java 全量测试与 Python 测试。
- 对真实 Java + Python + MySQL 环境执行端到端冒烟测试。
- 验证首轮对话、连续多轮、刷新重连、取消、模型失败和 Python 不可用。
- 在 OpenAPI 中明确新接口协议和无 Redis 限制。
- 旧接口保留但标注兼容接口，不让新前端继续使用。
- 不在本模块删除旧接口。

模块验收：端到端场景通过，原有登录、订单和后台接口没有回归。

## 10. 推荐执行顺序

1. 先完成 Python 协议和任务生命周期，使上游事件源稳定。
2. 再完成 Java 强类型客户端和历史上下文传递。
3. 然后完成 Java 事件落库、状态同步和最终消息持久化。
4. 接着替换 Java 对前端 SSE 和取消逻辑。
5. 最后执行跨项目端到端测试并更新接口文档。

每完成一个模块，展示代码差异、测试结果和未解决风险，并询问是否继续下一模块或调整方向。

## 11. 测试计划

### 11.1 Python 测试

- 请求模型接受并保留 `task_id/session_id/model`。
- 事件序号严格递增。
- 迟到订阅者可以收到指定序号后的所有事件。
- 正常完成包含完整 `result`。
- 异常产生 `task_error`。
- 取消产生 `task_cancelled`，模型协程停止。
- 相同 `task_id` 重复提交被拒绝或按既定幂等规则返回原任务。

### 11.2 Java 测试

- 当前员工只能访问自己的会话和任务。
- 历史消息按角色、顺序和限制正确映射。
- 同一事件重复到达只入库一次。
- `task_end` 只创建一条助手消息。
- 完成与取消并发时只保留一个终态。
- `Last-Event-ID` 回放边界正确。
- 多个前端订阅不会创建多个 Python 订阅。
- Python 404、超时和断流被映射为可诊断的任务错误。

### 11.3 端到端场景

1. 首轮提交并完整收到流式回答。
2. 第二轮基于第一轮历史正确回答。
3. 流式过程中断开，再连接后无重复、无遗漏。
4. 两个前端同时订阅同一任务，内容一致。
5. 执行中取消，Python停止且Java状态为取消。
6. Python执行失败，Java任务、事件和错误信息一致。
7. Java重启后恢复仍存在于Python内存中的任务。
8. Python重启后，Java将无法恢复的任务明确标记失败。

## 12. 完成标准

- 新链路不依赖旧 `/stream` 接口即可获得完整模型输出。
- `session_id` 能通过 Java 组装的 `context.history` 实际影响多轮回答。
- Java/MySQL 中的任务状态与最终回答完整、可查询。
- SSE 支持事件 ID、历史回放和断线续传。
- 取消操作真正停止 Python 模型任务。
- 所有任务相关接口完成员工归属校验。
- Java 与 Python 自动化测试通过，端到端核心场景通过。
- 旧接口仍保留，不影响现有调用方。

## 13. 实施结果

实施日期：2026-08-26。

### 13.1 已完成能力

- Python 请求模型已接收 `task_id`、`session_id` 和 `model`。
- Python 内存任务队列保存完整事件历史和递增 `seq_no`，支持 `Last-Event-ID` 回放。
- Python 保存实际 `asyncio.Task` 句柄，取消接口能够中止模型协程并产生 `task_cancelled`。
- Java 使用强类型请求、响应和事件模型调用 Python，不再在新链路中依赖裸 `Map`。
- Java 根据会话消息构造 `context.history`，同时限制历史消息数量和字符总量。
- Java 每个任务只建立一条上游 Python SSE 连接，并在启动时恢复未结束任务。
- Python 事件先幂等写入 MySQL，再由 Java 进程内事件中心向多个前端订阅者广播。
- `task_end` 自动更新任务完成状态并写入唯一助手消息。
- 任务详情、取消和SSE订阅均校验当前管理员归属。
- Java SSE 的历史和实时事件使用统一结构，并按序号缓冲合并。
- 取消接口等待 Python 确认，完成、失败和取消状态使用期望状态更新避免相互覆盖。
- 旧 `/query` 和 `/stream` 接口保留。

### 13.2 数据库迁移

- `V20260826_01__harden_agent_event_chain.sql`
  - 增加 `agent_event(task_id, seq_no)` 唯一约束。
  - 增加 `agent_message(task_id, role)` 唯一约束。
- `V20260826_02__serialize_agent_message_sequence.sql`
  - 增加 `agent_message(session_id, seq_no)` 唯一约束。
  - Java在分配会话消息序号前锁定对应会话行。

两条迁移均已由 Flyway 在本地 MySQL 成功执行，当前版本为 `20260826.02`。

### 13.3 自动化测试

| 测试 | 结果 |
| --- | --- |
| Python `unittest` | 4项通过 |
| Python `compileall` | 通过 |
| Java Maven全量测试 | 37项通过，0失败 |
| Java Maven打包 | 通过 |

新增测试覆盖：

- Python事件顺序、完整结果、断点回放、重复任务ID和真实协程取消。
- Java/Python字段命名契约和事件反序列化。
- Java事件重复写入、完成状态、唯一助手消息、晚到事件和取消竞争。

### 13.4 真实端到端验证

已使用本地 Java、Python、MySQL 和真实模型执行以下验证：

- 提交任务后收到 `task_start -> token -> task_end`。
- `task_end` 包含完整结果，任务状态为完成且进度为100。
- 用户消息和助手消息按顺序写入 `agent_message`。
- 携带 `Last-Event-ID: 1` 时只回放序号2之后的事件。
- 同一 `session_id` 的第二轮请求能够携带前一轮历史。
- 执行中取消后收到 `task_cancelled`，数据库状态为取消且不生成助手消息。
- 其他管理员访问任务时返回“任务不存在或无权访问”。

端到端测试产生的数据已按精确任务ID和会话ID清理，未删除原有业务数据。

### 13.5 运行地址

- Python Agent：`http://127.0.0.1:8000`
- 本次构建的 Java 服务：`http://127.0.0.1:8081`

原有 `8080` Java进程未停止或替换。

## 14. 第一版上线收口协议基线

本节于 2026-09-15 根据当前 Java、Python 实现重新核对，用于《管理端 Agent 第一版最终完善计划》的后续模块。若早期章节、代码注释或历史报告与本节冲突，以本节和当前强类型模型为准。

### 14.1 事件信封

Python 到 Java、Java 持久化以及 Java 到管理端的事件均使用同一组核心字段：

| 线上 JSON 字段 | Java 字段 | 类型 | 约束 |
| --- | --- | --- | --- |
| `task_id` | `taskId` | String | 必填，关联唯一 Agent 任务 |
| `trace_id` | `traceId` | String | 可选；缺省时使用任务 ID 关联日志 |
| `seq_no` | `seqNo` | Integer | 必填；同一任务内从 1 开始严格递增 |
| `event` | `event` | String | 必填，表示事件类型 |
| `data` | `data` | Object/String | 必填，内容由事件类型决定 |

事件唯一键固定为 `task_id + seq_no`。数据库唯一约束、历史回放和前端去重都必须使用该组合，不能只依赖事件类型或消息正文。

Java 对管理端输出 SSE 时：

- SSE `id` 等于十进制 `seq_no`；
- SSE `event` 等于事件信封中的 `event`；
- SSE `data` 为完整事件信封；
- 浏览器通过 `Last-Event-ID` 传入最后成功处理的序号，服务端只回放更大序号的事件。

### 14.2 状态和终止事件映射

| 生命周期 | Python 状态 | Java/MySQL 状态 | 对应事件 | 是否终态 |
| --- | --- | ---: | --- | --- |
| 排队 | `pending` | 0 | 尚无或等待 `task_start` | 否 |
| 执行中 | `running` | 1 | `task_start`、`token`、工具事件 | 否 |
| 完成 | `completed` | 2 | `task_end` | 是 |
| 失败 | `failed` | 3 | `task_error` | 是 |
| 取消 | `cancelled` | 4 | `task_cancelled` | 是 |

终止事件仅为 `task_end`、`task_error`、`task_cancelled`。早期 `task_queue.py` 顶部注释中“`task_end` 可携带 `failed`”属于历史描述；当前实现失败时发布独立的 `task_error`，后续前端和验收不得按旧描述处理。

SSE 连接关闭本身不代表任务成功。管理端只有收到终止事件，或通过 Java 任务详情查询得到状态 2、3、4，才能确认最终状态。Java/MySQL 是管理端业务终态的唯一权威源。

### 14.3 HTTP 错误分类

| HTTP 状态 | 语义 | Agent 场景 |
| ---: | --- | --- |
| 401 | 未认证或内部服务认证失败 | 管理端登录失效、Java/Python 内部令牌错误 |
| 403 | 已认证但无权访问 | 角色权限不足、资源不属于当前管理员 |
| 409 | 当前状态与请求冲突 | 任务 ID 冲突、确认已处理、确认已过期或状态竞争 |
| 503 | 当前依赖不可用 | Agent 未就绪、内部令牌未配置、核心依赖不可用 |

当前 `PermissionDeniedException` 已映射为 HTTP 403；部分任务、会话、知识库和文档归属校验仍抛出通用 `AgentBusinessException`，属于第一阶段模块五的剩余工作。对外权限文案应继续合并“不存在或无权访问”，避免泄露其他管理员资源是否存在。

### 14.4 第一版主链路边界

第一版唯一验收主链路为：

```text
管理端提交任务
  -> Java 创建任务并持久化用户消息
  -> Java 调用 Python /api/v1/agent/submit
  -> Java 唯一上游订阅消费 Python SSE
  -> Java 先持久化事件和终态，再向管理端广播
  -> 管理端订阅 /admin/agent/tasks/{taskId}/events
  -> 管理端以终止事件或 Java 任务详情确认最终状态
```

旧 `/query` 和 `/stream` 接口仅保留兼容，不纳入第一版发布验收、质量门禁或新功能扩展。后续修复不得让新主链路重新依赖旧接口。

### 14.5 验收证据要求

每个验收场景必须记录场景 ID、环境版本、输入、预期结果、实际结果、任务 ID、证据路径和结论。涉及写工具时还必须记录确认 ID、审计记录以及业务对象执行前后状态。只填写“通过”而没有任务或证据定位信息的记录无效。
