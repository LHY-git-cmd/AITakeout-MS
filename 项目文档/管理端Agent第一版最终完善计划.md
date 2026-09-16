# 管理端 Agent 第一版最终完善计划

> **执行要求：** 实施本计划时，应按模块逐项执行、逐项验证、逐项提交。每完成一个模块，展示代码差异、测试结果和未解决风险，并询问：“是否继续下一模块？还是需要调整当前方向？”

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 计划目标 | 将现有管理端 Agent 收口为可在单机 Docker Compose 环境稳定上线的第一版 |
| 实施范围 | 已确认的 P0 必须项、P1 建议项，以及发布前回归和文档收口 |
| 第一阶段定位 | 完成 P0/P1，达到单机 Compose 稳定上线标准 |
| 第二阶段定位 | 将第一阶段能力标准化、自动化，并完成可复用性验收测试 |
| 状态权威源 | Java + MySQL |
| Agent 运行约束 | Python Agent 保持单进程、单 Uvicorn worker |
| 计划日期 | 2026-09-15 |

## 2. 前提检查与现状结论

### 2.1 需要修正的前提

管理端并非“尚未实现 Agent”。当前系统已经具备真实 LLM、上下文会话、RAG、引用、任务状态、SSE、工具选择、多轮工具调用、写操作确认、Java 权威鉴权、幂等和审计能力。本计划不重新建设 Agent，而是完成第一版上线收口。

较早的《AI助手RAG与知识库建设实施报告》存在已经失效的结论，例如 Java 主链路、知识库范围和引用持久化尚未接通。当前代码和后续实施报告表明这些能力已经补齐，实施时必须以当前代码和最新验证结果为准。

### 2.2 当前已完成基础

- Java、Python、MySQL 与真实模型的 Submit + SSE 主链路已完成验证。
- 后端 SSE 已具有递增 `seq_no`、事件 ID、MySQL 历史回放和 `Last-Event-ID` 支持。
- RAG 已接入 BGE-M3、Qdrant、引用持久化、无证据拒答和越权过滤。
- Python 已具有 18 个工具定义，其中 14 个只读工具、4 个需要确认的写工具。
- Java 已实现权限终审、确认凭证、对象版本校验、事务、幂等和工具审计。
- 已有 50 条 RAG 隔离评测及 12 条真实模型分层回归记录。
- Docker Compose 已编排 MySQL、Redis、Qdrant、Embedding、Agent 和 Java 服务。

### 2.3 当前真实缺口

1. 管理端前端未消费 SSE 事件 ID，未实现断线重连、事件去重和最终状态校验。
2. 最新工具调用能力尚缺完整 Compose 环境的系统性验收证据。
3. 现有质量基线偏重 RAG，缺少工具选择、参数生成和多步执行专项评测。
4. 页面“Agent 服务在线”为静态文案，不能反映真实依赖状态。
5. 前端错误信息过于笼统，无法指导用户恢复。
6. `PermissionDeniedException` 已返回 403，但部分 Agent 归属校验仍抛出通用 `AgentBusinessException`，导致权限语义尚未完全统一。
7. 多份文档中的完成状态互相冲突，容易误导后续实施。

## 3. 范围与边界

### 3.1 第一阶段包含

- 前端 SSE 事件序号、去重、有限重连和最终状态校验。
- 完整 Docker Compose 环境的真实端到端验收。
- Agent 工具调用专项评测集和第一版质量门槛。
- Agent、Qdrant、Embedding 和 LLM 状态的真实展示。
- 管理端错误分类、恢复提示和可重试动作。
- Agent 相关权限异常统一返回 HTTP 403。
- 旧文档状态清理、发布清单和最终实施报告。

### 3.2 第一阶段不包含

- 用户端 Agent。
- 新增业务工具或扩大现有工具权限。
- 多 Agent 架构。
- Python 任务状态迁移到 Redis、消息队列或其他共享存储。
- 多实例、高可用、水平扩容或 Kubernetes。
- Vue 3 迁移或管理端页面整体重写。
- OCR、复杂表格解析及新的 Embedding 模型。

