"""Python工具注册表的通用模型。"""
from dataclasses import dataclass
from enum import StrEnum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class AdminRole(StrEnum):
    SUPER_ADMIN = "SUPER_ADMIN"
    ADMIN = "ADMIN"


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
    allowed_roles: frozenset[AdminRole] = frozenset(AdminRole)
    requires_confirmation: bool = False
    timeout_seconds: float = 10.0

    def __post_init__(self):
        if not self.name or not self.operation:
            raise ValueError("tool name and operation are required")
        if self.timeout_seconds <= 0:
            raise ValueError("tool timeout must be positive")
        if self.access == ToolAccess.WRITE and not self.requires_confirmation:
            raise ValueError("write tools must require confirmation")

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
