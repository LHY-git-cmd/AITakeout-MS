"""普通用户专属工具；所有业务事实由Java服务返回。"""
from typing import Literal

from pydantic import Field, model_validator

from app.tools.models import ActorRole, EmptyArguments, ToolAccess, ToolArguments, ToolDefinition

CUSTOMER_ONLY = frozenset({ActorRole.CUSTOMER})


class SearchProductsArguments(ToolArguments):
    keyword: str = Field(min_length=1, max_length=64, description="菜品或套餐关键词")
    limit: int = Field(default=10, ge=1, le=20)


class ProductDetailArguments(ToolArguments):
    product_type: Literal["dish", "setmeal"]
    product_id: int = Field(gt=0)


class ListOrdersArguments(ToolArguments):
    page: int = Field(default=1, ge=1, le=1000)
    page_size: int = Field(default=10, ge=1, le=20)
    status: int | None = Field(default=None, ge=1, le=7)


class OrderIdArguments(ToolArguments):
    order_id: int = Field(gt=0)


class AddCartItemArguments(ToolArguments):
    dish_id: int | None = Field(default=None, gt=0)
    setmeal_id: int | None = Field(default=None, gt=0)
    dish_flavor: str | None = Field(default=None, max_length=100)
    quantity: int = Field(default=1, ge=1, le=20)

    @model_validator(mode="after")
    def validate_product(self):
        if (self.dish_id is None) == (self.setmeal_id is None):
            raise ValueError("dish_id and setmeal_id must have exactly one value")
        return self


DEFINITIONS = (
    ToolDefinition("search_products", "搜索当前可售菜品和套餐。", SearchProductsArguments,
                   "user.product.search", allowed_roles=CUSTOMER_ONLY),
    ToolDefinition("get_product_detail", "查询可售菜品或套餐详情。", ProductDetailArguments,
                   "user.product.detail", allowed_roles=CUSTOMER_ONLY),
    ToolDefinition("get_cart", "查询当前用户购物车。", EmptyArguments,
                   "user.cart.get", allowed_roles=CUSTOMER_ONLY),
    ToolDefinition("list_my_orders", "分页查询当前用户订单。", ListOrdersArguments,
                   "user.order.list", allowed_roles=CUSTOMER_ONLY),
    ToolDefinition("get_my_order_detail", "查询当前用户指定订单详情。", OrderIdArguments,
                   "user.order.detail", allowed_roles=CUSTOMER_ONLY),
    ToolDefinition("get_order_timeline", "查询当前用户订单配送与售后时间线。", OrderIdArguments,
                   "user.order.timeline", allowed_roles=CUSTOMER_ONLY),
    ToolDefinition("get_after_sale_status", "查询当前用户订单的最新售后状态。", OrderIdArguments,
                   "user.after_sale.status", allowed_roles=CUSTOMER_ONLY),
    ToolDefinition("add_cart_item", "将明确指定的菜品或套餐加入当前用户购物车。",
                   AddCartItemArguments, "user.cart.add", ToolAccess.WRITE,
                   allowed_roles=CUSTOMER_ONLY, requires_confirmation=False),
)
