from pydantic import Field

from app.tools.models import EmptyArguments, ToolAccess, ToolArguments, ToolDefinition


class UpdateShopStatusArguments(ToolArguments):
    status: int = Field(ge=0, le=1, description="0打烊，1营业")


DEFINITIONS = (
    ToolDefinition("get_shop_status", "查询店铺营业状态。", EmptyArguments, "shop.status.get"),
    ToolDefinition(
        "update_shop_status", "修改店铺营业状态。执行前必须由管理员确认。",
        UpdateShopStatusArguments, "shop.status.update", ToolAccess.WRITE,
        requires_confirmation=True,
    ),
)
