"""
FastAPI 应用入口模块
负责：
  - 创建 FastAPI 实例
  - 注册中间件（CORS 等）
  - 注册路由
  - 定义应用生命周期（lifespan）

启动方式：python run.py
"""
import logging

import httpx
from fastapi import FastAPI, Request
from fastapi.responses import Response
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
from app.core.config import settings
from app.core.agent import PythonAgent
from app.core.task_queue import TaskQueue
from app.api.routes import router
from app.models.schemas import (
    DependencyHealth,
    HealthResponse,
    LLMHealthSummary,
    ReadinessResponse,
)
from app.knowledge.embedding import HttpEmbeddingProvider
from app.knowledge.vector_store import MemoryVectorStore, QdrantVectorStore
from app.knowledge.service import KnowledgeService
from app.llm.metrics import llm_metrics
from app.core.logging_config import configure_logging
from app.core.trace import current_trace_id
import uuid
import time
from collections import Counter
from datetime import datetime, timedelta, timezone

http_requests = Counter()
http_latency_seconds = 0.0
# 实际问答连续失败后的冷却窗口；到期后允许管理员手动发起真实恢复验证。
LLM_FAILURE_COOLDOWN_SECONDS = 300


def _utc_now() -> datetime:
    """提供可在确定性健康测试中替换的 UTC 时钟。"""
    return datetime.now(timezone.utc)