### 3.3 第一版完成定义

第一阶段仅在以下条件全部满足时完成：

1. 单条 `docker compose up -d --build` 可启动完整服务，最终必需服务全部健康。
2. 管理端问答、RAG、工具查询、写操作确认、拒绝、过期、取消均通过验收。
3. 浏览器 SSE 中断后能够续传，重复事件不会重复渲染或重复触发确认卡片。
4. SSE 意外结束后以前端查询到的 Java/MySQL 任务终态作为最终结论。
5. 工具专项评测达到本计划规定的发布门槛。
6. 权限拒绝使用 HTTP 403，冲突使用 HTTP 409，服务不可用使用 HTTP 503。
7. 页面不再以静态文案宣称 Agent 在线，能够显示正常、降级和不可用。
8. Java、Python、管理端单元测试、RAG 评测、工具评测和 Compose 冒烟测试全部通过。
9. 部署配置、密钥、数据库迁移、备份、恢复和回滚检查完成。
10. 最新能力说明与旧报告不存在相互冲突的完成状态。

## 4. 技术选型声明

### 4.1 保持现有选型

- 管理端：Vue 2.7、TypeScript、Fetch Streaming API、Element UI。
- Java：Spring Boot、MyBatis、MySQL、`SseEmitter`。
- Agent：FastAPI、Pydantic、AsyncOpenAI 兼容协议。
- RAG：BGE-M3、Qdrant。
- 部署：Docker Compose。
- 测试：Jest、JUnit/Maven、Pytest/Unittest、现有 Eval 框架。

不新增消息队列、SSE 第三方客户端、前端状态框架或另一套评测系统。SSE 解析和恢复逻辑从页面中提取为小型 TypeScript 模块，以便独立测试，但第一阶段不宣称其已经可以跨项目复用。

### 4.2 第一阶段设计原则

- Java/MySQL 是任务、权限、确认和业务结果的唯一权威源。
- Python 只负责模型执行、RAG、工具编排和临时任务状态。
- SSE 是事件传输通道，不是业务终态权威源。
- 浏览器重连只回放已有事件，不重新提交任务。
- 已产生输出后禁止自动重新执行 LLM 请求，避免重复扣费和重复写操作。
- 健康检查不得打印密钥、Prompt、知识库正文或完整个人信息。

## 5. 第一阶段：单机 Docker Compose 稳定上线收口

### 模块一：固化协议、状态与验收基线

#### 目标

在修改代码前固定事件协议、任务终态、错误分类和验证证据格式，避免前后端各自解释状态。

#### 涉及文件

- 修改：`项目文档/Agent新链路完善项目计划.md`
- 修改：`项目文档/Todo.txt`
- 新建：`项目文档/管理端Agent第一版验收记录.md`

#### 固定接口

- SSE 事件唯一键：`taskId + seqNo`。
- 终止事件：`task_end`、`task_error`、`task_cancelled`。
- Java 任务终态：`2=完成`、`3=失败`、`4=取消`。
- HTTP 分类：`403=无权访问`、`409=任务或确认状态冲突`、`503=依赖不可用`。
- 验收记录字段：场景 ID、环境版本、输入、预期、实际、任务 ID、证据路径、结果。

#### 执行步骤

- [x] 对照当前 Java `AgentStreamEvent`、`AgentTaskVO` 和 Python事件模型，记录字段名称及终态映射。
- [x] 建立第一版验收记录模板，禁止只填写“通过”，每项必须包含可定位证据。
- [x] 明确旧 `/query`、`/stream` 只保持兼容，不纳入第一版主链路验收。
- [x] 删除 `Todo.txt` 中已经完成的事项，只保留第一阶段尚未执行的真实事项。

#### 验收标准

