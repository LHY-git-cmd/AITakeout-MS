"""Agent 配置：环境变量优先，项目根目录 .env 仅供本地开发。"""
from urllib.parse import urlparse

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="ignore",
    )

    APP_NAME: str = "Python Agent Service"
    APP_VERSION: str = "1.0.0"
    APP_ENV: str = "development"
    DEBUG: bool = False
    HOST: str = "0.0.0.0"
    PORT: int = 8000
    LOG_LEVEL: str = "INFO"
    TASK_STATE_DB: str = "data/agent_tasks.sqlite3"
    KNOWLEDGE_STATE_DB: str = "data/knowledge_tasks.sqlite3"
    VECTOR_STORE: str = "qdrant"
    QDRANT_URL: str = "http://localhost:6333"
    QDRANT_COLLECTION: str = "sky_knowledge_v2"
    RAG_SCORE_THRESHOLD: float = 0.2
    EMBEDDING_BASE_URL: str = "http://localhost:8001"
    EMBEDDING_MODEL: str = "BAAI/bge-m3"
    EMBEDDING_DIMENSION: int = 1024
    EMBEDDING_BATCH_SIZE: int = 16
    KNOWLEDGE_SOURCE_PATH: str = "data/knowledge_sources"
    KNOWLEDGE_MAX_FILE_SIZE: int = 20 * 1024 * 1024
    AGENT_MAX_CONTEXT_CHARS: int = 24000
    JAVA_INTERNAL_BASE_URL: str = "http://localhost:8080"
    AGENT_INTERNAL_SERVICE_TOKEN: str = ""
    TOOL_MAX_ROUNDS: int = 6
    TOOL_MAX_CALLS_PER_ROUND: int = 4
    TOOL_CONFIRMATION_TIMEOUT_SECONDS: float = 300.0
    TOOL_CONFIRMATION_POLL_SECONDS: float = 3.0

    # 所有 OpenAI 兼容供应商统一使用以下四个变量。
    LLM_PROVIDER: str = ""
    LLM_BASE_URL: str = ""
    LLM_MODEL: str = ""
    LLM_API_KEY: str = ""
    LLM_FALLBACK_MODEL: str = ""
    LLM_ALLOWED_MODELS: str = ""
    LLM_TOTAL_TIMEOUT_SECONDS: float = 90.0
    LLM_CONNECT_TIMEOUT_SECONDS: float = 5.0
    LLM_FIRST_TOKEN_TIMEOUT_SECONDS: float = 15.0
    LLM_MAX_CONCURRENCY: int = 4
    LLM_MAX_QUEUE_SIZE: int = 16
    LLM_MAX_RETRIES: int = 2
    LLM_RETRY_BASE_DELAY_SECONDS: float = 0.5
    LLM_INPUT_COST_PER_MILLION: float = 0.0
    LLM_OUTPUT_COST_PER_MILLION: float = 0.0
    LLM_TEMPERATURE_MIN: float = 0.0
    LLM_TEMPERATURE_MAX: float = 0.3
    LLM_THINKING_ENABLED: bool = False

    @property
    def allowed_models(self) -> set[str]:
        values = [item.strip() for item in self.LLM_ALLOWED_MODELS.split(",") if item.strip()]
        return set(values or [self.LLM_MODEL])

    def validate_startup(self) -> None:
        """在服务启动时一次性报告关键配置错误，不打印配置值。"""
        errors: list[str] = []
        for name in ("LLM_PROVIDER", "LLM_BASE_URL", "LLM_MODEL", "LLM_API_KEY",
                     "AGENT_INTERNAL_SERVICE_TOKEN"):
            if not getattr(self, name).strip():
                errors.append(f"{name} is required")

        for name in ("LLM_BASE_URL", "QDRANT_URL", "EMBEDDING_BASE_URL",
                     "JAVA_INTERNAL_BASE_URL"):
            value = getattr(self, name).strip()
            parsed = urlparse(value)
            if value and (parsed.scheme not in {"http", "https"} or not parsed.netloc):
                errors.append(f"{name} must be an absolute http(s) URL")

        if self.VECTOR_STORE not in {"qdrant", "memory"}:
            errors.append("VECTOR_STORE must be 'qdrant' or 'memory'")
        if self.EMBEDDING_DIMENSION <= 0 or self.EMBEDDING_BATCH_SIZE <= 0:
            errors.append("embedding dimension and batch size must be positive")
        if self.AGENT_MAX_CONTEXT_CHARS <= 0:
            errors.append("AGENT_MAX_CONTEXT_CHARS must be positive")
        if len(self.AGENT_INTERNAL_SERVICE_TOKEN) < 32:
            errors.append("AGENT_INTERNAL_SERVICE_TOKEN must contain at least 32 characters")
        if self.LLM_MAX_CONCURRENCY <= 0 or self.LLM_MAX_RETRIES < 0:
            errors.append("LLM concurrency must be positive and retries non-negative")
        if self.LLM_MAX_QUEUE_SIZE < 0:
            errors.append("LLM_MAX_QUEUE_SIZE must be non-negative")
        if self.TOOL_MAX_ROUNDS <= 0 or self.TOOL_MAX_CALLS_PER_ROUND <= 0:
            errors.append("tool round and call limits must be positive")
        if (self.TOOL_CONFIRMATION_TIMEOUT_SECONDS <= 0
                or self.TOOL_CONFIRMATION_POLL_SECONDS <= 0):
            errors.append("tool confirmation timeouts must be positive")
        if (self.LLM_TOTAL_TIMEOUT_SECONDS <= 0
                or self.LLM_CONNECT_TIMEOUT_SECONDS <= 0
                or self.LLM_FIRST_TOKEN_TIMEOUT_SECONDS <= 0
                or self.LLM_FIRST_TOKEN_TIMEOUT_SECONDS > self.LLM_TOTAL_TIMEOUT_SECONDS):
            errors.append("LLM timeouts must be positive and first-token timeout <= total timeout")
        if self.LLM_FALLBACK_MODEL and self.LLM_FALLBACK_MODEL not in self.allowed_models:
            errors.append("LLM_FALLBACK_MODEL must be included in LLM_ALLOWED_MODELS")
        if not (0 <= self.LLM_TEMPERATURE_MIN <= self.LLM_TEMPERATURE_MAX <= 2):
            errors.append("LLM temperature bounds must satisfy 0 <= min <= max <= 2")

        if errors:
            raise RuntimeError("invalid Agent configuration: " + "; ".join(errors))


settings = Settings()
