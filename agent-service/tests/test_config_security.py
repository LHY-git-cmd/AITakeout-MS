import asyncio
import json
import unittest
from datetime import datetime, timedelta, timezone
from unittest.mock import patch

from app.core.agent import PythonAgent
from app.core.config import Settings
from app import main
from app.llm.metrics import LLMMetrics


class ConfigSecurityTest(unittest.TestCase):
    # 测试启动验证是否在不泄露敏感值的情况下，正确列出配置项名称
    # 验证在启动配置检查时，如果出现错误（如此处的LLM_BASE_URL无效），
    # 错误日志会指明是哪个配置项出了问题，但不会打印出该配置项的敏感内容（如LLM_API_KEY）。
    # 这是为了防止API密钥等敏感信息泄露到日志中。
    def test_startup_validation_lists_names_without_values(self):
        secret = "should-never-be-printed"
        config = Settings(
            _env_file=None,
            LLM_PROVIDER="deepseek",
            LLM_BASE_URL="not-a-url",
            LLM_MODEL="model",
            LLM_API_KEY=secret,
        )

        with self.assertRaises(RuntimeError) as raised:
            config.validate_startup()

        self.assertIn("LLM_BASE_URL", str(raised.exception))
        self.assertNotIn(secret, str(raised.exception))

    # 测试LLM请求日志是否排除了prompt和历史记录
    # 验证在记录LLM（大语言模型）请求的日志时，不会包含实际的对话内容（如系统提示或用户问题）。
    # 这是一项重要的安全措施，旨在保护用户隐私和数据安全，防止敏感对话内容被记录到日志里。

    def test_llm_request_log_excludes_prompt_and_history(self):
        messages = [
            {"role": "system", "content": "private system prompt"},
            {"role": "user", "content": "private employee question"},
        ]

        with self.assertLogs("sky.agent.llm", level="INFO") as captured:
            PythonAgent._log_llm_request(
                "task-1", "session-1", "model-1", 0.2, messages, True)

        output = "\n".join(captured.output)
        record = captured.records[0]
        self.assertEqual("task-1", record.task_id)
        self.assertEqual(46, record.prompt_chars)
        self.assertNotIn("private system prompt", output)
        self.assertNotIn("private employee question", output)

    # 健康检查必须使用安全的依赖名称和错误类型，不能暴露内部服务地址。
    def test_readiness_reports_qdrant_outage_as_safe_degraded_status(self):
        class FakeResponse:
            def __init__(self, success):
                self.is_success = success

        class FakeClient:
            def __init__(self):
                self.calls = 0

            async def __aenter__(self):
                return self

            async def __aexit__(self, *_):
                return False

            async def get(self, url):
                self.calls += 1
                return FakeResponse(self.calls != 1)

        request = type("Request", (), {
            "app": type("App", (), {
                "state": type("State", (), {"agent": object(), "llm_configured": True})()
            })()
        })()
        original_client = main.httpx.AsyncClient
        main.httpx.AsyncClient = lambda **_: FakeClient()
        try:
            response = asyncio.run(main.readiness(request))
        finally:
            main.httpx.AsyncClient = original_client

        payload = json.loads(response.body)
        self.assertEqual(503, response.status_code)
        self.assertEqual("degraded", payload["status"])
        self.assertEqual("QDRANT_UNAVAILABLE", payload["error_type"])
        self.assertEqual("unavailable", payload["dependencies"]["qdrant"]["status"])
        self.assertIn("checked_at", payload)
        self.assertNotIn("http://", json.dumps(payload))

    # LLM 尚无调用记录只影响页面能力状态，不能阻断 Compose 的服务就绪检查。
    def test_readiness_keeps_cold_start_with_unknown_llm_http_healthy(self):
        class FakeResponse:
            is_success = True

        class FakeClient:
            async def __aenter__(self):
                return self

            async def __aexit__(self, *_):
                return False

            async def get(self, _):
                return FakeResponse()

        request = type("Request", (), {
            "app": type("App", (), {
                "state": type("State", (), {"agent": object(), "llm_configured": True})()
            })()
        })()
        original_client = main.httpx.AsyncClient
        main.httpx.AsyncClient = lambda **_: FakeClient()
        try:
            response = asyncio.run(main.readiness(request))
        finally:
            main.httpx.AsyncClient = original_client

        self.assertIsInstance(response, dict)
        self.assertEqual("degraded", response["status"])
        self.assertEqual("unknown", response["llm"]["status"])

    # 累计成功次数不能覆盖最近连续失败；LLM 核心能力不可用时页面必须离线。
    def test_readiness_marks_recent_continuous_llm_failures_offline(self):
        async def record_sequence(metrics):
            for _ in range(3):
                await metrics.record(model="model", status="completed", error_type=None,
                                     elapsed_ms=1, first_token_ms=None,
                                     prompt_tokens=1, completion_tokens=1)
            for _ in range(2):
                await metrics.record(model="model", status="failed", error_type="UPSTREAM",
                                     elapsed_ms=1, first_token_ms=None,
                                     prompt_tokens=1, completion_tokens=0)

        metrics = LLMMetrics()
        asyncio.run(record_sequence(metrics))
        request = type("Request", (), {
            "app": type("App", (), {
                "state": type("State", (), {"agent": object(), "llm_configured": True})()
            })()
        })()
        original_metrics = main.llm_metrics
        original_client = main.httpx.AsyncClient

        class HealthyClient:
            async def __aenter__(self): return self
            async def __aexit__(self, *_): return False
            async def get(self, _): return type("Response", (), {"is_success": True})()

        main.llm_metrics = metrics
        main.httpx.AsyncClient = lambda **_: HealthyClient()
        try:
            response = asyncio.run(main.readiness(request))
        finally:
            main.llm_metrics = original_metrics
            main.httpx.AsyncClient = original_client

        self.assertEqual("offline", response["status"])
        self.assertEqual("LLM_CALL_FAILED", response["error_type"])

    # LLM 是普通问答核心能力；即使知识库同时异常，整体仍须优先显示离线。
    def test_readiness_prioritizes_llm_offline_over_qdrant_degraded(self):
        async def record_failures(metrics):
            for _ in range(2):
                await metrics.record(model="model", status="failed", error_type="UPSTREAM",
                                     elapsed_ms=1, first_token_ms=None,
                                     prompt_tokens=1, completion_tokens=0)

        metrics = LLMMetrics()
        asyncio.run(record_failures(metrics))
        request = type("Request", (), {
            "app": type("App", (), {
                "state": type("State", (), {"agent": object(), "llm_configured": True})()
            })()
        })()
        original_metrics = main.llm_metrics
        original_client = main.httpx.AsyncClient

        class QdrantUnavailableClient:
            def __init__(self): self.calls = 0
            async def __aenter__(self): return self
            async def __aexit__(self, *_): return False
            async def get(self, _):
                self.calls += 1
                return type("Response", (), {"is_success": self.calls != 1})()

        main.llm_metrics = metrics
        main.httpx.AsyncClient = lambda **_: QdrantUnavailableClient()
        try:
            response = asyncio.run(main.readiness(request))
        finally:
            main.llm_metrics = original_metrics
            main.httpx.AsyncClient = original_client

        payload = json.loads(response.body)
        self.assertEqual("offline", payload["status"])
        self.assertEqual("LLM_CALL_FAILED", payload["error_type"])

    # 冷却窗口必须是显式正数，供可控时钟的过期判定使用。
    def test_llm_failure_cooldown_is_positive(self):
        self.assertGreater(main.LLM_FAILURE_COOLDOWN_SECONDS, 0)

    # 冷却判定必须依赖可替换时钟，避免时间相关测试不稳定。
    def test_llm_failure_clock_is_injectable(self):
        self.assertTrue(callable(getattr(main, "_utc_now", None)))

    # 连续调用失败只在冷却窗口内离线，到期后降为可手动验证的 unknown。
    def test_llm_failure_expires_to_unknown_but_invalid_configuration_does_not(self):
        async def record_failures(metrics):
            for _ in range(2):
                await metrics.record(model="model", status="failed", error_type="UPSTREAM",
                                     elapsed_ms=1, first_token_ms=None,
                                     prompt_tokens=1, completion_tokens=0)
            return await metrics.snapshot()

        metrics = LLMMetrics()
        snapshot = asyncio.run(record_failures(metrics))
        failed_at = datetime.fromisoformat(snapshot["recent"]["last_failure_at"])
        original_metrics = main.llm_metrics
        main.llm_metrics = metrics
        try:
            with patch.object(main, "_utc_now", return_value=failed_at + timedelta(
                    seconds=main.LLM_FAILURE_COOLDOWN_SECONDS - 1)):
                within_window = asyncio.run(main._llm_readiness_summary(True))
            with patch.object(main, "_utc_now", return_value=failed_at + timedelta(
                    seconds=main.LLM_FAILURE_COOLDOWN_SECONDS)):
                at_expiration = asyncio.run(main._llm_readiness_summary(True))
            with patch.object(main, "_utc_now", return_value=failed_at + timedelta(
                    seconds=main.LLM_FAILURE_COOLDOWN_SECONDS + 1)):
                after_window = asyncio.run(main._llm_readiness_summary(True))
                invalid_configuration = asyncio.run(main._llm_readiness_summary(False))
        finally:
            main.llm_metrics = original_metrics

        self.assertEqual("unavailable", within_window.status)
        self.assertEqual("unknown", at_expiration.status)
        self.assertEqual("unknown", after_window.status)
        self.assertEqual("unavailable", invalid_configuration.status)

    # 一次新的成功调用应清除连续失败状态，恢复 LLM 健康能力。
    def test_llm_metrics_recovers_after_success_following_continuous_failures(self):
        async def record_sequence(metrics):
            for _ in range(2):
                await metrics.record(model="model", status="failed", error_type="UPSTREAM",
                                     elapsed_ms=1, first_token_ms=None,
                                     prompt_tokens=1, completion_tokens=0)
            await metrics.record(model="model", status="completed", error_type=None,
                                 elapsed_ms=1, first_token_ms=None,
                                 prompt_tokens=1, completion_tokens=1)
            return await metrics.snapshot()

        snapshot = asyncio.run(record_sequence(LLMMetrics()))
        recent = snapshot.get("recent", {})
        self.assertEqual("completed", recent.get("last_status"))
        self.assertEqual(0, recent.get("consecutive_failures"))

    # 成功调用恢复后，页面状态应从 LLM 离线恢复为在线。
    def test_readiness_recovers_online_after_llm_success(self):
        async def record_sequence(metrics):
            for _ in range(2):
                await metrics.record(model="model", status="failed", error_type="UPSTREAM",
                                     elapsed_ms=1, first_token_ms=None,
                                     prompt_tokens=1, completion_tokens=0)
            await metrics.record(model="model", status="completed", error_type=None,
                                 elapsed_ms=1, first_token_ms=None,
                                 prompt_tokens=1, completion_tokens=1)

        metrics = LLMMetrics()
        asyncio.run(record_sequence(metrics))
        request = type("Request", (), {
            "app": type("App", (), {
                "state": type("State", (), {"agent": object(), "llm_configured": True})()
            })()
        })()
        original_metrics = main.llm_metrics
        original_client = main.httpx.AsyncClient

        class HealthyClient:
            async def __aenter__(self): return self
            async def __aexit__(self, *_): return False
            async def get(self, _): return type("Response", (), {"is_success": True})()

        main.llm_metrics = metrics
        main.httpx.AsyncClient = lambda **_: HealthyClient()
        try:
            response = asyncio.run(main.readiness(request))
        finally:
            main.llm_metrics = original_metrics
            main.httpx.AsyncClient = original_client

        self.assertEqual("online", response["status"])


if __name__ == "__main__":
    unittest.main()