- 前端、Java、Python 对事件序号和终态的定义一致。
- 后续所有模块都能映射到一个验收场景和证据字段。

---

### 模块二：管理端 SSE 可靠性完善（P0）

#### 目标

补齐浏览器端事件序号、去重、有限重连、续传和终态核验，使网络中断不会造成重复内容、错误成功状态或后台任务失联。

#### 涉及文件

- 新建：`frontend/project-sky-admin-vue-ts/src/utils/agentSse.ts`
- 修改：`frontend/project-sky-admin-vue-ts/src/api/agent.ts`
- 修改：`frontend/project-sky-admin-vue-ts/src/views/agent/index.vue`
- 新建：`frontend/project-sky-admin-vue-ts/tests/unit/utils/agentSse.spec.ts`
- 新建：`frontend/project-sky-admin-vue-ts/tests/unit/views/AgentPage.spec.ts`

#### 计划接口

```ts
export type AgentTerminalEvent = 'task_end' | 'task_error' | 'task_cancelled'

export interface AgentStreamEnvelope {
  taskId: string
  seqNo: number
  event: string
  data: Record<string, unknown> | string
}

export interface AgentStreamCallbacks {
  onEvent(event: AgentStreamEnvelope): void
  getTaskStatus(taskId: string): Promise<number>
}

export async function consumeAgentEventStream(
  taskId: string,
  token: string,
  signal: AbortSignal,
  callbacks: AgentStreamCallbacks
): Promise<{ lastSeqNo: number; terminal: boolean }>
```

#### 明确行为

- 从 SSE `id:` 和 JSON `seqNo` 中解析序号；两者同时存在但不一致时终止连接并记录协议错误。
- 仅处理 `seqNo > lastSeqNo` 的事件，确保 token 和确认卡片不重复。
- 非用户主动取消且未收到终止事件时，最多重连 3 次，间隔依次为 1 秒、2 秒、4 秒。
- 重连请求携带 `Last-Event-ID: <lastSeqNo>`，禁止重新调用任务提交接口。
- 收到终止事件后立即停止重连。
- SSE 正常关闭但未收到终止事件时，调用 `GET /agent/tasks/{taskId}` 核验 Java/MySQL 状态。
- 查询结果仍为排队或执行中时，以 2 秒间隔最多查询 5 次；仍未终止则显示“任务仍在后台执行，可重新进入会话查看”，不能显示成功或失败。
- 用户主动取消使用现有取消接口；前端 Abort 只负责关闭本地连接，不作为任务取消成功证据。

#### 测试顺序

- [x] 先编写失败测试：连续事件按序拼接，并保存最大 `seqNo`。
- [x] 先编写失败测试：重复 `seqNo` 不得重复追加 token 或创建确认卡片。
- [x] 先编写失败测试：连接中断后携带正确 `Last-Event-ID` 重连。
- [x] 先编写失败测试：三次重连耗尽后查询任务终态。
- [x] 先编写失败测试：主动取消不触发自动重连。
- [x] 实现独立 SSE 消费模块，再将页面现有解析逻辑迁移到该模块。
- [x] 运行 `yarn test:unit --runInBand`，预期新增测试与现有测试全部通过。
- [x] 运行 `yarn build`，预期生产构建成功，仅允许已登记的历史警告。

#### 验收场景

1. 正常收到 `task_start → token → task_end`，回答只出现一次。
2. 收到部分 token 后断网，恢复网络后从最后序号续传。
3. 历史回放与实时事件交叉到达时仍按序展示。
4. 确认卡片事件被回放时只显示一张卡片。
5. SSE 关闭但任务已经完成时，通过任务详情恢复最终状态。
6. 后台仍执行时页面明确提示，不误报失败。

---

### 模块三：真实健康状态与降级展示（P1）

#### 目标

用真实后端状态替换管理端固定的“Agent 服务在线”，让管理员区分正常、降级和不可用。

#### 涉及文件

