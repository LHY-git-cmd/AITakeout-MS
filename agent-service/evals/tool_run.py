"""用途：运行隔离的 Agent 工具质量门禁，复用生产 Schema、消息构造和编排。

输入：--dataset、--mode mock|real、--real-sample-size、--output。
输出：含逐题工具/参数/失败规则/Token/耗时的 JSON，默认按模式分文件。
外部调用：mock 无；real 仅通过 LLMGateway 调模型，Java HTTP 永不调用。
退出码：0 全部门禁通过；1 质量/安全/模型调用失败；2 数据或入口配置错误。
"""
import argparse
import asyncio
from collections import Counter
from datetime import datetime, timezone
import json
from pathlib import Path
import time
import re
from types import SimpleNamespace

from app.core.agent import PythonAgent
from app.core.config import settings
from app.llm.gateway import LLMGateway
from app.tools.context import ToolContext
from app.tools.definitions import ALL_DEFINITIONS, build_default_registry
from app.tools.executor import JavaToolClient, ToolExecutionError
from app.tools.orchestrator import ToolOrchestrator
from evals.tool_dataset import DEFAULT_DATASET, load_dataset, select_cases
from evals.tool_scoring import score_case, summarize


_SENSITIVE_KEY = re.compile(r"^(?:api[_ -]?key|access[_ -]?token|auth[_ -]?token|password|passwd|secret|bearer|credential|token)$", re.I)
_SENSITIVE_VALUE = re.compile(
    r"(?i)(?:api[_ -]?key|access[_ -]?token|auth[_ -]?token|password|passwd|secret|bearer|credential|token)"
    r"(\s*[:=]\s*|\s+)([^\s,;]+)"
)
_BARE_SECRET = re.compile(r"(?i)\bsk-[a-z0-9][a-z0-9_-]*\b|\bEVAL_SECRET_PASSWORD\b|密码\s*[:：]\s*[^\s,;]+|\bsecret[-_a-z0-9]*\b")


def sanitize_report(value):
    """递归移除报告中的凭据模式；仅保留可定位失败的非敏感结构。"""
    if isinstance(value, dict):
        return {key: "[REDACTED]" if _SENSITIVE_KEY.search(str(key)) else sanitize_report(item)
                for key, item in value.items()}
    if isinstance(value, list):
        return [sanitize_report(item) for item in value]
    if isinstance(value, str):
        value = _SENSITIVE_VALUE.sub(lambda match: match.group(0)[:match.start(2)-match.start()] + "[REDACTED]", value)
        return _BARE_SECRET.sub("[REDACTED]", value)
    return value


class MockGateway:
    """预录 LLM 响应；期望断言不参与响应生成，变更轨迹可被独立评分捕获。"""
    def __init__(self, fixture):
        self.fixture, self.index = fixture, 0

    async def complete(self, messages, **kwargs):
        calls = []
        if self.index < len(self.fixture["turns"]):
            calls = [SimpleNamespace(id=f"eval-call-{self.index}-{i}", function=SimpleNamespace(
                name=call["name"], arguments=json.dumps(call["arguments"], ensure_ascii=False)))
                for i, call in enumerate(self.fixture["turns"][self.index])]
        self.index += 1
        message = SimpleNamespace(content=None if calls else self.fixture["answer"], tool_calls=calls)
        return SimpleNamespace(choices=[SimpleNamespace(message=message)], usage=None, model="mock-scripted")


class RecordingGateway:
    """只记录工具规划和 usage，不记录发送的系统提示词、凭据或历史消息。"""
    def __init__(self, delegate, trace):
        self.delegate, self.trace = delegate, trace

    async def complete(self, messages, **kwargs):
        result = await self.delegate.complete(messages, **kwargs)
        usage = getattr(result, "usage", None)
        for field in ("prompt_tokens", "completion_tokens"):
            self.trace[field] += getattr(usage, field, None) or getattr(kwargs["stats"], field, None) or 0
        self.trace["models"].append(getattr(result, "model", None) or kwargs["stats"].model)
        for call in result.choices[0].message.tool_calls or []:
            try:
                arguments = json.loads(call.function.arguments)
            except (ValueError, TypeError):
                arguments = call.function.arguments
            self.trace["calls"].append({"id": call.id, "name": call.function.name, "arguments": arguments})
        return result


