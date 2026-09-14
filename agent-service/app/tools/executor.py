"""调用Java原子业务接口；Python不直接访问业务数据库。"""
import asyncio
from typing import Any

import httpx

from app.core.config import settings
from app.tools.context import ToolContext
from app.tools.models import ToolDefinition, ToolResult


class ToolExecutionError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.error_type = code


class JavaToolClient:
    def __init__(self, base_url: str | None = None, token: str | None = None):
        self.base_url = (base_url or settings.JAVA_INTERNAL_BASE_URL).rstrip("/")
        self.token = token if token is not None else settings.AGENT_INTERNAL_SERVICE_TOKEN

    async def execute(self, definition: ToolDefinition, arguments: dict[str, Any],
                      context: ToolContext, tool_call_id: str) -> ToolResult:
        path = "/internal/agent/operations/prepare" if definition.requires_confirmation \
            else "/internal/agent/operations/execute"
        payload = {
            "request_id": tool_call_id,
            "task_id": context.task_id,
            "tool_call_id": tool_call_id,
            "operation": definition.operation,
            "arguments": arguments,
        }
        return await self._post_result(path, payload, context)

    async def confirmation_status(self, confirmation_id: str,
                                  context: ToolContext) -> dict[str, Any]:
        return await self._request_json(
            "GET", f"/internal/agent/operations/confirmations/{confirmation_id}",
            context=context)

    async def execute_confirmed(self, confirmation_id: str,
                                context: ToolContext) -> ToolResult:
        return await self._post_result(
            "/internal/agent/operations/execute-confirmed",
            {"confirmation_id": confirmation_id}, context)

    async def wait_and_execute(self, confirmation_id: str,
                               context: ToolContext) -> ToolResult:
        loop = asyncio.get_running_loop()
        deadline = loop.time() + settings.TOOL_CONFIRMATION_TIMEOUT_SECONDS
        while loop.time() < deadline:
            state = await self.confirmation_status(confirmation_id, context)
            status = state.get("status")
            if status == "CONFIRMED":
                return await self.execute_confirmed(confirmation_id, context)
            if status in {"REJECTED", "EXPIRED", "EXECUTED"}:
                return ToolResult(
                    tool_call_id=state.get("tool_call_id", confirmation_id),
                    status=status.lower(), data=state if status == "EXECUTED" else None,
                    error=None if status == "EXECUTED" else {
                        "code": f"CONFIRMATION_{status}",
                        "message": "管理员未确认该操作" if status == "REJECTED" else "确认已失效",
                    }, trace_id=context.trace_id)
            await asyncio.sleep(settings.TOOL_CONFIRMATION_POLL_SECONDS)
        return ToolResult(
            tool_call_id=confirmation_id, status="expired", data=None,
            error={"code": "CONFIRMATION_TIMEOUT", "message": "等待管理员确认超时"},
            trace_id=context.trace_id)

    async def _post_result(self, path: str, payload: dict[str, Any],
                           context: ToolContext) -> ToolResult:
        data = await self._request_json("POST", path, json=payload, context=context)
        try:
            return ToolResult.model_validate(data)
        except Exception as exception:
            raise ToolExecutionError("INVALID_TOOL_RESPONSE", "Java工具响应格式无效") from exception

    async def _request_json(self, method: str, path: str, *, context: ToolContext,
                            json: dict[str, Any] | None = None) -> dict[str, Any]:
        headers = {
            "X-Agent-Service-Token": self.token,
            "X-Trace-ID": context.trace_id,
        }
        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                response = await client.request(method, self.base_url + path,
                                                headers=headers, json=json)
            response.raise_for_status()
            value = response.json()
            if not isinstance(value, dict):
                raise ValueError("response is not an object")
            return value
        except httpx.TimeoutException as exception:
            raise ToolExecutionError("TOOL_TIMEOUT", "业务工具调用超时") from exception
        except (httpx.HTTPError, ValueError) as exception:
            raise ToolExecutionError("TOOL_UNAVAILABLE", "业务工具服务暂时不可用") from exception