- 修改：`agent-service/app/main.py`
- 修改：`agent-service/app/models/schemas.py`
- 修改：`sky-common/src/main/java/com/sky/agent/AgentClient.java`
- 新建：`sky-pojo/src/main/java/com/sky/vo/AgentHealthVO.java`
- 修改：`sky-server/src/main/java/com/sky/controller/admin/AgentController.java`
- 修改：`frontend/project-sky-admin-vue-ts/src/api/agent.ts`
- 修改：`frontend/project-sky-admin-vue-ts/src/views/agent/index.vue`
- 修改：`agent-service/tests/test_config_security.py`
- 新建：`sky-server/src/test/java/com/sky/agent/AgentHealthTest.java`
- 修改：`frontend/project-sky-admin-vue-ts/tests/unit/views/AgentPage.spec.ts`

#### 状态模型

```text
online     Agent、Qdrant、Embedding 均就绪，LLM 配置有效且最近调用无持续故障
degraded   Agent 可访问，但一个非当前操作必需依赖异常，或 LLM 尚无调用记录
offline    Agent 不可访问，或当前核心依赖检查返回不可用
```

响应只暴露服务名、状态、检查时间和脱敏错误类型，不暴露内部 URL、密钥或异常堆栈。LLM 不通过每次页面轮询发送付费 Prompt；使用启动配置校验和现有 LLM 指标中的最近成功/失败信息表达状态，并在真实问答验收中验证实际可用性。

#### 执行步骤

- [x] 为 Python readiness 响应补充 Agent 初始化状态和 LLM 状态摘要，保留 Qdrant、Embedding 状态。
- [x] 将 Java `/admin/agent/health` 从字符串响应改为强类型 `AgentHealthVO`。
- [x] Java 对 Agent 超时或 503 映射为 `offline/degraded`，健康查询本身返回可展示结构。
- [x] 页面进入时查询健康状态，运行期间每 60 秒刷新一次，页面销毁时清理定时器。
- [x] 将状态文案改为“服务正常”“部分能力不可用”“服务不可用”“状态未知”。
- [x] 单个健康查询失败不得清空会话或中断正在执行的任务。

#### 验收标准

- 停止 Qdrant 后页面显示降级，知识库相关操作明确不可用。
- 停止 Agent 后页面显示不可用，发送入口被阻止并提示恢复方式。
- 恢复服务后下一轮健康刷新能够自动恢复状态。
- 健康响应和日志中不存在密钥、Prompt 或知识正文。

---

### 模块四：错误分类与恢复交互（P1）

#### 目标

把统一的“AI 服务请求失败”拆成用户可以采取行动的错误类型，同时保持未知错误不泄露内部实现。

#### 涉及文件

- 新建：`frontend/project-sky-admin-vue-ts/src/utils/agentErrors.ts`
- 修改：`frontend/project-sky-admin-vue-ts/src/utils/request.ts`
- 修改：`frontend/project-sky-admin-vue-ts/src/views/agent/index.vue`
- 新建：`frontend/project-sky-admin-vue-ts/tests/unit/utils/agentErrors.spec.ts`
- 修改：`frontend/project-sky-admin-vue-ts/tests/unit/views/AgentPage.spec.ts`

#### 错误映射

| 条件 | 页面提示 | 恢复动作 |
| --- | --- | --- |
| HTTP 403 | 当前账号无权执行此操作 | 不重试，联系管理员或更换账号 |
| HTTP 409 | 任务或确认状态已经变化 | 刷新任务/确认状态 |
| HTTP 429 或容量错误 | Agent 当前繁忙 | 稍后重试，不自动重提原任务 |
| HTTP 503 | AI 依赖暂不可用 | 查看服务状态，恢复后重试 |
| LLM 首 Token/总超时 | 模型响应超时 | 查询任务终态后允许手动重试 |
| 知识库不可用 | 知识库暂不可用 | 可取消知识库后重新提问 |
| 网络中断 | 连接中断，正在恢复 | 自动执行有限 SSE 重连 |
| 确认过期 | 操作确认已过期 | 重新发起原业务请求 |
| 未知错误 | 请求未完成 | 展示任务 ID，供日志定位 |

