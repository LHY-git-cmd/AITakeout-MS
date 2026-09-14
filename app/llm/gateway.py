"""OpenAI 兼容 LLM 的统一调用边界。

同步和流式请求共享配置、错误分类、超时和有限重试策略。该模块不记录
提示词正文，调用方可通过 hooks 接入指标系统。
"""
import asyncio
import time
from dataclasses import dataclass
from typing import Any, AsyncIterator

import httpx
from openai import AsyncOpenAI

from app.core.config import settings


class LLMError(RuntimeError):
    """
    自定义异常类，用于表示在与 LLM 交互过程中发生的特定错误。

    :param error_type: 错误的分类字符串，如 'MODEL_NOT_FOUND'。
    :param message: 人类可读的错误信息。
    :param retryable: 一个布尔值，指示这个错误是否可以通过重试来解决。
    """
    def __init__(self, error_type: str, message: str, retryable: bool = False):
        super().__init__(message)
        self.error_type = error_type
        self.retryable = retryable


@dataclass
class LLMCallStats:
    """
    一个数据类，用于在 LLM 调用期间收集和传递统计信息。

    :param model: 正在使用的 LLM 模型名称。
    :param stream: 指示调用是否为流式模式的布尔值。
    :param started_at: 调用开始时的时间戳 (perf_counter)。
    :param first_token_ms: 对于流式调用，接收到第一个 token 所需的时间（毫秒）。
    :param prompt_tokens: 输入给模型的 prompt token 数量。
    :param completion_tokens: 模型生成的 completion token 数量。
    :param fallback_used: 指示是否在调用期间使用了备用模型的布尔值。
    """
    model: str
    stream: bool
    started_at: float
    first_token_ms: float | None = None
    prompt_tokens: int | None = None
    completion_tokens: int | None = None
    fallback_used: bool = False


