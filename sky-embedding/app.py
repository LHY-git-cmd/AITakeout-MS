# 导入必要的库
import asyncio
import os
import time
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException
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


# 创建全局状态实例
state = EmbeddingState()


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


# --- FastAPI 应用生命周期管理 ---


@asynccontextmanager
async def lifespan(_: FastAPI):
    """
    FastAPI 应用的生命周期函数，在应用启动和关闭时执行。
    """
    # --- 应用启动时 ---
    print("INFO:     Loading embedding model...")
    state.lock = asyncio.Lock()  # 初始化异步锁
    state.started_at = time.time()  # 记录启动时间
    # 在一个独立的线程中加载模型，避免阻塞 FastAPI 的主事件循环
    state.model = await asyncio.to_thread(load_model)
    print("INFO:     Embedding model loaded.")

    yield  # 在此等待应用运行

    # --- 应用关闭时 ---
    state.model = None  # 释放模型资源


# --- FastAPI 应用实例 ---

# 创建 FastAPI 应用实例，并配置标题、版本和生命周期函数
app = FastAPI(title="Sky BGE-M3 Embedding Service", version="1.0.0", lifespan=lifespan)


# --- API 端点 ---


@app.get("/health")
async def health():
    """
    健康检查接口，返回服务的状态信息。
    """
    return {
        "status": "healthy" if state.model is not None else "starting",
        "model": MODEL_NAME,
        "dimension": DIMENSION,
        "device": DEVICE,
        "uptime_seconds": time.time() - state.started_at,
    }


@app.post("/v1/embeddings")
async def embeddings(body: EmbeddingRequest):
    """
    核心的 embedding 接口，接收文本并返回其向量表示。
    """
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
    async with state.lock:
        # 在独立的线程中执行模型的 encode 方法，避免阻塞主循环
        result = await asyncio.to_thread(
            state.model.encode,
            texts,
            batch_size=MAX_BATCH_SIZE,
            max_length=8192,  # BGE-M3 支持的最大长度
            return_dense=True,  # 只返回 dense vector
            return_sparse=False,
            return_colbert_vecs=False,
        )

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