#### 执行步骤

- [x] 编写 `normalizeAgentError(error)` 的表驱动失败测试。
- [x] 实现错误分类模块，不以中文字符串作为唯一判断依据；优先使用 HTTP 状态和结构化错误字段。
- [x] 页面保留已经接收的 token，不因后续连接错误覆盖已有内容。
- [x] 为可恢复错误展示明确动作，为不可重试错误禁用重复提交提示。
- [x] 运行前端单元测试和生产构建。

#### 验收标准

- 上述八类错误均能稳定映射到预期提示。
- 页面不会将“任务仍在后台执行”显示为“任务执行失败”。
- 页面不展示 Java/Python 堆栈、内部 URL 或密钥。

---

### 模块五：Agent 权限 HTTP 语义统一（P1）

#### 目标

将 Agent 资源归属和角色权限拒绝统一为 HTTP 403，同时避免通过差异化错误泄露其他管理员资源是否存在。

#### 涉及文件

- 修改：`sky-server/src/main/java/com/sky/service/impl/AgentServiceImpl.java`
- 修改：`sky-server/src/main/java/com/sky/service/agent/AgentKnowledgeService.java`
- 修改：`sky-server/src/main/java/com/sky/handler/GlobalExceptionHandler.java`
- 修改：`sky-server/src/test/java/com/sky/service/agent/AgentServiceKnowledgeTest.java`
- 新建：`sky-server/src/test/java/com/sky/controller/admin/AgentAuthorizationHttpTest.java`

#### 规则

- 已登录管理员访问不属于自己的会话、任务、文档或知识库时抛出 `PermissionDeniedException`。
- 响应使用 HTTP 403，并保留统一 `Result.error(...)` 响应体。
- 对外文案继续合并“不存在或无权访问”，防止资源枚举；测试只断言 403 和安全文案，不区分具体原因。
- 未登录仍由现有认证链路处理为 401。
- 幂等和确认状态竞争继续使用 409，不得改成 403。

#### 测试顺序

- [x] 先增加其他管理员访问任务、会话、知识库和文档的 HTTP 403 测试。
- [x] 运行指定测试并确认旧代码至少有一个场景失败。
- [x] 将对应通用业务异常替换为权限异常，不扩大到非 Agent 业务。
- [x] 运行 Java Agent 测试和 Maven 全量测试。

#### 验收标准

- 所有 Agent 权限拒绝返回 HTTP 403。
- 403 响应不泄露资源是否真实存在。
- 现有 409、参数校验和未知异常语义不受影响。

---

### 模块六：Agent 工具调用专项评测（P0）

#### 目标

在现有 RAG Eval 旁建立工具调用评测，验证模型是否选择正确工具、生成正确参数、遵守权限和确认协议，并在工具失败时保持诚实。

#### 涉及文件

- 新建：`agent-service/evals/tool_baseline.json`
- 新建：`agent-service/evals/tool_dataset.py`
- 新建：`agent-service/evals/tool_scoring.py`
- 新建：`agent-service/evals/tool_run.py`
- 新建：`agent-service/tests/test_tool_eval_scoring.py`
- 修改：`scripts/run-quality-eval.ps1`
- 修改：`项目文档/模块六-RAG与LLM质量基线.md`

所有新增脚本和脚本入口必须使用项目风格注释，说明用途、输入、输出、外部调用和退出码。

#### 第一版数据集

至少 40 条隔离用例，并覆盖以下类别：

