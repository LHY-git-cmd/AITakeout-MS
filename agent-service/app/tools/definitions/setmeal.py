from decimal import Decimal

from pydantic import Field, model_validator

from app.tools.definitions.common import PageArguments
from app.tools.models import ToolAccess, ToolArguments, ToolDefinition


class QuerySetmealsArguments(PageArguments):
    name: str | None = Field(default=None, max_length=64, description="套餐名称关键字")
    category_id: int | None = Field(default=None, gt=0, description="分类ID")
    status: int | None = Field(default=None, ge=0, le=1, description="0停售，1起售")


class GetSetmealDetailArguments(ToolArguments):
    setmeal_id: int = Field(gt=0, description="套餐ID")


class UpdateSetmealArguments(ToolArguments):
    setmeal_id: int = Field(gt=0, description="套餐ID")
    name: str | None = Field(default=None, min_length=1, max_length=64)
    category_id: int | None = Field(default=None, gt=0)
    price: Decimal | None = Field(default=None, gt=0, max_digits=10, decimal_places=2)
    image: str | None = Field(default=None, min_length=1, max_length=255)
    description: str | None = Field(default=None, max_length=255)
    status: int | None = Field(default=None, ge=0, le=1, description="0停售，1起售")

    @model_validator(mode="after")
    def require_change(self):
        if not self.model_fields_set.difference({"setmeal_id"}):
            raise ValueError("at least one setmeal field must be changed")
        return self


DEFINITIONS = (
    ToolDefinition("query_setmeals", "分页查询套餐。", QuerySetmealsArguments,
                   "setmeal.query"),
    ToolDefinition("get_setmeal_detail", "查询套餐及其菜品明细。",
                   GetSetmealDetailArguments, "setmeal.detail"),
    ToolDefinition(
        "update_setmeal", "修改套餐信息或启停状态。执行前必须由管理员确认。",
        UpdateSetmealArguments, "setmeal.update", ToolAccess.WRITE,
        requires_confirmation=True,
    ),
)
