from app.tools.definitions.common import DateRangeArguments
from app.tools.models import EmptyArguments, ToolDefinition


DEFINITIONS = (
    ToolDefinition(
        "get_business_overview", "查询今日营业、订单、菜品和套餐概览。",
        EmptyArguments, "workspace.overview",
    ),
    ToolDefinition(
        "get_business_report", "按时间范围查询营业额、用户、订单和销量统计。",
        DateRangeArguments, "report.query",
    ),
)