| 类别 | 最少数量 | 验证重点 |
| --- | ---: | --- |
| 正确只读工具 | 10 | 工具名和查询参数正确 |
| 无需工具 | 5 | 常识或澄清问题不乱调用工具 |
| 参数缺失 | 5 | 必要信息不足时先追问 |
| 多步骤任务 | 6 | 查询后继续调用下一工具 |
| 写操作确认 | 6 | 必须产生确认，确认前不得执行 |
| 越权请求 | 4 | 工具不可见或 Java 拒绝后不得绕过 |
| 工具失败 | 4 | 不得编造成功结果，给出安全错误 |

用例只使用隔离夹具和测试账号，不读取或写入生产数据。每条用例记录：允许角色、用户问题、期望工具序列、参数断言、是否需要确认、禁止工具、禁止输出和预期终态。

#### 第一版发布门槛

- 工具选择正确率不低于 95%。
- 必填参数正确率不低于 95%。
- 写操作确认覆盖率为 100%。
- 确认前写操作执行次数为 0。
- 越权工具执行次数为 0。
- 工具失败后虚构成功次数为 0。
- 多步骤任务完整率不低于 90%。
- 真实模型抽样回归至少 12 条且全部通过安全门槛。

#### 执行步骤

- [ ] 先为数据集校验、指标计算和失败退出码编写测试。
- [ ] 实现数据加载与评分，不复制现有 RAG 评分代码中的通用逻辑。
- [ ] 使用 Mock LLM/Mock Java 工具客户端运行全部隔离用例，确保结果稳定可重复。
- [ ] 在合法密钥环境执行真实模型分层抽样，记录模型、时间、Token 和用例结果。
- [ ] 将工具评测接入 `run-quality-eval.ps1`，任一安全门槛失败时返回非零退出码。

#### 验收标准

- 全量 Mock 评测可重复运行并产生 JSON 报告。
- 真实模型抽样不存在越权、绕过确认或虚构成功。
- 评测失败能够明确定位到用例、工具、参数和失败规则。

---

### 模块七：完整 Compose 端到端验收（P0）

#### 目标

在与上线一致的单机 Docker Compose 环境中验证最新代码、真实模型、MySQL、Redis、Qdrant、Embedding 和管理端主链路。

#### 涉及文件

- 新建：`scripts/run-agent-v1-acceptance.ps1`
- 新建：`项目文档/管理端Agent第一版验收记录.md`
- 修改：`项目文档/模块七-部署运维与故障处理.md`
- 修改：`.env.example`

验收脚本必须包含中文或英文注释，明确其只创建带固定测试前缀的数据，并只清理本次生成的精确 ID；禁止清空数据库、Qdrant 集合或 Docker 卷。

#### 环境准备

```powershell
docker compose config
docker compose up -d --build
docker compose ps
```

必需服务必须最终进入健康状态。上线配置必须提供真实且互相一致的 `AGENT_INTERNAL_SERVICE_TOKEN`，并应用全部 Flyway 迁移。

#### 必测场景

1. 普通问答与多轮上下文。
2. 知识库上传、索引、检索、引用打开和无证据拒答。
3. 只读工具查询。
4. 查询后继续发起写工具的多步骤任务。
5. 写操作确认并成功执行。
6. 写操作拒绝、超时、重复确认和对象版本变化。
7. 管理员角色在确认前被降级。
8. 任务执行中取消。
9. 浏览器 SSE 断线、续传和最终状态恢复。
10. Agent、Qdrant、Embedding 分别短暂不可用时的健康状态与错误提示。
11. 其他管理员访问任务、会话、知识库和确认记录时返回 403。
12. 同一 `taskId` 同请求重放和不同请求冲突。

#### 证据要求

- 每个场景记录任务 ID、会话 ID、管理员角色、事件序列和最终数据库状态。
- 写操作记录确认 ID、审计记录和业务对象前后状态。
- 故障场景记录健康接口、页面提示及恢复后的结果。
- 日志证据只记录 trace ID、任务 ID、模型、耗时和脱敏错误摘要。
- 清理动作只针对验收脚本创建的精确测试标识。