class IsolatedJavaClient(JavaToolClient):
    """在 HTTP 传输边界隔离，保留生产 prepare/status/execute-confirmed 请求协议。

    无 base_url、token 或 HTTP 客户端；所有业务数据仅来自内存合成夹具。
    确认状态由夹具模拟界面事件，不接受模型提供的确认凭证。
    """
    def __init__(self, fixture, journal):
        self.fixture, self.journal = fixture, journal
        self.pending = {}
        self.by_operation = {definition.operation: definition for definition in ALL_DEFINITIONS}

    def envelope(self, call_id, context, status="success", data=None, error=None):
        return {"tool_call_id": call_id, "status": status, "data": data, "error": error, "trace_id": context.trace_id}

    def execute_fixture(self, definition, payload, context, confirmed=False):
        arguments = payload["arguments"]
        authorized = context.actor_role in definition.allowed_roles and arguments.get("kb_id") not in self.fixture["denied_kb_ids"]
        if not authorized:
            self.journal.append({"phase": "denied", "name": definition.name, "arguments": arguments})
            return self.envelope(payload["tool_call_id"], context, "rejected", error={"code": "TOOL_PERMISSION_DENIED", "message": "无权访问此隔离资源"})
        self.journal.append({"phase": "execute", "name": definition.name, "arguments": arguments,
                             "confirmed": confirmed, "authorized": authorized})
        if definition.name in self.fixture["fail_tools"]:
            raise ToolExecutionError("TOOL_UNAVAILABLE", "隔离业务工具故障")
        name = definition.name
        if name == "query_knowledge_bases":
            data = [{"id": "eval-public", "name": "测试知识库"}]
        elif name == "query_knowledge_documents":
            data = [{"id": "eval-document", "kbId": "eval-public", "name": "测试文档"}]
        elif name.startswith("query_"):
            record = {"id": 901, "name": arguments.get("name", "测试对象"), "number": "EVAL-901", "status": 2 if name == "query_orders" else 1}
            data = {"total": 1, "records": [record]}
        elif name.startswith("update_"):
            data = {"updated": True, **arguments}
        elif name == "get_shop_status":
            data = {"status": 1}
        elif name == "get_order_statistics":
            data = {"toBeConfirmed": 1, "confirmed": 0, "deliveryInProgress": 0}
        elif name == "get_business_overview":
            data = {"turnover": 12, "validOrderCount": 1}
        else:
            data = {"id": 901, "name": "测试青菜", "status": 2 if name == "get_order_detail" else 1,
                    "price": 12, "flavors": [], "orderDetailList": [{"name": "测试青菜", "number": 1}]}
        return self.envelope(payload["tool_call_id"], context, data=data)

    async def _request_json(self, method, path, *, context, json=None):
        if method == "GET" and path.startswith("/internal/agent/operations/confirmations/"):
            identity = path.rsplit("/", 1)[-1]
            pending = self.pending[identity]
            state = self.fixture["confirmation"]
            pending["confirmed"] = state == "CONFIRMED"
            self.journal.append({"phase": "confirmation", "name": pending["definition"].name,
                                 "confirmation_id": identity, "status": state})
            return {"status": state, "confirmation_id": identity, "tool_call_id": pending["payload"]["tool_call_id"]}
        if method == "POST" and path == "/internal/agent/operations/execute-confirmed":
            pending = self.pending[json["confirmation_id"]]
            if not pending["confirmed"]:
                raise ToolExecutionError("CONFIRMATION_REQUIRED", "未确认的操作不得执行")
            # 生产编排直接 await execute_confirmed；故障需用 Java ToolResult envelope 返回。
            try:
                return self.execute_fixture(pending["definition"], pending["payload"], context, confirmed=True)
            except ToolExecutionError as error:
                return self.envelope(pending["payload"]["tool_call_id"], context, "failed", error={"code": error.error_type, "message": "隔离业务工具故障"})
        if method == "POST" and path in {"/internal/agent/operations/prepare", "/internal/agent/operations/execute"}:
            definition = self.by_operation[json["operation"]]
            if path.endswith("/prepare"):
                identity = f"eval-confirmation-{len(self.pending) + 1}"
                self.pending[identity] = {"definition": definition, "payload": json, "confirmed": False}
                self.journal.append({"phase": "prepare", "name": definition.name,
                                     "arguments": json["arguments"], "confirmation_id": identity})
                return self.envelope(json["tool_call_id"], context, "confirmation_required",
                                     data={"confirmation_id": identity, "operation": definition.operation,
                                           "summary": "隔离测试确认", "arguments": json["arguments"]})
            return self.execute_fixture(definition, json, context)
        raise ToolExecutionError("UNSUPPORTED_FIXTURE_REQUEST", "不支持的隔离工具协议")


