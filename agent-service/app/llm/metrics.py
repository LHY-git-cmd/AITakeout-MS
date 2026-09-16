"""轻量级进程内 LLM 指标；后续可由 Prometheus 适配器读取。"""
import asyncio
from collections import Counter
from datetime import date, datetime, timezone

from app.core.config import settings
class LLMMetrics:
    """
    一个用于收集和暴露 LLM 调用指标的线程安全类。

    这个类在内存中聚合指标，如调用次数、延迟、token 数量和成本估算。
    它提供了一个 `snapshot` 方法来获取当前指标的字典表示，
    以及一个 `prometheus` 方法来将指标格式化为 Prometheus 的文本格式。
    """
    def __init__(self):
        """
        初始化 LLMMetrics 实例。

        - `_lock`: 一个 asyncio.Lock，用于确保对指标进行并发访问时的线程安全。
        - `_calls`: 一个 Counter，用于按 (模型, 状态, 错误类型) 统计 LLM 调用次数。
        - `_latencies_ms`: 记录所有调用延迟（毫秒）的列表。
        - `_first_token_ms`: 记录所有流式调用收到第一个 token 的延迟（毫秒）的列表。
        - `_prompt_tokens`: 累计的 prompt token 总数。
        - `_completion_tokens`: 累计的 completion token 总数。
        - `_daily`: 一个 Counter，用于按 (日期, 模型, 状态) 统计每日调用次数。
        - `_estimated_cost`: 基于 token 数量和配置中的成本费率估算的累计成本。
        - `_rag_searches`: RAG（检索增强生成）的总搜索次数。
        - `_rag_with_results`: RAG 搜索中返回了结果的次数。
        - `_rag_refusals`: RAG 流程因证据不足而拒答的次数。
        - `_rag_errors`: RAG 搜索过程中发生错误的次数。
        - `_rag_scores`: RAG 检索结果的相关性分数列表。
        """
        self._lock = asyncio.Lock()
        self._calls = Counter()
        self._latencies_ms: list[float] = []
        self._first_token_ms: list[float] = []
        self._prompt_tokens = 0
        self._completion_tokens = 0
        self._daily = Counter()
        self._estimated_cost = 0.0
        self._rag_searches = 0
        self._rag_with_results = 0
        self._rag_refusals = 0
        self._rag_errors = 0
        self._rag_scores: list[float] = []
        self._last_status: str | None = None
        self._last_success_at: datetime | None = None
        self._last_failure_at: datetime | None = None
        self._consecutive_failures = 0

    async def record(self, *, model: str, status: str, error_type: str | None,
                     elapsed_ms: float, first_token_ms: float | None,
                     prompt_tokens: int | None, completion_tokens: int | None):
        """
        记录一次完整的 LLM 调用的指标。

        :param model: 所使用的 LLM 模型名称。
        :param status: 调用的最终状态 (例如, 'completed', 'failed', 'cancelled')。
        :param error_type: 如果调用失败，记录错误的类型。
        :param elapsed_ms: 整个调用所花费的总时间（毫秒）。
        :param first_token_ms: 对于流式调用，接收到第一个 token 所需的时间（毫秒）。
        :param prompt_tokens: 输入给模型的 prompt token 数量。
        :param completion_tokens: 模型生成的 completion token 数量。
        """
        """
        记录一次完整的 LLM 调用的指标。

        :param model: 所使用的 LLM 模型名称。
        :param status: 调用的最终状态 (例如, 'completed', 'failed', 'cancelled')。
        :param error_type: 如果调用失败，记录错误的类型。
        :param elapsed_ms: 整个调用所花费的总时间（毫秒）。
        :param first_token_ms: 对于流式调用，接收到第一个 token 所需的时间（毫秒）。
        :param prompt_tokens: 输入给模型的 prompt token 数量。
        :param completion_tokens: 模型生成的 completion token 数量。
        """
        async with self._lock:
            self._calls[(model, status, error_type or "NONE")] += 1
            self._latencies_ms.append(elapsed_ms)
            if first_token_ms is not None:
                self._first_token_ms.append(first_token_ms)
            self._prompt_tokens += prompt_tokens or 0
            self._completion_tokens += completion_tokens or 0
            day = date.today().isoformat()
            self._daily[(day, model, status)] += 1
            recorded_at = datetime.now(timezone.utc)
            self._last_status = status
            if status == "completed":
                self._last_success_at = recorded_at
                self._consecutive_failures = 0
            elif status == "failed":
                self._last_failure_at = recorded_at
                self._consecutive_failures += 1
            self._estimated_cost += (
                (prompt_tokens or 0) * settings.LLM_INPUT_COST_PER_MILLION
                + (completion_tokens or 0) * settings.LLM_OUTPUT_COST_PER_MILLION
            ) / 1_000_000

    async def record_rag(self, results: list[dict], *, refusal=False, error=False):
        """
        记录一次 RAG 检索操作的指标。

        :param results: 从知识库检索到的结果列表。
        :param refusal: 是否因为证据不足而拒答。
        :param error: 检索过程中是否发生错误。
        """
        """
        记录一次 RAG 检索操作的指标。

        :param results: 从知识库检索到的结果列表。
        :param refusal: 是否因为证据不足而拒答。
        :param error: 检索过程中是否发生错误。
        """
        async with self._lock:
            self._rag_searches += 1
            if results:
                self._rag_with_results += 1
                self._rag_scores.extend(
                    float(item["score"]) for item in results if item.get("score") is not None)
            if refusal:
                self._rag_refusals += 1
            if error:
                self._rag_errors += 1

    async def snapshot(self) -> dict:
        """
        以字典形式返回当前所有指标的快照。

        :return: 一个包含所有当前指标的字典。
        """
        """
        以字典形式返回当前所有指标的快照。

        :return: 一个包含所有当前指标的字典。
        """
        async with self._lock:
            return {
                "calls": {f"{model}:{status}:{error}": count
                          for (model, status, error), count in self._calls.items()},
                "latency_ms": self._summary(self._latencies_ms),
                "first_token_ms": self._summary(self._first_token_ms),
                "prompt_tokens": self._prompt_tokens,
                "completion_tokens": self._completion_tokens,
                "estimated_cost": round(self._estimated_cost, 6),
                "daily_calls": {
                    f"{day}:{model}:{status}": count
                    for (day, model, status), count in self._daily.items()
                },
                "recent": {
                    "last_status": self._last_status,
                    "last_success_at": self._last_success_at.isoformat()
                    if self._last_success_at else None,
                    "last_failure_at": self._last_failure_at.isoformat()
                    if self._last_failure_at else None,
                    "consecutive_failures": self._consecutive_failures,
                },
                "rag": {
                    "searches": self._rag_searches,
                    "with_results": self._rag_with_results,
                    "refusals": self._rag_refusals,
                    "errors": self._rag_errors,
                    "scores": self._summary(self._rag_scores),
                },
            }

    async def prometheus(self) -> str:
        """
        以 Prometheus 文本格式返回当前所有指标的快照。

        :return: 一个符合 Prometheus 格式的字符串，可供其抓取。
        """
        """
        以 Prometheus 文本格式返回当前所有指标的快照。

        :return: 一个符合 Prometheus 格式的字符串，可供其抓取。
        """
        snapshot = await self.snapshot()
        lines = [
            "# HELP sky_llm_calls_total LLM calls by model and result.",
            "# TYPE sky_llm_calls_total counter",
        ]
        for key, count in snapshot["calls"].items():
            model, status, error = key.split(":", 2)
            lines.append(
                f'sky_llm_calls_total{{model="{model}",status="{status}",error_type="{error}"}} {count}')
        lines.extend([
            "# TYPE sky_llm_prompt_tokens_total counter",
            f'sky_llm_prompt_tokens_total {snapshot["prompt_tokens"]}',
            "# TYPE sky_llm_completion_tokens_total counter",
            f'sky_llm_completion_tokens_total {snapshot["completion_tokens"]}',
            "# TYPE sky_llm_estimated_cost_total counter",
            f'sky_llm_estimated_cost_total {snapshot["estimated_cost"]}',
        ])
        for metric, values in (("sky_llm_latency_ms", snapshot["latency_ms"]),
                               ("sky_llm_first_token_ms", snapshot["first_token_ms"])):
            lines.append(f"# TYPE {metric} gauge")
            if values["p50"] is not None:
                lines.append(f'{metric}{{quantile="0.50"}} {values["p50"]}')
                lines.append(f'{metric}{{quantile="0.95"}} {values["p95"]}')
        rag = snapshot["rag"]
        lines.extend([
            "# TYPE sky_rag_searches_total counter",
            f'sky_rag_searches_total {rag["searches"]}',
            "# TYPE sky_rag_searches_with_results_total counter",
            f'sky_rag_searches_with_results_total {rag["with_results"]}',
            "# TYPE sky_rag_refusals_total counter",
            f'sky_rag_refusals_total {rag["refusals"]}',
            "# TYPE sky_rag_errors_total counter",
            f'sky_rag_errors_total {rag["errors"]}',
        ])
        if rag["scores"]["p50"] is not None:
            lines.extend([
                "# TYPE sky_rag_score gauge",
                f'sky_rag_score{{quantile="0.50"}} {rag["scores"]["p50"]}',
                f'sky_rag_score{{quantile="0.95"}} {rag["scores"]["p95"]}',
            ])
        return "\n".join(lines) + "\n"

    @staticmethod
    def _summary(values: list[float]) -> dict:
        """
        计算一个数值列表的摘要统计信息（计数、p50、p95）。

        :param values: 一个浮点数列表。
        :return: 一个包含 'count', 'p50', 'p95' 键的字典。
        """
        """
        计算一个数值列表的摘要统计信息（计数、p50、p95）。

        :param values: 一个浮点数列表。
        :return: 一个包含 'count', 'p50', 'p95' 键的字典。
        """
        if not values:
            return {"count": 0, "p50": None, "p95": None}
        ordered = sorted(values)
        percentile = lambda p: round(ordered[min(len(ordered) - 1, int((len(ordered) - 1) * p))], 2)
        return {"count": len(ordered), "p50": percentile(.50), "p95": percentile(.95)}


llm_metrics = LLMMetrics()