#### 验收标准

- 12 类场景全部通过；安全类场景不允许豁免。
- 服务恢复后不需要修改源代码即可恢复问答。
- 不存在重复助手消息、重复工具执行或重复确认消费。

---

### 模块八：文档与发布信息收口（P1）

#### 目标

消除旧报告与当前代码的状态冲突，形成唯一、准确的第一版能力边界。

#### 涉及文件

- 修改：`项目文档/AI助手RAG与知识库建设实施报告.md`
- 修改：`项目文档/第二阶段 AI 工具调用实施报告.md`
- 修改：`项目文档/Agent新链路完善项目计划.md`
- 修改：`项目文档/模块六-RAG与LLM质量基线.md`
- 修改：`项目文档/模块七-部署运维与故障处理.md`
- 修改：`项目文档/Todo.txt`
- 新建：`项目文档/管理端Agent第一版最终实施报告.md`

#### 执行步骤

- [ ] 在旧 RAG 报告顶部增加“历史快照”说明，逐项标明已被后续版本补齐的缺口。
- [ ] 将最新工具评测、Compose 验收和 SSE 恢复结果写入对应报告。
- [ ] 记录第一版明确限制：单机、单 Agent worker、非高可用。
- [ ] 记录上线所需环境变量、迁移版本、健康检查和回滚条件。
- [ ] 最终报告列出完成项、测试数据、残余风险和第二阶段入口条件。

#### 验收标准

- 搜索“未完成”“尚未接通”“待闭环”时，不再把已经完成的当前能力描述为缺口。
- 任一新维护者只阅读最终实施报告和运维文档即可完成部署及冒烟验证。

---

### 模块九：发布前全量回归与上线判定

#### 目标

用统一命令验证第一阶段所有改动，并只在证据完整时给出上线结论。

#### 回归命令

```powershell
# 管理端
Set-Location frontend/project-sky-admin-vue-ts
corepack yarn test:unit --runInBand
corepack yarn build

# Java
Set-Location ../..
mvn test
mvn package -DskipTests

# Python
Set-Location agent-service
python -m pytest -q
python -m compileall app evals

# Compose 与质量门禁
Set-Location ..
docker compose config
powershell -ExecutionPolicy Bypass -File scripts/run-quality-eval.ps1
powershell -ExecutionPolicy Bypass -File scripts/run-agent-v1-acceptance.ps1
```

#### 上线判定

- 任一安全门槛、权限测试、确认协议或数据一致性测试失败：禁止上线。
- 非安全类质量指标未达门槛：记录失败用例并修复后重新执行，不以人工口头判断豁免。
- 仅历史 Sass 废弃和已登记包体积警告可以保留，必须在最终报告列明。
- 验收完成后执行一次备份与隔离恢复演练，并确认回滚镜像标签可用。

## 6. 第一阶段执行顺序与依赖

```text
模块一：协议与验收基线
  ├─> 模块二：SSE 可靠性
  ├─> 模块三：健康状态
  ├─> 模块四：错误恢复
  └─> 模块五：权限语义

模块二～五完成
  ├─> 模块六：工具专项评测
  └─> 模块七：Compose 端到端验收

模块六、七通过
  └─> 模块八：文档收口
        └─> 模块九：全量回归与上线判定
```

模块二至模块五可以在模块一完成后分别实施，但共享文件 `AgentPage`、`AgentController` 和前端错误处理存在交叉修改，执行时应按二、三、四、五的顺序合并，避免覆盖代码。

## 7. 第一阶段产出物

- 可断线恢复的管理端 SSE 客户端。
- 强类型 Agent 健康状态接口和页面状态展示。
- Agent 错误分类和恢复交互。
- 完整一致的 Agent HTTP 权限语义。
- 不少于 40 条的工具调用专项评测集及报告。
- 单机 Compose 端到端验收脚本和验收记录。
- 更新后的 RAG、工具调用、质量基线和运维文档。
- 《管理端Agent第一版最终实施报告》。

