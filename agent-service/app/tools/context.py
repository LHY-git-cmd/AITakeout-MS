"""一次工具编排任务的可信上下文。"""
from pydantic import AliasChoices, BaseModel, ConfigDict, Field

from app.tools.models import ActorRole, ActorType


class ToolContext(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    task_id: str = Field(min_length=1, max_length=64)
    trace_id: str = Field(min_length=1, max_length=64)
    actor_id: int = Field(gt=0, validation_alias=AliasChoices("actor_id", "employee_id"))
    actor_type: ActorType = ActorType.ADMIN
    actor_role: ActorRole

    @property
    def employee_id(self) -> int:
        """兼容尚未改名的管理端工具执行器。"""
        return self.actor_id
