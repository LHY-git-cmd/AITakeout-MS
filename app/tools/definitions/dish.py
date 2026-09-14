from decimal import Decimal

from pydantic import Field, model_validator

from app.tools.definitions.common import PageArguments
from app.tools.models import ToolAccess, ToolArguments, ToolDefinition


class QueryDishesArguments(PageArguments):
    name: str | None = Field(default=None, max_length=64, description="菜品名称关键字")
    category_id: int | None = Field(default=None, gt=0, description="分类ID")
    status: int | None = Field(default=None, ge=0, le=1, description="0停售，1起售")


class GetDishDetailArguments(ToolArguments):
    dish_id: int = Field(gt=0, description="菜品ID")


class UpdateDishArguments(ToolArguments):
    dish_id: int = Field(gt=0, description="菜品ID")
    name: str | None = Field(default=None, min_length=1, max_length=64)
    category_id: int | None = Field(default=None, gt=0)
    price: Decimal | None = Field(default=None, gt=0, max_digits=10, decimal_places=2)
    image: str | None = Field(default=None, min_length=1, max_length=255)
    description: str | None = Field(default=None, max_length=255)
    status: int | None = Field(default=None, ge=0, le=1, description="0停售，1起售")

    @model_validator(mode="after")
    def require_change(self):
        if not self.model_fields_set.difference({"dish_id"}):
            raise ValueError("at least one dish field must be changed")
        return self


DEFINITIONS = (
    ToolDefinition("query_dishes", "分页查询菜品。", QueryDishesArguments, "dish.query"),
    ToolDefinition("get_dish_detail", "查询菜品详情和口味。", GetDishDetailArguments,
                   "dish.detail"),
    ToolDefinition(
        "update_dish", "修改菜品信息或启停状态。执行前必须由管理员确认。",
        UpdateDishArguments, "dish.update", ToolAccess.WRITE, requires_confirmation=True,
    ),
)
