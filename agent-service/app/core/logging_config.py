"""应用统一 JSON 日志配置。"""
import json
import logging
from datetime import datetime, timezone


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "service": "sky-agent",
            "level": record.levelname,
            "logger": record.name,
            "event": record.getMessage(),
        }
        for field in (
            "environment", "trace_id", "task_id", "session_id", "provider",
            "model", "actual_model", "stream", "status", "error_type",
            "elapsed_ms", "first_token_ms", "prompt_tokens",
            "completion_tokens", "fallback_used", "message_count",
            "prompt_chars", "output_chars",
        ):
            value = getattr(record, field, None)
            if value is not None:
                payload[field] = value
        if record.exc_info:
            payload["exception_type"] = record.exc_info[0].__name__
        return json.dumps(payload, ensure_ascii=False)


def configure_logging(level: str) -> None:
    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter())
    root = logging.getLogger()
    root.handlers.clear()
    root.addHandler(handler)
    root.setLevel(level.upper())
    # httpx 会为确认状态的周期轮询逐条打印 INFO，既无诊断价值又会淹没业务日志。
    # HTTP 异常仍由调用层转换为结构化工具错误并记录。
    logging.getLogger("httpx").setLevel(logging.WARNING)
