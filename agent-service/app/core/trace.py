"""跨异步任务传播追踪与业务关联标识。"""
from contextvars import ContextVar

current_trace_id: ContextVar[str | None] = ContextVar("trace_id", default=None)
current_task_id: ContextVar[str | None] = ContextVar("task_id", default=None)
current_session_id: ContextVar[str | None] = ContextVar("session_id", default=None)