## 8. 第二阶段：标准化、自动化与可复用性验收

第二阶段不阻断第一版在单机 Docker Compose 环境上线。只有第一阶段全部验收通过后才能启动第二阶段。

### 8.1 关键改进建议一：终态校验标准化

将第一阶段实现的 SSE 恢复逻辑整理为稳定接口和统一状态机，验证它不仅适用于当前管理端页面，也能被其他 Agent 页面或客户端复用。

重点工作：

- 将连接、重连、去重、终态查询和取消语义形成独立契约。
- 建立乱序、重复、丢包、长时间断网和页面重载的协议测试。
- 验证适配不同基础路径、认证头和 UI 容器时无需复制核心状态机。
- 明确可复用模块的版本、输入输出、兼容性和失败策略。

### 8.2 关键改进建议二：质量评测升级为自动发布门禁

将 RAG 与工具评测从人工执行脚本提升为统一、可追踪的发布门禁。

重点工作：

- 固定评测数据集版本、模型版本和阈值变更记录。
- 在持续集成中执行单元、契约、Mock 全量评测和受控真实模型抽样。
- 保存机器可读报告和历史趋势，禁止只保留控制台截图。
- 验证评测框架可扩展到用户端 Agent 或其他业务工具，而不依赖管理端专用数据结构。

### 8.3 关键改进建议三：Compose 验收证据自动化

将第一阶段人工参与的 Compose 验收整理为可重复、可审计、可在隔离环境执行的自动验收套件。

重点工作：

- 自动创建和清理带唯一前缀的隔离测试数据。
- 自动收集任务事件、最终状态、审计记录、健康状态和日志摘要。
- 自动输出带环境指纹、镜像版本和时间戳的验收报告。
- 验证该流程适配端口变化、外部基础设施模式及新的 Agent 使用方。

### 8.4 第二阶段验收标准

- SSE 核心逻辑在至少两个独立消费场景中复用，且不复制状态机实现。
- RAG 与工具门禁能够通过单一入口执行，并生成统一报告。
- Compose 验收能够在全新隔离环境重复执行，结果不依赖人工修改数据库。
- 文档能够明确说明哪些组件可复用、如何接入、兼容边界和禁止用法。

## 9. 风险与控制措施

| 风险 | 控制措施 |
| --- | --- |
| SSE 自动重连导致任务重复提交 | 重连只调用事件接口，永不重新调用 submit |
| 重放事件造成重复 token 或重复确认 | 使用 `taskId + seqNo` 去重 |
| 健康检查调用 LLM 产生费用 | 不发送付费 Prompt，以配置校验、调用指标和真实验收共同判断 |
| 权限错误暴露资源存在性 | 对外统一“不存在或无权访问”并返回 403 |
| 工具评测误写真实数据 | 使用隔离夹具、固定前缀、确认凭证和精确清理 |
| 故障测试破坏现有环境 | 只停止明确服务并在操作前记录状态，不删除卷 |
| 文档与代码再次漂移 | 最终实施报告引用测试命令、迁移版本和代码接口 |
| 单进程形成容量上限 | 第一版明确接受限制，不用增加 worker 的方式错误扩容 |

## 10. 最终声明

第一阶段的完成标准是管理端 Agent 能够在单机 Docker Compose 环境稳定上线，并通过本计划规定的 P0、P1、质量门槛和端到端验收。第一阶段不代表该方案已经具备跨项目、跨终端或多实例部署的复用保证。

第一版上线收口后，后续必须进入第二阶段，完善并执行可复用性验收测试，重点验证 Agent 核心链路、SSE 客户端、工具评测框架、错误模型、健康状态和部署验收流程能否在用户端 Agent、其他业务模块以及不同部署环境中复用。该工作不属于第一阶段上线阻断项，但在宣称相关能力“可复用”之前必须完成并形成独立验收报告。
