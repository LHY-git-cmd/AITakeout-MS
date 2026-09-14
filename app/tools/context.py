"""一次工具编排任务的可信上下文。"""
from pydantic import BaseModel, ConfigDict, Field

from app.tools.models import AdminRole


class ToolContext(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    task_id: str = Field(min_length=1, max_length=64)
    trace_id: str = Field(min_length=1, max_length=64)
    employee_id: int = Field(gt=0)
    actor_role: AdminRole
