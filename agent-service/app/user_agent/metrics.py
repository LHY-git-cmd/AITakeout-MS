"""用户Agent运行指标；仅聚合意图、工具状态和延迟，不保存用户文本。"""
import asyncio
from collections import Counter
from time import perf_counter


class UserAgentMetrics:
    """提供进程内快照和Prometheus格式，便于MVP阶段接入现有监控抓取。"""

    def __init__(self):
        self._lock = asyncio.Lock()
        self._started: dict[str, float] = {}
        self._first_response_seen: set[str] = set()
        self._intents = Counter()
        self._tools = Counter()
        self._tasks = Counter()
        self._first_response_ms: list[float] = []

    async def task_started(self, task_id: str) -> None:
        async with self._lock:
            self._started[task_id] = perf_counter()
            self._tasks["started"] += 1

    async def event(self, task_id: str, event_name: str, data: dict) -> None:
        async with self._lock:
            if event_name == "workflow_routed" and data.get("intent"):
                self._intents[str(data["intent"])] += 1
            if event_name == "tool_completed":
                tool = str(data.get("tool_name") or "unknown")
                self._tools[(tool, str(data.get("status") or "unknown"))] += 1
            if event_name in {"message_delta", "recommendation_cards", "business_state_changed"} \
                    and task_id not in self._first_response_seen:
                started = self._started.get(task_id)
                if started is not None:
                    self._first_response_ms.append((perf_counter() - started) * 1000)
                self._first_response_seen.add(task_id)

    async def task_finished(self, task_id: str, status: str) -> None:
        async with self._lock:
            self._tasks[status] += 1
            self._started.pop(task_id, None)
            self._first_response_seen.discard(task_id)

    async def snapshot(self) -> dict:
        async with self._lock:
            return {
                "tasks": dict(self._tasks),
                "intents": dict(self._intents),
                "tools": {f"{tool}:{status}": count for (tool, status), count in self._tools.items()},
                "first_response_ms": self._summary(self._first_response_ms),
            }

    async def prometheus(self) -> str:
        snapshot = await self.snapshot()
        lines = ["# TYPE sky_user_agent_tasks_total counter"]
        for status, count in snapshot["tasks"].items():
            lines.append(f'sky_user_agent_tasks_total{{status="{status}"}} {count}')
        lines.append("# TYPE sky_user_agent_intents_total counter")
        for intent, count in snapshot["intents"].items():
            lines.append(f'sky_user_agent_intents_total{{intent="{intent}"}} {count}')
        lines.append("# TYPE sky_user_agent_tools_total counter")
        for key, count in snapshot["tools"].items():
            tool, status = key.split(":", 1)
            lines.append(f'sky_user_agent_tools_total{{tool="{tool}",status="{status}"}} {count}')
        lines.extend([
            "# TYPE sky_user_agent_first_response_ms gauge",
            f'sky_user_agent_first_response_ms{{quantile="0.50"}} {snapshot["first_response_ms"]["p50"] or 0}',
            f'sky_user_agent_first_response_ms{{quantile="0.95"}} {snapshot["first_response_ms"]["p95"] or 0}',
        ])
        return "\n".join(lines) + "\n"

    @staticmethod
    def _summary(values: list[float]) -> dict:
        if not values:
            return {"count": 0, "p50": None, "p95": None}
        ordered = sorted(values)
        pick = lambda ratio: round(ordered[min(len(ordered) - 1, int((len(ordered) - 1) * ratio))], 2)
        return {"count": len(ordered), "p50": pick(.5), "p95": pick(.95)}


user_agent_metrics = UserAgentMetrics()
