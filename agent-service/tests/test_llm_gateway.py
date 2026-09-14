import asyncio
import unittest
from unittest.mock import patch

from app.llm.gateway import LLMCallStats, LLMError, LLMGateway


class _Chunk:
    pass


class _Response:
    usage = None


class _Stream:
    def __init__(self, values):
        self.values = iter(values)

    def __aiter__(self):
        return self

    async def __anext__(self):
        value = next(self.values, StopAsyncIteration)
        if value is StopAsyncIteration:
            raise StopAsyncIteration
        if isinstance(value, BaseException):
            raise value
        return value


class _SlowFirstStream:
    def __aiter__(self):
        return self

    async def __anext__(self):
        await asyncio.sleep(1)
        return _Chunk()


class _Completions:
    def __init__(self, streams):
        self.streams = iter(streams)
        self.calls = 0

    async def create(self, **_):
        self.calls += 1
        value = next(self.streams)
        if isinstance(value, BaseException):
            raise value
        return value


class _Client:
    def __init__(self, completions):
        self.chat = type("Chat", (), {"completions": completions})()


class LLMGatewayTest(unittest.IsolatedAsyncioTestCase):
    def gateway(self, streams):
        gateway = object.__new__(LLMGateway)
        completions = _Completions(streams)
        gateway._client = _Client(completions)
        gateway._semaphore = asyncio.Semaphore(1)
        return gateway, completions

    async def test_stream_retries_before_first_chunk(self):
        # 测试在收到第一个数据块之前，流式传输是否会重试
        # 这个测试模拟了在流式传输开始时发生超时错误的情况，
        # 并验证系统是否会尝试重新连接或重试，而不是立即失败。
        # 最终，它应该成功接收到数据。
        gateway, completions = self.gateway([
            asyncio.TimeoutError(), _Stream([_Chunk()])])
        with patch("app.llm.gateway.asyncio.sleep", return_value=None):
            result = [item async for item in gateway.stream(
                [], model=None, temperature=0.1,
                stats=LLMCallStats("", True, 0))]
        self.assertEqual(1, len(result))
        self.assertEqual(2, completions.calls)

    async def test_stream_never_retries_after_output(self):
        # 测试在已经开始输出后，流式传输是否永不重试
        # 这个测试确保一旦LLM已经开始返回数据（即使只返回了一部分），
        # 如果此时发生错误，系统不会再去重试或请求备用模型。
        # 这是为了防止在已经部分回答后，又产生一个全新的、不连贯的回答。
        gateway, completions = self.gateway([
            _Stream([_Chunk(), asyncio.TimeoutError()]), _Stream([_Chunk()])])
        with self.assertRaises(LLMError) as caught:
            _ = [item async for item in gateway.stream(
                [], model=None, temperature=0.1)]
        self.assertEqual("TIMEOUT", caught.exception.error_type)
        self.assertEqual(1, completions.calls)

    async def test_stream_falls_back_only_before_output(self):
        # 测试流式传输仅在输出前进行回退
        # 这个测试验证了只有在主模型完全没有返回任何数据的情况下发生错误时，
        # 系统才会切换到备用（fallback）模型。如果主模型已经开始输出，则不应切换。
        gateway, completions = self.gateway([
            asyncio.TimeoutError(), _Stream([_Chunk()])])
        stats = LLMCallStats("primary", True, 0)
        with (patch("app.llm.gateway.settings.LLM_MAX_RETRIES", 0),
              patch("app.llm.gateway.settings.LLM_FALLBACK_MODEL", "fallback"),
              patch("app.llm.gateway.settings.LLM_ALLOWED_MODELS", "primary,fallback")):
            result = [item async for item in gateway.stream(
                [], model="primary", temperature=0.1, stats=stats)]
        self.assertEqual(1, len(result))
        self.assertEqual(2, completions.calls)
        self.assertTrue(stats.fallback_used)
        self.assertEqual("fallback", stats.model)

    async def test_complete_falls_back_after_retryable_failure(self):
        # 测试在可重试失败后，非流式补全是否会回退到备用模型
        # 对于非流式的`complete`方法，这个测试验证了在遇到可重试的错误（如超时）后，
        # 系统会尝试使用备用模型来完成请求。
        gateway, completions = self.gateway([asyncio.TimeoutError(), _Response()])
        stats = LLMCallStats("primary", False, 0)
        with (patch("app.llm.gateway.settings.LLM_MAX_RETRIES", 0),
              patch("app.llm.gateway.settings.LLM_FALLBACK_MODEL", "fallback"),
              patch("app.llm.gateway.settings.LLM_ALLOWED_MODELS", "primary,fallback")):
            response = await gateway.complete(
                [], model="primary", temperature=0.1, stats=stats)
        self.assertIsInstance(response, _Response)
        self.assertEqual(2, completions.calls)
        self.assertTrue(stats.fallback_used)

    async def test_complete_does_not_fallback_on_auth_failure(self):
        # 测试在认证失败时，非流式补全不会回退
        # 这个测试确保如果发生认证失败（例如API密钥错误），系统不会尝试使用备用模型，
        # 而是会直接报告认证失败。因为切换到备用模型也解决不了认证问题。
        auth_error = type("AuthError", (Exception,), {"status_code": 401})()
        gateway, completions = self.gateway([auth_error, _Response()])
        with (patch("app.llm.gateway.settings.LLM_MAX_RETRIES", 0),
              patch("app.llm.gateway.settings.LLM_FALLBACK_MODEL", "fallback"),
              patch("app.llm.gateway.settings.LLM_ALLOWED_MODELS", "primary,fallback")):
            with self.assertRaises(LLMError) as caught:
                await gateway.complete([], model="primary", temperature=0.1)
        self.assertEqual("AUTH_FAILED", caught.exception.error_type)
        self.assertEqual(1, completions.calls)

    def test_error_classification(self):
        # 测试错误分类功能
        # 这个测试用一个“速率限制”（Rate Limit）的例子，
        # 验证系统能否正确地将底层的HTTP错误（如状态码429）分类为特定的、
        # 具有业务含义的错误类型（如`RATE_LIMITED`），并判断其是否可重试。
        exc = type("RateLimit", (Exception,), {"status_code": 429})()
        error = LLMGateway._classify(exc)
        self.assertEqual("RATE_LIMITED", error.error_type)
        self.assertTrue(error.retryable)

    def test_error_matrix(self):
        # 测试错误矩阵
        # 这是一个综合性测试，覆盖了多种不同的HTTP错误（如401, 404, 503等），
        # 验证每种错误是否都能被准确地分类为预期的错误类型，
        # 以及是否被正确地标记为“可重试”或“不可重试”。
        cases = [
            (type("Auth", (Exception,), {"status_code": 401})(), "AUTH_FAILED", False),
            (type("Missing", (Exception,), {"status_code": 404})(), "MODEL_NOT_FOUND", False),
            (type("Server", (Exception,), {"status_code": 503})(), "UPSTREAM_5XX", True),
            (type("Bad", (Exception,), {"status_code": 400, "body": {"code": "content_filter"}})(),
             "CONTENT_BLOCKED", False),
            (type("APIConnectionError", (Exception,), {})(), "UPSTREAM_UNAVAILABLE", True),
        ]
        for exc, expected, retryable in cases:
            with self.subTest(expected=expected):
                error = LLMGateway._classify(exc)
                self.assertEqual(expected, error.error_type)
                self.assertEqual(retryable, error.retryable)

    def test_temperature_whitelist(self):
        # 测试temperature参数的白名单
        # LLM的`temperature`参数控制生成文本的随机性。通常，这个值需要在一个合理的范围内（例如0到1）。
        # 这个测试验证了如果提供一个过高（或无效）的`temperature`值，系统会拒绝该请求。
        with self.assertRaises(LLMError) as caught:
            LLMGateway.validate_temperature(0.9)
        self.assertEqual("INVALID_REQUEST", caught.exception.error_type)

    async def test_first_token_timeout_is_classified(self):
        # 测试首个令牌超时是否被正确分类
        # 系统有一个特殊的超时设置，用于等待LLM返回第一个数据块（token）。
        # 这个测试验证了如果在这个时间内没有收到任何数据，
        # 错误会被正确地分类为“TIMEOUT”类型。
        gateway, _ = self.gateway([_SlowFirstStream()])
        with (patch("app.llm.gateway.settings.LLM_MAX_RETRIES", 0),
              patch("app.llm.gateway.settings.LLM_FALLBACK_MODEL", ""),
              patch("app.llm.gateway.settings.LLM_FIRST_TOKEN_TIMEOUT_SECONDS", 0.01)):
            with self.assertRaises(LLMError) as caught:
                _ = [item async for item in gateway.stream(
                    [], model=None, temperature=0.1)]
        self.assertEqual("TIMEOUT", caught.exception.error_type)


if __name__ == "__main__":
    unittest.main()