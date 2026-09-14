from datetime import datetime
from typing import Literal

from pydantic import Field, model_validator

from app.tools.definitions.common import PageArguments
from app.tools.models import EmptyArguments, ToolAccess, ToolArguments, ToolDefinition


class QueryOrdersArguments(PageArguments):
    number: str | None = Field(default=None, max_length=64, description="订单号")
    phone: str | None = Field(default=None, max_length=20, description="顾客手机号")
    status: int | None = Field(default=None, ge=1, le=7, description="订单状态1至7")
    begin_time: datetime | None = Field(default=None, description="开始时间，ISO 8601格式")
    end_time: datetime | None = Field(default=None, description="结束时间，ISO 8601格式")

    @model_validator(mode="after")
    def validate_range(self):
        if (self.begin_time is None) != (self.end_time is None):
            raise ValueError("begin_time and end_time must be provided together")
        if self.begin_time and self.end_time:
            if self.end_time < self.begin_time:
                raise ValueError("end_time must not be before begin_time")
            if (self.end_time - self.begin_time).days > 366:
                raise ValueError("date range must not exceed 366 days")
        return self


class GetOrderDetailArguments(ToolArguments):
    order_id: int = Field(gt=0, description="订单ID")


class UpdateOrderStatusArguments(ToolArguments):
    order_id: int = Field(gt=0, description="订单ID")
    action: Literal["confirm", "reject", "cancel", "deliver", "complete"] = Field(
        description="订单状态操作"
    )
    reason: str | None = Field(default=None, min_length=1, max_length=255,
                               description="拒单或取消时必填的原因")

    @model_validator(mode="after")
    def validate_reason(self):
        if self.action in {"reject", "cancel"} and not self.reason:
            raise ValueError("reason is required for reject or cancel")
        if self.action not in {"reject", "cancel"} and self.reason is not None:
            raise ValueError("reason is only accepted for reject or cancel")
        return self


DEFINITIONS = (
    ToolDefinition("query_orders", "按条件分页查询订单。", QueryOrdersArguments, "order.query"),
    ToolDefinition("get_order_detail", "查询订单及订单明细。", GetOrderDetailArguments,
                   "order.detail"),
    ToolDefinition("get_order_statistics", "查询各状态订单数量。", EmptyArguments,
                   "order.statistics"),
    ToolDefinition(
        "update_order_status", "修改订单状态。执行前必须由管理员确认。",
        UpdateOrderStatusArguments, "order.status.update", ToolAccess.WRITE,
        requires_confirmation=True,
    ),
)
