from app.tools.definitions.dish import DEFINITIONS as DISH_DEFINITIONS
from app.tools.definitions.employee import DEFINITIONS as EMPLOYEE_DEFINITIONS
from app.tools.definitions.knowledge import DEFINITIONS as KNOWLEDGE_DEFINITIONS
from app.tools.definitions.order import DEFINITIONS as ORDER_DEFINITIONS
from app.tools.definitions.report import DEFINITIONS as REPORT_DEFINITIONS
from app.tools.definitions.setmeal import DEFINITIONS as SETMEAL_DEFINITIONS
from app.tools.definitions.shop import DEFINITIONS as SHOP_DEFINITIONS
from app.tools.definitions.user import DEFINITIONS as USER_DEFINITIONS
from app.tools.registry import ToolRegistry
from app.tools.models import ActorRole


ALL_DEFINITIONS = (
    *EMPLOYEE_DEFINITIONS,
    *KNOWLEDGE_DEFINITIONS,
    *ORDER_DEFINITIONS,
    *DISH_DEFINITIONS,
    *SETMEAL_DEFINITIONS,
    *SHOP_DEFINITIONS,
    *REPORT_DEFINITIONS,
    *USER_DEFINITIONS,
)


def build_default_registry() -> ToolRegistry:
    return ToolRegistry(ALL_DEFINITIONS)


def build_admin_registry() -> ToolRegistry:
    """构建管理端专属目录，物理排除所有普通用户工具。"""
    return ToolRegistry((
        *EMPLOYEE_DEFINITIONS, *KNOWLEDGE_DEFINITIONS, *ORDER_DEFINITIONS,
        *DISH_DEFINITIONS, *SETMEAL_DEFINITIONS, *SHOP_DEFINITIONS,
        *REPORT_DEFINITIONS,
    ))


def build_user_registry() -> ToolRegistry:
    """构建用户端专属目录，模型无法发现任何管理工具。"""
    user_shop = tuple(
        definition for definition in SHOP_DEFINITIONS
        if ActorRole.CUSTOMER in definition.allowed_roles
    )
    return ToolRegistry((*user_shop, *USER_DEFINITIONS))
