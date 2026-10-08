from decimal import Decimal
from typing import Literal

from pydantic import Field, model_validator

from app.tools.definitions.common import PageArguments
from app.tools.models import EmptyArguments, ToolAccess, ToolArguments, ToolDefinition


# 仅作用于管理端：测试意图确认与执行确认卡片是两个不同阶段。
DISH_CREATION_INSTRUCTION = (
    "添加菜品流程：管理员只说‘帮我添加一些菜品’而没有具体要求时，必须先询问‘是否生成测试数据？’，"
    "等待明确回答，不能推定同意、不能调用create_dishes。若拒绝测试，说明需要提供菜品名称、售价、"
    "分类及实际描述/食材/过敏原/营养资料，逐项追问缺失的必要字段，禁止编造正式配方。"
    "明确同意测试（包括上下文回答‘是’）后，先query_dish_categories查询真实启用分类；"
    "没有分类时请管理员先创建分类并停止添加，不得虚构分类ID或自动建分类。"
    "有分类时随机设计恰好三道不同菜品，提供名字、价格、分类、描述、食材、过敏原声明和完整每份营养："
    "serving_size_g、energy_kcal、protein_g、fat_g、carbohydrate_g、dietary_fiber_g、sugar_g、sodium_mg。"
    "食材使用标准code/name/amount_g；过敏原用code/status/source_reference，未知声明使用UNKNOWN，"
    "生成值仅是测试模拟，不得保证真实无过敏原。source_type必须SIMULATED并注明测试来源。"
    "图片image可不填，不得编造图片地址；口味可不填。调用create_dishes(test_data=true,dishes=三道菜品)"
    "由界面展示确认卡片，禁止只在聊天中列菜品而不调用工具。具体正式信息完整时先查询真实分类，"
    "再调用create_dishes(test_data=false)，只保存实际提供的资料，营养缺省不编造。"
    "新增菜品均默认停售，营养待审核，管理员后续上传图片、核实资料并起售。"
)


class IngredientArguments(ToolArguments):
    code: str = Field(min_length=1, max_length=64)
    name: str = Field(min_length=1, max_length=64)
    amount_g: Decimal | None = Field(default=None, ge=0, max_digits=10, decimal_places=2)
    role_type: Literal["PRIMARY", "SECONDARY", "SEASONING"] = "PRIMARY"
    replaceable: bool = False


class AllergenArguments(ToolArguments):
    code: str = Field(min_length=1, max_length=64)
    status: Literal["FREE", "CONTAINS", "MAY_CONTAIN", "CROSS_CONTACT_RISK", "UNKNOWN"]
    source_reference: str = Field(min_length=1, max_length=500)


class NutritionArguments(ToolArguments):
    profile_version: int = Field(default=1, ge=1)
    recipe_version: int = Field(default=1, ge=1)
    serving_size_g: Decimal = Field(gt=0, max_digits=10, decimal_places=2)
    energy_kcal: Decimal | None = Field(default=None, ge=0, max_digits=10, decimal_places=2)
    protein_g: Decimal | None = Field(default=None, ge=0, max_digits=10, decimal_places=2)
    fat_g: Decimal | None = Field(default=None, ge=0, max_digits=10, decimal_places=2)
    carbohydrate_g: Decimal | None = Field(default=None, ge=0, max_digits=10, decimal_places=2)
    dietary_fiber_g: Decimal | None = Field(default=None, ge=0, max_digits=10, decimal_places=2)
    sugar_g: Decimal | None = Field(default=None, ge=0, max_digits=10, decimal_places=2)
    sodium_mg: Decimal | None = Field(default=None, ge=0, max_digits=10, decimal_places=2)
    purine_mg: Decimal | None = Field(default=None, ge=0, max_digits=10, decimal_places=2)
    source_type: str = Field(min_length=1, max_length=32)
    source_reference: str = Field(min_length=1, max_length=500)
    calculation_method: str | None = Field(default=None, max_length=128)
    uncertainty_note: str | None = Field(default=None, max_length=500)
    ingredients: list[IngredientArguments] = Field(default_factory=list, max_length=50)
    allergens: list[AllergenArguments] = Field(default_factory=list, max_length=50)

    @model_validator(mode="after")
    def unique_allergens(self):
        codes = [a.code.strip().upper() for a in self.allergens]
        if len(codes) != len(set(codes)):
            raise ValueError("allergen codes must be unique")
        return self


class FlavorArguments(ToolArguments):
    name: str = Field(min_length=1, max_length=32)
    value: str = Field(min_length=1, max_length=255, description="口味选项的JSON数组字符串")


class CreateDishArguments(ToolArguments):
    name: str = Field(min_length=1, max_length=64)
    category_id: int = Field(gt=0, description="query_dish_categories返回的真实启用菜品分类ID")
    price: Decimal = Field(gt=0, max_digits=10, decimal_places=2, description="售价，元")
    image: str | None = Field(default=None, max_length=255)
    description: str | None = Field(default=None, max_length=255)
    flavors: list[FlavorArguments] = Field(default_factory=list, max_length=20)
    nutrition: NutritionArguments | None = None


class CreateDishesArguments(ToolArguments):
    test_data: bool = Field(strict=True, description="必须由管理员明确同意生成测试数据后设为true")
    dishes: list[CreateDishArguments] = Field(min_length=1, max_length=10)

    @model_validator(mode="after")
    def validate_batch(self):
        names = [dish.name.strip().casefold() for dish in self.dishes]
        if not all(names) or len(names) != len(set(names)):
            raise ValueError("dish names must be nonblank and unique")
        if self.test_data:
            if len(self.dishes) != 3:
                raise ValueError("test data must contain exactly three dishes")
            for dish in self.dishes:
                n = dish.nutrition
                if (n is None or n.source_type != "SIMULATED" or not n.ingredients or not n.allergens
                    or any(getattr(n, field) is None for field in (
                        "energy_kcal", "protein_g", "fat_g", "carbohydrate_g", "dietary_fiber_g", "sugar_g", "sodium_mg"))):
                    raise ValueError("test dishes require complete simulated nutrition, ingredients and allergens")
                if not dish.description or not dish.description.strip():
                    raise ValueError("test dishes require a description")
        return self


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
    ToolDefinition("query_dish_categories", "查询真实启用的菜品分类；添加前必查，无分类时先询问管理员。",
                   EmptyArguments, "dish.category.query"),
    ToolDefinition("create_dishes", "添加正式菜品或管理员同意后生成的三道测试菜品。默认停售，图片可后补，营养待审核；必须经确认卡片后保存。",
                   CreateDishesArguments, "dish.create", ToolAccess.WRITE, requires_confirmation=True),
    ToolDefinition("query_dishes", "分页查询菜品。", QueryDishesArguments, "dish.query"),
    ToolDefinition("get_dish_detail", "查询菜品详情和口味。", GetDishDetailArguments,
                   "dish.detail"),
    ToolDefinition(
        "update_dish", "修改菜品信息或启停状态。执行前必须由管理员确认。",
        UpdateDishArguments, "dish.update", ToolAccess.WRITE, requires_confirmation=True,
    ),
)
