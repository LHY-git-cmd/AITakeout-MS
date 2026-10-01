"""LangGraph用户工作流的强类型状态。"""
from typing import Any, Literal, TypedDict


UserIntent = Literal[
    "knowledge", "recommendation", "cart", "order", "after_sale", "unknown"
]


class UserAgentState(TypedDict, total=False):
    task_id: str
    session_id: str | None
    actor_type: str
    query: str
    context: dict[str, Any]
    intent: UserIntent
    confidence: float
    slots: dict[str, Any]
    missing_slots: list[str]
    retrieval_plan: str
    tool_plan: list[str]
    page_context: dict[str, Any]
    pending_confirmation: dict[str, Any] | None
    response_blocks: list[dict[str, Any]]
    system_instruction: str
    error: dict[str, Any] | None
