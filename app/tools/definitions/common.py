from datetime import datetime

from pydantic import Field, model_validator

from app.tools.models import ToolArguments


class PageArguments(ToolArguments):
    page: int = Field(default=1, ge=1, description="页码")
    page_size: int = Field(default=20, ge=1, le=50, description="每页数量，最多50")


class DateRangeArguments(ToolArguments):
    begin_time: datetime = Field(description="查询开始时间，ISO 8601格式")
    end_time: datetime = Field(description="查询结束时间，ISO 8601格式")

    @model_validator(mode="after")
    def validate_range(self):
        if self.end_time < self.begin_time:
            raise ValueError("end_time must not be before begin_time")
        if (self.end_time - self.begin_time).days > 366:
            raise ValueError("date range must not exceed 366 days")
        return self
