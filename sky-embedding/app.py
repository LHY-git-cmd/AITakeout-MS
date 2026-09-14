# 导入必要的库
import asyncio
import logging
import json
import os
import time
from contextlib import asynccontextmanager
from pathlib import Path
from collections import Counter

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import Response
from pydantic import BaseModel, Field


# --- 全局配置 ---
# 从环境变量中获取配置，并提供默认值

# 使用的模型名称，默认为 "BAAI/bge-m3"
MODEL_NAME = os.getenv("EMBEDDING_MODEL", "BAAI/bge-m3")
# 模型文件所在的路径
MODEL_PATH = os.getenv(
    "EMBEDDING_MODEL_PATH",
    str(Path(__file__).parent / "models" / "bge-m3"),
)
# 是否使用 FP16 半精度浮点数进行推理，可以提升速度，但会略微降低精度
USE_FP16 = os.getenv("EMBEDDING_USE_FP16", "false").lower() == "true"
# 推理设备，可以是 "cpu" 或 "cuda"
DEVICE = os.getenv("EMBEDDING_DEVICE", "cpu")
# 每批次处理的最大文本数量
MAX_BATCH_SIZE = int(os.getenv("EMBEDDING_MAX_BATCH_SIZE", "16"))
# BGE-M3 模型的输出向量维度
DIMENSION = 1024


class JsonFormatter(logging.Formatter):
    def format(self, record):
        payload = {
            "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "service": "sky-embedding",
            "level": record.levelname,
            "event": record.getMessage(),
        }
        for field in (
            "trace_id", "task_id", "session_id", "batch_size", "elapsed_ms",
            "status", "error_type",
        ):
            value = getattr(record, field, None)
            if value is not None:
                payload[field] = value
        return json.dumps(payload, ensure_ascii=False)


_handler = logging.StreamHandler()
_handler.setFormatter(JsonFormatter())
logging.getLogger().handlers.clear()
logging.getLogger().addHandler(_handler)
logging.getLogger().setLevel(os.getenv("LOG_LEVEL", "INFO").upper())


# --- API 数据模型 ---

# 定义 embedding 请求的 body 结构
class EmbeddingRequest(BaseModel):
    # 输入的文本，可以是一个字符串或一个字符串列表
    input: str | list[str]
    # 请求使用的模型名称，默认为全局配置的 MODEL_NAME
    model: str = MODEL_NAME


# --- 应用状态管理 ---

# 定义一个类来管理应用的全局状态
class EmbeddingState:
    model = None  # 用于存放加载后的 embedding 模型
    lock: asyncio.Lock  # 异步锁，用于保证并发请求时模型推理的线程安全
    started_at: float  # 应用启动时间的时间戳
    status: str = "starting"
    error_type: str | None = None
    load_task: asyncio.Task | None = None
    request_count: int = 0
    failure_count: int = 0
    inference_seconds: float = 0.0
    input_count: int = 0


# 创建全局状态实例
state = EmbeddingState()
http_requests = Counter()
http_latency_seconds = 0.0


# --- 模型加载 ---


def load_model():
    """
    加载 embedding 模型。
    """
    # 动态导入 FlagEmbedding 库，避免在不需要时过早加载
    from FlagEmbedding import BGEM3FlagModel

    # 检查模型路径是否存在
    if not Path(MODEL_PATH).is_dir():
        raise RuntimeError(f"embedding model directory does not exist: {MODEL_PATH}")

    # 初始化并返回 BGEM3FlagModel 模型实例
    return BGEM3FlagModel(
        MODEL_PATH,
        use_fp16=USE_FP16,  # 是否使用 FP16
        devices=DEVICE,  # 使用的设备
    )


async def load_model_in_background():
    """后台加载模型，使健康端点能报告 starting/failed 状态。"""
    try:
        state.model = await asyncio.to_thread(load_model)
        state.status = "healthy"
        logging.getLogger(__name__).info("Embedding model loaded")
    except Exception as exc:
        state.status = "failed"
        state.error_type = type(exc).__name__
        logging.getLogger(__name__).exception(
            "Embedding model load failed: error_type=%s", state.error_type)


# --- FastAPI 应用生命周期管理 ---


@asynccontextmanager
async def lifespan(_: FastAPI):
    """
    FastAPI 应用的生命周期函数，在应用启动和关闭时执行。
    """
    # --- 应用启动时 ---
    state.lock = asyncio.Lock()  # 初始化异步锁
    state.started_at = time.time()  # 记录启动时间
    state.status = "starting"
    state.error_type = None
    state.load_task = asyncio.create_task(load_model_in_background())

    yield  # 在此等待应用运行

    # --- 应用关闭时 ---
    if state.load_task and not state.load_task.done():
        state.load_task.cancel()
    state.model = None  # 释放模型资源


# --- FastAPI 应用实例 ---

# 创建 FastAPI 应用实例，并配置标题、版本和生命周期函数
app = FastAPI(title="Sky BGE-M3 Embedding Service", version="1.0.0", lifespan=lifespan)


