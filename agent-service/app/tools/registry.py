"""工具注册、角色过滤和参数校验。"""
import json
from collections.abc import Iterable
from typing import Any

from pydantic import ValidationError

from app.tools.models import ActorRole, ToolArguments, ToolDefinition


class ToolRegistryError(ValueError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


class ToolRegistry:
    def __init__(self, definitions: Iterable[ToolDefinition] = ()):
        self._definitions: dict[str, ToolDefinition] = {}
        for definition in definitions:
            self.register(definition)

    def register(self, definition: ToolDefinition) -> None:
        if definition.name in self._definitions:
            raise ValueError(f"duplicate tool: {definition.name}")
        self._definitions[definition.name] = definition

    def names_for_role(self, role: ActorRole | str) -> tuple[str, ...]:
        resolved_role = ActorRole(role)
        return tuple(
            name for name, definition in self._definitions.items()
            if resolved_role in definition.allowed_roles
        )

    def schemas_for_role(self, role: ActorRole | str,
                         allowed_tools: frozenset[str] | None = None) -> list[dict[str, Any]]:
        names = self.names_for_role(role)
        if allowed_tools:
            names = tuple(name for name in names if name in allowed_tools)
        return [self._definitions[name].openai_schema() for name in names]

    def definition_for_role(self, name: str, role: ActorRole | str,
                            allowed_tools: frozenset[str] | None = None) -> ToolDefinition:
        definition = self._definitions.get(name)
        if definition is None:
            raise ToolRegistryError("TOOL_NOT_FOUND", "模型请求了未注册工具")
        if ActorRole(role) not in definition.allowed_roles:
            raise ToolRegistryError("TOOL_PERMISSION_DENIED", "当前主体无权调用该工具")
        if allowed_tools and name not in allowed_tools:
            raise ToolRegistryError("TOOL_SCENE_DENIED", "当前场景不允许调用该工具")
        return definition

    def validate_arguments(self, name: str, role: ActorRole | str,
                           raw_arguments: str | dict[str, Any],
                           allowed_tools: frozenset[str] | None = None) -> ToolArguments:
        definition = self.definition_for_role(name, role, allowed_tools)
        try:
            values = json.loads(raw_arguments) if isinstance(raw_arguments, str) else raw_arguments
        except json.JSONDecodeError as exception:
            raise ToolRegistryError("INVALID_TOOL_ARGUMENTS", "工具参数不是有效JSON") from exception
        if not isinstance(values, dict):
            raise ToolRegistryError("INVALID_TOOL_ARGUMENTS", "工具参数必须是JSON对象")
        try:
            return definition.arguments_model.model_validate(values)
        except ValidationError as exception:
            raise ToolRegistryError("INVALID_TOOL_ARGUMENTS", "工具参数校验失败") from exception
