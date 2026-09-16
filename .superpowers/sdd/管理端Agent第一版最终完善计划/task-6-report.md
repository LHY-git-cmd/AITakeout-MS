# 模块六：Agent 工具调用专项评测报告

状态：`DONE_WITH_CONCERNS`

实现已完成，提交前工作树未提交、未推送。真实模型抽样的安全门槛全部通过，但严格工具序列门槛未通过，因此不宣称真实质量门禁通过。

## RED / GREEN

- RED：新增 `agent-service/tests/test_tool_eval_scoring.py` 首次运行时 9 项测试全部因 `tool_dataset`、`tool_scoring`、`tool_run` 尚不存在而失败。
- GREEN：实现数据契约、确定性抽样、轨迹评分、隔离 Java Mock、真实规划模式和入口后，新增测试通过；最终新增测试为 14 项。

## 数据集与实现

- `agent-service/evals/tool_baseline.json`：40 条合成隔离用例。
- 分布：只读 10、无需工具 5、参数缺失 5、多步骤 6、写操作确认 6、越权 4、工具失败 4。
- 每条包含角色、问题、期望工具序列、参数断言、确认要求、禁止工具/输出、预期终态和 Mock 轨迹。
- 复用生产工具注册表、Pydantic 参数 Schema、消息构造和 `ToolOrchestrator`；Java 协议在内存隔离客户端中模拟，绝不连接业务 HTTP 或数据库。
- real 模式只调用 LLM 做工具规划，工具执行仍为隔离 Mock。

## 命令与证据

1. `E:\code\web-sky-project\skyproject\sky-take-out\agent-service\.venv\Scripts\python.exe -m unittest discover -s tests -v`
   - 结果：`Ran 74 tests ... OK (skipped=1)`；跳过项为需外部服务的旧 RAG E2E 测试。
2. `...python.exe -m compileall -q app evals tests`
   - 结果：退出码 `0`。
3. `...python.exe -m evals.tool_run --mode mock --output ../build/reports/tool-eval-mock-final.json`
   - 结果：退出码 `0`；40/40，通过率 100%；工具选择、参数、确认覆盖、多步骤均为 100%，确认前写入/越权执行/虚构成功均为 0。
4. `...python.exe -m evals.tool_run --mode real --real-sample-size 12 --output ../build/reports/tool-eval-real-validated.json`
   - 结果：模型实际调用 12 条，退出码 `1`。真实安全门槛通过：确认覆盖 100%，确认前写入 0，越权执行 0，失败后虚构成功 0，禁止输出 0；严格工具选择/参数/回答门槛未通过，原因包括额外只读调用及越权问题先查询知识库，未伪造为通过。
5. `pwsh -NoProfile -File scripts/run-quality-eval.ps1 -Mode mock -Python ... -Output ... -ToolOutput ...`
   - 结果：RAG 依赖 Qdrant 返回 HTTP 502，入口仍继续完成 Tool Mock 40/40，整体退出码 `1`；验证任一子评测失败会传播非零，同时不跳过另一项。
6. `git diff --check`
   - 结果：退出码 `0`。

## 报告与文件

实现文件：

- `agent-service/evals/tool_baseline.json`
- `agent-service/evals/tool_dataset.py`
- `agent-service/evals/tool_scoring.py`
- `agent-service/evals/tool_run.py`
- `agent-service/tests/test_tool_eval_scoring.py`
- `scripts/run-quality-eval.ps1`
- `项目文档/模块六-RAG与LLM质量基线.md`

执行产物在本地 `build/reports/`，未纳入提交。真实报告未包含 API Key、系统提示词或生产数据。

## 自审与关注点

- 真实抽样严格工具序列未通过是模型行为问题，已保留逐题失败规则和工具轨迹，未放宽门槛。
- 当前生产注册表对 ADMIN 与 SUPER_ADMIN 暴露相同工具集合；越权评测覆盖未注册工具、敏感信息请求和隔离知识库拒绝，不能替代 Java 真实鉴权集成测试。
- 文本终态/虚构成功使用确定性规则，报告保留原回答供复核。
- 本报告记录的是实际执行结果；提交前状态为未提交、未推送。

## 审查修订（2026-09-15）

- 评分器现对“工具失败但操作已落地/结果已生效”等后续成功断言 fail-closed；新增回归测试覆盖该表达及失败、成功混合句。
- 报告写出前递归脱敏 `answer`、`calls.arguments`、事件与轨迹中的 API key、token、password、secret、bearer、credential 等键和值；新增测试确认原始值不会进入 JSON。
- 修订后真实 12 条报告仍保留 `DONE_WITH_CONCERNS`：工具选择正确率 83.33%，必填参数正确率 83.33%，回答/终态正确率 91.67%；确认覆盖 100%，确认前写入、越权执行、失败后虚构成功及禁止输出均为 0。严格门槛未放宽。
- 本轮进一步收紧成功断言为结构化成功/否定模式：失败后的“已办妥/已生效/更新完成/已落地”等均 fail-closed；“操作未成功/结果未生效/状态没有更新”等诚实否定不误报。报告字符串递归脱敏裸 `sk-*`、`EVAL_SECRET_PASSWORD`、中文“密码：…”及中性字段中的 secret，同时保留 `prompt_tokens`、`completion_tokens` 与 `token_source`。
- 修订后验证：`python -m unittest discover -s tests -v` 为 78 项通过、1 项跳过；`python -m compileall -q app evals tests` 与 `git diff --check` 均退出码 0。
