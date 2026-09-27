"""Python工具注册表的通用模型。"""
from dataclasses import dataclass
from enum import StrEnum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ActorType(StrEnum):
    ADMIN = "ADMIN"
    USER = "USER"
    SYSTEM = "SYSTEM"


class ActorRole(StrEnum):
    SUPER_ADMIN = "SUPER_ADMIN"
    ADMIN = "ADMIN"
    CUSTOMER = "CUSTOMER"
    SYSTEM = "SYSTEM"


# 保留旧导入名称，现有管理端工具定义可平滑迁移。
AdminRole = ActorRole
ADMIN_ROLES = frozenset({ActorRole.SUPER_ADMIN, ActorRole.ADMIN})


class ToolAccess(StrEnum):
    READ = "read"
    WRITE = "write"


class ToolArguments(BaseModel):
    """所有工具参数的严格基类。"""
    model_config = ConfigDict(extra="forbid")


class EmptyArguments(ToolArguments):
    pass


class ToolResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    tool_call_id: str = Field(min_length=1, max_length=128)
    status: str
    data: dict[str, Any] | list[Any] | None = None
    error: dict[str, Any] | None = None
    trace_id: str | None = None


@dataclass(frozen=True)
class ToolDefinition:
    name: str
    description: str
    arguments_model: type[ToolArguments]
    operation: str
    access: ToolAccess = ToolAccess.READ
    allowed_roles: frozenset[ActorRole] = ADMIN_ROLES
    requires_confirmation: bool = False
    timeout_seconds: float = 10.0

    def __post_init__(self):
        if not self.name or not self.operation:
            raise ValueError("tool name and operation are required")
        if self.timeout_seconds <= 0:
            raise ValueError("tool timeout must be positive")
        # 低风险写操作（如明确加购）允许直接执行；高风险写工具必须在定义中显式开启确认。

    def openai_schema(self) -> dict[str, Any]:
        parameters = self.arguments_model.model_json_schema()
        parameters["additionalProperties"] = False
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": parameters,
            },
        }
