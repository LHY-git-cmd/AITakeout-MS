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


class OrderActionArguments(OrderIdArguments):
    reason: str = Field(min_length=1, max_length=255)


class PreviewOrderActionArguments(OrderIdArguments):
    action: Literal["CANCELLATION", "AFTER_SALE"]


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


class RecommendMealsArguments(ToolArguments):
    """结构化推荐参数；疾病名称仅接受服务端支持的受控编码。"""
    scene: Literal["GENERAL", "GOAL_BASED", "SEASONAL_REGIONAL", "CONDITION_AWARE", "COMMON_COLD"] = "GENERAL"
    people_count: int = Field(default=1, ge=1, le=20)
    budget: float | None = Field(default=None, ge=0)
    meal_type: str | None = Field(default=None, max_length=32)
    region_code: str | None = Field(default=None, max_length=32)
    use_saved_profile: bool = True
    allergens: list[str] = Field(default_factory=list, max_length=20)
    excluded_ingredients: list[str] = Field(default_factory=list, max_length=30)
    goals: list[Literal["WEIGHT_LOSS", "HIGH_PROTEIN", "LOW_SODIUM", "LOW_SUGAR", "LOW_FAT", "LIGHT"]] = Field(default_factory=list)
    conditions: list[Literal["HYPERTENSION", "DIABETES", "HYPERLIPIDEMIA", "OBESITY"]] = Field(default_factory=list)
    preferences: list[str] = Field(default_factory=list, max_length=20)
    hard_constraints: list[str] = Field(default_factory=list, max_length=30)
    soft_preferences: list[str] = Field(default_factory=list, max_length=30)
    season: str | None = Field(default=None, max_length=16)
    confidence: float = Field(default=0.95, ge=0, le=1)
    limit: int = Field(default=5, ge=1, le=20)


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
    ToolDefinition("preview_order_action", "预览取消订单或售后申请的影响和当前状态。",
                   PreviewOrderActionArguments, "user.order.action.preview",
                   allowed_roles=CUSTOMER_ONLY),
    ToolDefinition("remind_order", "催促商家处理当前用户本人待接单订单。", OrderIdArguments,
                   "user.order.remind", ToolAccess.WRITE, allowed_roles=CUSTOMER_ONLY,
                   requires_confirmation=False),
    ToolDefinition("request_order_cancellation", "为当前用户本人订单提交取消申请；必须界面确认。",
                   OrderActionArguments, "user.order.cancel.request", ToolAccess.WRITE,
                   allowed_roles=CUSTOMER_ONLY, requires_confirmation=True),
    ToolDefinition("submit_after_sale", "为当前用户本人订单提交售后申请；必须界面确认。",
                   OrderActionArguments, "user.after_sale.submit", ToolAccess.WRITE,
                   allowed_roles=CUSTOMER_ONLY, requires_confirmation=True),
    ToolDefinition("add_cart_item", "将明确指定的菜品或套餐加入当前用户购物车。",
                   AddCartItemArguments, "user.cart.add", ToolAccess.WRITE,
                   allowed_roles=CUSTOMER_ONLY, requires_confirmation=False),
    ToolDefinition("get_diet_profile", "读取当前用户主动保存的饮食目标、过敏和忌口档案。",
                   EmptyArguments, "user.diet.profile.get", allowed_roles=CUSTOMER_ONLY),
    ToolDefinition("recommend_personalized_meals",
                   "仅从实时可售且具备已审核营养数据的菜品中执行结构化安全过滤与个性化推荐。",
                   RecommendMealsArguments, "user.diet.recommend", allowed_roles=CUSTOMER_ONLY,
                   timeout_seconds=15.0),
)
