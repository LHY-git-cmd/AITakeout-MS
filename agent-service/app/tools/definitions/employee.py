from pydantic import Field

from app.tools.definitions.common import PageArguments
from app.tools.models import ToolArguments, ToolDefinition


class QueryEmployeesArguments(PageArguments):
    name: str | None = Field(default=None, max_length=64, description="员工姓名关键字")


class GetEmployeeArguments(ToolArguments):
    employee_id: int = Field(gt=0, description="员工ID")


DEFINITIONS = (
    ToolDefinition(
        name="query_employees",
        description="分页查询员工列表。结果不包含密码，手机号和身份证号将脱敏。",
        arguments_model=QueryEmployeesArguments,
        operation="employee.query",
    ),
    ToolDefinition(
        name="get_employee_detail",
        description="查询单个员工的基础信息。结果不包含密码，手机号和身份证号将脱敏。",
        arguments_model=GetEmployeeArguments,
        operation="employee.detail",
    ),
)