class LLMGateway:
    """
    一个统一的网关，用于与兼容 OpenAI API 的大语言模型进行交互。

    该类封装了模型验证、超时管理、并发控制、重试逻辑、错误分类
    以及对备用模型的支持。
    """
    def __init__(self):
        """
        初始化 LLMGateway 实例。

        - `_client`: 一个 AsyncOpenAI 客户端实例，配置了 API 密钥、基础 URL 和超时。
        - `_semaphore`: 一个 asyncio.Semaphore，用于限制对 LLM 的最大并发请求数。
        """
        self._client = AsyncOpenAI(
            api_key=settings.LLM_API_KEY,
            base_url=settings.LLM_BASE_URL,
            timeout=httpx.Timeout(
                settings.LLM_TOTAL_TIMEOUT_SECONDS,
                connect=settings.LLM_CONNECT_TIMEOUT_SECONDS),
        )
        self._semaphore = asyncio.Semaphore(settings.LLM_MAX_CONCURRENCY)

    def validate_model(self, model: str | None) -> str:
        """
        验证指定的模型是否在允许的模型列表中。

        :param model: 要验证的模型名称。如果为 None，则使用默认模型。
        :return: 验证通过的模型名称。
        :raises LLMError: 如果模型不被支持。
        """
        selected = model or settings.LLM_MODEL
        if selected not in settings.allowed_models:
            raise LLMError("MODEL_NOT_ALLOWED", "不支持的模型")
        return selected

    @staticmethod
    def validate_temperature(temperature: float) -> float:
        """
        验证指定的温度参数是否在允许的范围内。

        :param temperature: 要验证的温度值。
        :return: 验证通过的温度值。
        :raises LLMError: 如果温度超出范围。
        """
        if not settings.LLM_TEMPERATURE_MIN <= temperature <= settings.LLM_TEMPERATURE_MAX:
            raise LLMError("INVALID_REQUEST", "模型温度参数超出允许范围")
        return temperature

    @staticmethod
    def _request_options(model, messages, temperature, stream=False,
                         tools=None, tool_choice=None):
        """
        构建对 LLM API 的请求选项。

        :param model: 模型名称。
        :param messages: 消息列表。
        :param temperature: 温度参数。
        :param stream: 是否为流式请求。
        :return: 一个包含请求选项的字典。
        """
        options = {
            "model": model, "messages": messages,
            "temperature": temperature, "stream": stream,
        }
        if settings.LLM_THINKING_ENABLED:
            options["extra_body"] = {"thinking": {"type": "enabled"}}
        if tools:
            options["tools"] = tools
            options["tool_choice"] = tool_choice or "auto"
        return options

    async def complete(self, messages: list[dict[str, Any]], *, model: str | None,
                       temperature: float, stats: LLMCallStats | None = None,
                       tools: list[dict[str, Any]] | None = None,
                       tool_choice: str | None = None):
        """
        以同步（一次性返回）模式执行 LLM 调用。

        该方法包含重试和备用模型逻辑。

        :param messages: 发送给 LLM 的消息列表。
        :param model: 要使用的模型名称。
        :param temperature: 温度参数。
        :param stats: 用于记录调用统计信息的可选 LLMCallStats 对象。
        :return: LLM API 的响应对象。
        """
        selected = self.validate_model(model)
        temperature = self.validate_temperature(temperature)
        deadline = time.monotonic() + settings.LLM_TOTAL_TIMEOUT_SECONDS
        async with self._capacity():
            try:
                return await self._complete_with_retry(
                    messages, selected, temperature, stats, deadline,
                    tools, tool_choice)
            except LLMError as error:
                fallback = self._fallback_for(selected, error)
                if not fallback:
                    raise
                if stats:
                    stats.fallback_used = True
                    stats.model = fallback
                return await self._complete_with_retry(
                    messages, fallback, temperature, stats, deadline,
                    tools, tool_choice)

    async def stream(self, messages: list[dict[str, Any]], *, model: str | None,
                     temperature: float, stats: LLMCallStats | None = None) -> AsyncIterator[Any]:
        """
        以流式模式执行 LLM 调用。

        该方法在接收到第一个 token 之前支持重试和备用模型逻辑。

        :param messages: 发送给 LLM 的消息列表。
        :param model: 要使用的模型名称。
        :param temperature: 温度参数。
        :param stats: 用于记录调用统计信息的可选 LLMCallStats 对象。
        :yield: LLM API 返回的流式数据块。
        """
        selected = self.validate_model(model)
        temperature = self.validate_temperature(temperature)
        async with self._capacity():
            # 首 token 前可重试；已有输出后由调用方接收异常，禁止重复请求。
            attempts = 0
            fallback_used = False
            deadline = time.monotonic() + settings.LLM_TOTAL_TIMEOUT_SECONDS
            while True:
                emitted = False
                try:
                    stream = await self._client.chat.completions.create(
                        **self._request_options(selected, messages, temperature, True))
                    first = True
                    iterator = stream.__aiter__()
                    while True:
                        try:
                            remaining = deadline - time.monotonic()
                            if remaining <= 0:
                                raise asyncio.TimeoutError
                            chunk = await asyncio.wait_for(
                                iterator.__anext__(),
                                min(settings.LLM_FIRST_TOKEN_TIMEOUT_SECONDS, remaining)
                                if first else remaining,
                            )
                        except StopAsyncIteration:
                            return
                        if first:
                            if stats:
                                stats.first_token_ms = (time.perf_counter() - stats.started_at) * 1000
                            first = False
                        emitted = True
                        yield chunk
                except asyncio.CancelledError:
                    raise
                except Exception as exc:
                    error = self._classify(exc)
                    if not emitted and error.retryable and attempts < settings.LLM_MAX_RETRIES:
                        attempts += 1
                        await asyncio.sleep(self._retry_delay(attempts, exc))
                        continue
                    fallback = self._fallback_for(selected, error)
                    if not emitted and not fallback_used and fallback:
                        fallback_used = True
                        selected = fallback
                        attempts = 0
                        if stats:
                            stats.fallback_used = True
                            stats.model = fallback
                        continue
                    raise error from exc

    def _fallback_for(self, selected: str, error: LLMError) -> str | None:
        """
        确定在发生可重试错误时应使用的备用模型。

        :param selected: 当前选择的模型。
        :param error: 发生的 LLMError。
        :return: 如果适用，返回备用模型的名称；否则返回 None。
        """
        fallback = settings.LLM_FALLBACK_MODEL.strip()
        if not error.retryable or not fallback or fallback == selected:
            return None
        if fallback not in settings.allowed_models:
            return None
        return fallback

    async def _complete_with_retry(self, messages, model, temperature, stats, deadline,
                                   tools=None, tool_choice=None):
        """内部方法：以同步模式执行 LLM 调用，并包含重试逻辑。"""
        for attempt in range(settings.LLM_MAX_RETRIES + 1):
            try:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise asyncio.TimeoutError
                response = await asyncio.wait_for(
                    self._client.chat.completions.create(
                        **self._request_options(model, messages, temperature,
                                                tools=tools, tool_choice=tool_choice)),
                    remaining)
                if stats and getattr(response, "usage", None):
                    stats.prompt_tokens = getattr(response.usage, "prompt_tokens", None)
                    stats.completion_tokens = getattr(response.usage, "completion_tokens", None)
                return response
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                error = self._classify(exc)
                if not error.retryable or attempt >= settings.LLM_MAX_RETRIES:
                    raise error from exc
                await asyncio.sleep(self._retry_delay(attempt + 1, exc))

    @staticmethod
    def _retry_delay(attempt: int, exc: Exception) -> float:
        """
        计算下一次重试的延迟时间。

        它优先使用 'retry-after' HTTP 头，否则使用指数退避策略。

        :param attempt: 当前的重试次数。
        :param exc: 捕获到的异常。
        :return: 以秒为单位的延迟时间。
        """
        headers = getattr(exc, "headers", None)
        retry_after = headers.get("retry-after") if headers else None
        try:
            if retry_after is not None:
                return min(float(retry_after), 30.0)
        except (TypeError, ValueError):
            pass
        return settings.LLM_RETRY_BASE_DELAY_SECONDS * (2 ** (attempt - 1))

    class _Capacity:
        """一个上下文管理器，用于通过信号量控制并发访问。"""
        def __init__(self, semaphore): self.semaphore = semaphore
        async def __aenter__(self):
            # 在事件循环线程内先检查令牌，避免 wait_for(0) 因调度导致误拒绝。
            if self.semaphore._value <= 0:  # noqa: SLF001 - 有界容量的非阻塞检查
                raise LLMError("CAPACITY_EXCEEDED", "模型服务当前繁忙，请稍后重试")
            await self.semaphore.acquire()
        async def __aexit__(self, *_): self.semaphore.release()

    def _capacity(self):
        """返回一个用于管理并发容量的上下文管理器实例。"""
        return self._Capacity(self._semaphore)

    @staticmethod
    def _classify(exc: Exception) -> LLMError:
        """
        将一个通用异常分类为一个特定的 LLMError。

        :param exc: 捕获到的原始异常。
        :return: 一个包含错误类型和可重试状态的 LLMError 实例。
        """
        status = getattr(exc, "status_code", None) or getattr(exc, "status", None)
        name = type(exc).__name__.lower()
        body = getattr(exc, "body", None)
        detail = str(body or "").lower()
        if status in (401, 403): return LLMError("AUTH_FAILED", "模型认证失败")
        if status == 404: return LLMError("MODEL_NOT_FOUND", "模型不可用")
        if status == 429: return LLMError("RATE_LIMITED", "模型服务限流", True)
        if status and int(status) >= 500: return LLMError("UPSTREAM_5XX", "模型服务暂时不可用", True)
        if status == 400 and any(value in detail for value in ("content_filter", "content policy", "moderation")):
            return LLMError("CONTENT_BLOCKED", "请求被内容安全策略拦截")
        if status == 400:
            return LLMError("INVALID_REQUEST", "模型请求参数无效")
        if (isinstance(exc, (asyncio.TimeoutError, TimeoutError))
                or "timeout" in name):
            return LLMError("TIMEOUT", "模型请求超时", True)
        if "connection" in name:
            return LLMError("UPSTREAM_UNAVAILABLE", "无法连接模型服务", True)
        return LLMError("UNKNOWN", "模型调用失败", False)