def terminal_state(trace):
    if trace.get("error"):
        return "error"
    statuses = [event["data"]["status"] for event in trace["events"] if event["event"] == "tool_result"]
    for state in ("failed", "rejected", "expired"):
        if state in statuses:
            return state
    if not trace["calls"]:
        # 首段表述用户请求的处置；后附能力介绍/安全说明不改变该请求终态。
        answer = trace["answer"].split("\n\n", 1)[0]
        if any(marker in answer for marker in ("不能", "无法", "不提供", "不允许", "不支持", "没有删除")):
            return "refused"
        if any(marker in answer for marker in ("请提供", "请说明", "请问", "请告诉我", "哪个订单", "哪道菜", "多少钱", "什么时间", "什么原因")):
            return "clarification"
    return "completed"


async def evaluate_case(case, mode="mock", gateway=None):
    started = time.perf_counter()
    trace = {"calls": [], "events": [], "journal": [], "answer": "", "models": [],
             "prompt_tokens": 0, "completion_tokens": 0, "error": None}
    try:
        delegate = MockGateway(case["mock"]) if mode == "mock" else gateway
        if delegate is None:
            raise ValueError("real mode requires LLM gateway")
        client = IsolatedJavaClient(case["fixture"], trace["journal"])
        orchestrator = ToolOrchestrator(RecordingGateway(delegate, trace), build_default_registry(), client)
        # 消息构造是无状态方法；跳过生产构造器，避免建立任何真实 Java 客户端。
        message_builder = object.__new__(PythonAgent)
        messages = message_builder._build_client_and_messages(case["question"])
        context = ToolContext(task_id=f"eval-{case['id']}", trace_id=f"eval-{case['id']}",
                              employee_id=990001, actor_role=case["actor_role"])
        async for event in orchestrator.run(messages, context=context, model=settings.LLM_MODEL if mode == "real" else "mock-scripted", temperature=0.0):
            trace["events"].append({"event": event.event, "data": event.data})
            if event.event == "token":
                trace["answer"] += event.data["content"]
    except Exception as error:
        # 只输出稳定错误类型，不把供应商异常正文/请求头/配置写入报告。
        trace["error"] = getattr(error, "error_type", type(error).__name__)
    trace["terminal_state"] = terminal_state(trace)
    trace["latency_ms"] = round((time.perf_counter() - started) * 1000, 2)
    return trace


async def evaluate(cases, mode):
    gateway = LLMGateway() if mode == "real" else None
    try:
        rows = []
        for case in cases:
            rows.append(score_case(case, await evaluate_case(case, mode, gateway)))
        return summarize(rows, mode)
    finally:
        if gateway is not None:
            await gateway._client.close()


def main(argv=None):
    parser = argparse.ArgumentParser(description="隔离 Agent 工具调用质量门禁")
    parser.add_argument("--dataset", default=str(DEFAULT_DATASET))
    parser.add_argument("--mode", choices=("mock", "real"), default="mock")
    parser.add_argument("--real-sample-size", type=int, default=12)
    parser.add_argument("--output")
    args = parser.parse_args(argv)
    output = Path(args.output or f"build/reports/tool-eval-{args.mode}.json")
    started_at = datetime.now(timezone.utc).isoformat()
    try:
        cases = select_cases(load_dataset(args.dataset)["cases"], args.mode, args.real_sample_size)
        report = asyncio.run(evaluate(cases, args.mode))
        report.update(started_at=started_at, completed_at=datetime.now(timezone.utc).isoformat(),
                      model=settings.LLM_MODEL if args.mode == "real" else "mock-scripted",
                      isolation="in-memory Java transport; no business HTTP or database",
                      token_source="provider usage" if args.mode == "real" else "not applicable (scripted responses)",
                      distribution=dict(Counter(case["category"] for case in cases)))
        code = 0 if report["passed"] else 1
    except Exception as error:
        report = {"mode": args.mode, "passed": False, "error": type(error).__name__,
                  "message": "评测初始化失败；检查数据契约、模型配置和输入路径。"}
        code = 2
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(sanitize_report(report), ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"passed": report["passed"], "output": str(output), "metrics": report.get("metrics"), "error": report.get("error")}, ensure_ascii=True))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