configure_logging(settings.LOG_LEVEL)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    应用生命周期管理器
    FastAPI 启动时执行 yield 之前的代码（初始化）
    FastAPI 关闭时执行 yield 之后的代码（清理）
    """
    # ---------- 启动阶段 ----------
    settings.validate_startup()
    embedding = HttpEmbeddingProvider(
        settings.EMBEDDING_BASE_URL,
        settings.EMBEDDING_MODEL,
        settings.EMBEDDING_DIMENSION,
        settings.EMBEDDING_BATCH_SIZE,
    )
    if settings.VECTOR_STORE == "memory":
        vector_store = MemoryVectorStore()
    else:
        vector_store = QdrantVectorStore(
            settings.QDRANT_URL,
            settings.QDRANT_COLLECTION,
            embedding.dimension,
        )
    knowledge_service = KnowledgeService(
        embedding,
        vector_store,
        settings.KNOWLEDGE_STATE_DB,
        settings.KNOWLEDGE_SOURCE_PATH,
    )
    await knowledge_service.start()
    agent = PythonAgent(knowledge_service)
    await agent.initialize()        # 初始化（加载模型、连接数据库等）
    task_queue = TaskQueue(agent, settings.TASK_STATE_DB)
    await task_queue.start()
    app.state.agent = agent        # 将 Agent 存入 app.state，供路由层获取
    app.state.task_queue = task_queue
    app.state.knowledge_service = knowledge_service
    app.state.settings = settings
    # 启动配置验证已经通过；readiness 不会为健康检查额外调用付费模型。
    app.state.llm_configured = True
    logging.getLogger(__name__).info(
        "Agent service ready: environment=%s port=%s llm_provider=%s llm_model=%s",
        settings.APP_ENV,
        settings.PORT,
        settings.LLM_PROVIDER,
        settings.LLM_MODEL,
    )

    yield  # ---------- 运行阶段：服务正常对外提供 ----------

    # ---------- 关闭阶段 ----------
    await task_queue.shutdown()
    await knowledge_service.shutdown()
    logging.getLogger(__name__).info("Agent service stopped")


# 创建 FastAPI 应用实例
app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    lifespan=lifespan,
    description="Python Agent 微服务 —— 供 Java 应用通过 HTTP 调用"
)


@app.middleware("http")
async def trace_middleware(request: Request, call_next):
    global http_latency_seconds
    started_at = time.perf_counter()
    supplied = request.headers.get("X-Trace-ID", "")
    trace_id = supplied if supplied and len(supplied) <= 64 else uuid.uuid4().hex
    token = current_trace_id.set(trace_id)
    try:
        try:
            response = await call_next(request)
        except Exception:
            http_requests[(request.method, 500)] += 1
            http_latency_seconds += time.perf_counter() - started_at
            raise
        http_requests[(request.method, response.status_code)] += 1
        http_latency_seconds += time.perf_counter() - started_at
        response.headers["X-Trace-ID"] = trace_id
        return response
    finally:
        current_trace_id.reset(token)

# 注册 CORS 中间件
# 生产环境建议将 allow_origins 改为具体的域名白名单
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],       # 允许所有来源（跨域）
    allow_credentials=True,    # 允许携带 Cookie / Authorization
    allow_methods=["*"],       # 允许所有 HTTP 方法
    allow_headers=["*"],      # 允许所有请求头
)

# 注册业务路由（/api/v1/*）
app.include_router(router)


# ---------- 根路径 ----------
@app.get("/", summary="Root")
async def root():
    """根路径：返回服务基本信息和所有接口列表"""
    return {
        "service": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "docs": "/docs",  # Swagger UI 地址
        "endpoints": {
            "sync_query": "/api/v1/agent/query",
            "stream_query": "/api/v1/agent/stream",
            "submit_task": "/api/v1/agent/submit",
            "task_sse_stream": "/api/v1/agent/stream/{task_id}",
            "task_status": "/api/v1/agent/status/{task_id}",
            "health": "/health"
        }
    }


# ---------- 健康检查 ----------
@app.get("/health", response_model=HealthResponse, summary="健康检查")
async def health_check(request: Request):
    """
    健康检查接口
    部署时供注册中心（Nacos / Eureka）或负载均衡器（Nginx）定期探活
    """
    agent = request.app.state.agent
    return HealthResponse(
        version=settings.APP_VERSION,
        uptime_seconds=agent.get_uptime()
    )


@app.get("/health/live", summary="存活检查")
async def liveness():
    return {"status": "alive", "service": settings.APP_NAME}


@app.get("/health/ready", response_model=ReadinessResponse, summary="就绪检查")
async def readiness(request: Request):
    """检查初始化和运行依赖，仅返回安全名称、状态和脱敏错误类型。"""
    dependencies: dict[str, DependencyHealth] = {}
    checks = {
        "qdrant": f"{settings.QDRANT_URL.rstrip('/')}/healthz",
        "embedding": f"{settings.EMBEDDING_BASE_URL.rstrip('/')}/health/ready",
    }
    async with httpx.AsyncClient(timeout=3) as client:
        for name, url in checks.items():
            try:
                response = await client.get(url)
                dependencies[name] = DependencyHealth(
                    status="healthy" if response.is_success else "unavailable",
                    error_type=None if response.is_success else f"{name.upper()}_UNAVAILABLE",
                )
            except httpx.HTTPError:
                dependencies[name] = DependencyHealth(
                    status="unavailable", error_type=f"{name.upper()}_UNAVAILABLE")

    agent_ready = getattr(request.app.state, "agent", None) is not None
    dependencies["agent"] = DependencyHealth(
        status="healthy" if agent_ready else "unavailable",
        error_type=None if agent_ready else "AGENT_NOT_INITIALIZED",
    )
    llm = await _llm_readiness_summary(getattr(request.app.state, "llm_configured", False))
    unavailable = next(
        (item.error_type for item in dependencies.values() if item.status == "unavailable"), None)
    startup_dependencies_ready = (
        agent_ready and all(item.status == "healthy" for item in dependencies.values()))
    if not agent_ready:
        status = "offline"
        error_type = "AGENT_UNAVAILABLE"
    elif llm.status == "unavailable":
        status = "offline"
        error_type = llm.error_type
    elif unavailable:
        status = "degraded"
        error_type = unavailable
    elif llm.status == "unknown":
        status = "degraded"
        error_type = None
    else:
        status = "online"
        error_type = None

    payload = ReadinessResponse(
        service=settings.APP_NAME,
        status=status,
        checked_at=datetime.now(timezone.utc),
        dependencies=dependencies,
        llm=llm,
        error_type=error_type,
    ).model_dump(mode="json")
    # HTTP readiness只代表服务能否接受请求，页面能力状态单独用 status 表达。
    if not startup_dependencies_ready:
        from fastapi.responses import JSONResponse
        return JSONResponse(status_code=503, content=payload)
    return payload


async def _llm_readiness_summary(configured: bool) -> LLMHealthSummary:
    """从启动配置与已有指标派生 LLM 状态，绝不因轮询执行 Prompt。"""
    if not configured:
        return LLMHealthSummary(status="unavailable", error_type="LLM_CONFIGURATION_INVALID")
    recent = (await llm_metrics.snapshot())["recent"]
    if recent["last_status"] is None:
        return LLMHealthSummary(status="unknown")
    if recent["consecutive_failures"] >= 2:
        failed_at = datetime.fromisoformat(recent["last_failure_at"])
        if _utc_now() - failed_at < timedelta(seconds=LLM_FAILURE_COOLDOWN_SECONDS):
            return LLMHealthSummary(status="unavailable", error_type="LLM_CALL_FAILED")
        return LLMHealthSummary(status="unknown")
    return LLMHealthSummary(status="healthy")


@app.get("/metrics/llm", summary="LLM 调用指标")
async def llm_metrics_snapshot():
    """返回进程内指标快照；不包含提示词或对话正文。"""
    return await llm_metrics.snapshot()


@app.get("/metrics/agent", summary="Agent 运行指标")
async def agent_metrics_snapshot(request: Request):
    return {
        "llm": await llm_metrics.snapshot(),
        "queue": await request.app.state.task_queue.metrics_snapshot(),
    }


@app.get("/metrics", include_in_schema=False)
async def prometheus_metrics(request: Request):
    queue = await request.app.state.task_queue.metrics_snapshot()
    payload = await llm_metrics.prometheus()
    payload += "# TYPE sky_agent_tasks gauge\n"
    payload += f'sky_agent_tasks{{status="running"}} {queue["running"]}\n'
    payload += f'sky_agent_tasks{{status="queued"}} {queue["queued"]}\n'
    payload += f'sky_agent_task_capacity {queue["capacity"]}\n'
    payload += "# TYPE sky_agent_http_requests_total counter\n"
    for (method, status), count in http_requests.items():
        payload += f'sky_agent_http_requests_total{{method="{method}",status="{status}"}} {count}\n'
    payload += "# TYPE sky_agent_http_request_duration_seconds_total counter\n"
    payload += f"sky_agent_http_request_duration_seconds_total {http_latency_seconds}\n"
    return Response(payload, media_type="text/plain; version=0.0.4; charset=utf-8")
