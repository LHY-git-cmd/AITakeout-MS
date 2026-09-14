from app.tools.definitions.dish import DEFINITIONS as DISH_DEFINITIONS
from app.tools.definitions.employee import DEFINITIONS as EMPLOYEE_DEFINITIONS
from app.tools.definitions.knowledge import DEFINITIONS as KNOWLEDGE_DEFINITIONS
from app.tools.definitions.order import DEFINITIONS as ORDER_DEFINITIONS
from app.tools.definitions.report import DEFINITIONS as REPORT_DEFINITIONS
from app.tools.definitions.setmeal import DEFINITIONS as SETMEAL_DEFINITIONS
from app.tools.definitions.shop import DEFINITIONS as SHOP_DEFINITIONS
from app.tools.registry import ToolRegistry


ALL_DEFINITIONS = (
    *EMPLOYEE_DEFINITIONS,
    *KNOWLEDGE_DEFINITIONS,
    *ORDER_DEFINITIONS,
    *DISH_DEFINITIONS,
    *SETMEAL_DEFINITIONS,
    *SHOP_DEFINITIONS,
    *REPORT_DEFINITIONS,
)


def build_default_registry() -> ToolRegistry:
    return ToolRegistry(ALL_DEFINITIONS)
