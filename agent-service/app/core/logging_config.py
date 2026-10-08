"""应用统一 JSON 日志配置。"""
import json
import logging
import re
from datetime import datetime, timezone


def redact_log_text(value: str) -> str:
    """遮盖日志事件和额外文本字段中的凭据、手机号及身份证号，不修改业务数据。"""
    value = re.sub(r"(?i)Bearer\s+[^\s\"',;]+", "Bearer [REDACTED]", value)
    value = re.sub(
        r'''(?i)(["']?(?:password|passwd|api[_-]?key|access[_-]?token|refresh[_-]?token|token|secret|authorization|cookie)["']?\s*[:=]\s*)(?:"[^"]*"|'[^']*'|[^\s,;)}]+)''',
        r"\1[REDACTED]", value,
    )
    value = re.sub(r"eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+", "[REDACTED]", value)
    value = re.sub(r"(?<![A-Za-z0-9])(?:sk-[A-Za-z0-9_-]{20,}|AKIA[A-Z0-9]{16})", "[REDACTED]", value)
    return re.sub(r"(?<!\d)(?:[1-9]\d{16}[\dXx]|1[3-9]\d{9})(?!\d)", "[REDACTED]", value)


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "service": "sky-agent",
            "level": record.levelname,
            "logger": record.name,
            "event": redact_log_text(record.getMessage()),
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
                payload[field] = redact_log_text(value) if isinstance(value, str) else value
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