@app.middleware("http")
async def observe_http(request: Request, call_next):
    global http_latency_seconds
    started_at = time.perf_counter()
    try:
        response = await call_next(request)
    except Exception:
        http_requests[(request.method, 500)] += 1
        http_latency_seconds += time.perf_counter() - started_at
        raise
    http_requests[(request.method, response.status_code)] += 1
    http_latency_seconds += time.perf_counter() - started_at
    return response


# --- API 端点 ---


@app.get("/health")
async def health():
    """
    健康检查接口，返回服务的状态信息。
    """
    return {
        "status": state.status,
        "model": MODEL_NAME,
        "dimension": DIMENSION,
        "device": DEVICE,
        "uptime_seconds": time.time() - state.started_at,
    }


@app.get("/health/live")
async def liveness():
    return {"status": "alive"}


@app.get("/health/ready")
async def readiness():
    if state.status != "healthy" or state.model is None:
        from fastapi.responses import JSONResponse
        return JSONResponse(
            status_code=503,
            content={"status": state.status, "error_type": state.error_type},
        )
    return {"status": "healthy", "model": MODEL_NAME, "dimension": DIMENSION}


@app.post("/v1/embeddings")
async def embeddings(body: EmbeddingRequest, request: Request):
    """
    核心的 embedding 接口，接收文本并返回其向量表示。
    """
    if state.model is None:
        raise HTTPException(status_code=503, detail=f"model is {state.status}")

    # 检查请求中的模型名称是否与服务加载的模型一致
    if body.model != MODEL_NAME:
        raise HTTPException(status_code=400, detail=f"unsupported model: {body.model}")

    # 将输入统一处理为文本列表
    texts = [body.input] if isinstance(body.input, str) else body.input

    # 对输入进行校验
    if not texts or len(texts) > MAX_BATCH_SIZE or any(not text.strip() for text in texts):
        raise HTTPException(
            status_code=400,
            detail=f"input must contain 1 to {MAX_BATCH_SIZE} non-blank texts",
        )

    # 使用异步锁保证线程安全
    trace_id = request.headers.get("X-Trace-ID")
    task_id = request.headers.get("X-Task-ID")
    session_id = request.headers.get("X-Session-ID")
    started_at = time.perf_counter()
    state.request_count += 1
    state.input_count += len(texts)
    try:
        async with state.lock:
            result = await asyncio.to_thread(
                state.model.encode,
                texts,
                batch_size=MAX_BATCH_SIZE,
                max_length=8192,
                return_dense=True,
                return_sparse=False,
                return_colbert_vecs=False,
            )
    except Exception as exc:
        state.failure_count += 1
        logging.getLogger(__name__).exception("embedding_request", extra={
            "trace_id": trace_id, "task_id": task_id, "session_id": session_id,
            "batch_size": len(texts), "status": "failed",
            "error_type": type(exc).__name__})
        raise
    elapsed = time.perf_counter() - started_at
    state.inference_seconds += elapsed
    logging.getLogger(__name__).info("embedding_request", extra={
        "trace_id": trace_id, "task_id": task_id, "session_id": session_id,
        "batch_size": len(texts), "status": "completed",
        "elapsed_ms": round(elapsed * 1000)})

    # 从结果中提取 dense vector 并转换为列表
    vectors = result["dense_vecs"].tolist()

    # 按照 OpenAI 的格式构建并返回响应
    return {
        "object": "list",
        "model": MODEL_NAME,
        "data": [
            {"object": "embedding", "index": index, "embedding": vector}
            for index, vector in enumerate(vectors)
        ],
        "usage": {
            # 这里简单地用字符数作为 token 数量的估算
            "prompt_tokens": sum(len(text) for text in texts),
            "total_tokens": sum(len(text) for text in texts),
        },
    }


@app.get("/metrics", include_in_schema=False)
async def metrics():
    payload = "\n".join([
        "# TYPE sky_embedding_requests_total counter",
        f"sky_embedding_requests_total {state.request_count}",
        "# TYPE sky_embedding_failures_total counter",
        f"sky_embedding_failures_total {state.failure_count}",
        "# TYPE sky_embedding_inputs_total counter",
        f"sky_embedding_inputs_total {state.input_count}",
        "# TYPE sky_embedding_inference_seconds_total counter",
        f"sky_embedding_inference_seconds_total {state.inference_seconds}",
        "# TYPE sky_embedding_model_ready gauge",
        f'sky_embedding_model_ready{{model="{MODEL_NAME}",device="{DEVICE}"}} {1 if state.status == "healthy" else 0}',
        "",
    ])
    payload += "# TYPE sky_embedding_http_requests_total counter\n"
    for (method, status), count in http_requests.items():
        payload += f'sky_embedding_http_requests_total{{method="{method}",status="{status}"}} {count}\n'
    payload += "# TYPE sky_embedding_http_request_duration_seconds_total counter\n"
    payload += f"sky_embedding_http_request_duration_seconds_total {http_latency_seconds}\n"
    return Response(payload, media_type="text/plain; version=0.0.4; charset=utf-8")
