"""
API 路由定义模块 —— Submit + SSE Event Stream 版

客户端推荐调用链：
  1) POST /api/v1/agent/submit          → 得到 task_id + stream_url
  2) GET  /api/v1/agent/stream/{task_id} (SSE) → 实时接收事件
     事件类型：task_start / token / task_end

旧接口 /agent/query、/agent/stream、/agent/status/{task_id} 保留兼容。
"""
import json
import logging

import asyncio
from fastapi import APIRouter, File, Form, Header, HTTPException, Query, Request, UploadFile
from fastapi.responses import StreamingResponse
from app.models.schemas import (
    AgentRequest, AgentSubmitRequest, AgentResponse, SubmitResponse,
    TaskStatusResponse, SubmitStreamResponse, KnowledgeIndexRequest, KnowledgeSearchRequest
)
from app.core.config import settings
from app.core.task_queue import TaskCapacityError

# 创建路由器，统一前缀 /api/v1，Swagger 中归类到 "Agent" 标签
router = APIRouter(prefix="/api/v1", tags=["Agent"])

ALLOWED_MODELS = settings.allowed_models
DEFAULT_MODEL = settings.LLM_MODEL

@router.post("/agent/query", response_model=AgentResponse,
             deprecated=True, summary="同步查询 Agent（已废弃）")
async def handle_query(request: Request, body: AgentRequest):
    """
    同步查询接口
    客户端发送请求后，等待 Agent 处理完成并返回完整结果
    适合耗时短（<5s）的场景
    """
    logging.info(
        "Received query request: task_id=%s session_id=%s model=%s query_chars=%s",
        body.task_id, body.session_id, body.model or DEFAULT_MODEL, len(body.query))
    agent = request.app.state.agent  # 从 app.state 获取 lifespan 中初始化的 Agent
    try:
        result = await agent.process(
            query=body.query,
            context=body.context,
            knowledge=body.knowledge,
            model=body.model or DEFAULT_MODEL,
            temperature=body.temperature
        )
        return AgentResponse(status="success", result=result)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/agent/stream", deprecated=True,
             summary="流式查询 Agent（已废弃）")
async def stream_query(request: Request, body: AgentRequest):
    """
    流式查询接口（Server-Sent Events）
    采用 SSE 协议逐块推送结果，前端可实现打字机效果
    Java 端可用 WebClient / OkHttp 流式消费
    """
    logging.info(
        "Received stream query request: task_id=%s session_id=%s model=%s query_chars=%s",
        body.task_id, body.session_id, body.model or DEFAULT_MODEL, len(body.query))
    agent = request.app.state.agent

    async def event_generator():
        """SSE 事件生成器：将 Agent 的流式输出转为 SSE 格式"""
        try:
            rag_context, _, refusal = await agent.prepare_rag(
                body.query, body.context, body.knowledge)
            async for chunk in agent.stream_process(
                query=body.query,
                context=body.context,
                model=body.model or DEFAULT_MODEL,
                temperature=body.temperature,
                rag_context=rag_context,
                refusal=refusal,
            ):
                # SSE 格式：每条消息以 "data: " 开头，以双换行结尾
                yield f"data: {chunk}\n\n"
            # 结束标记
            yield "data: [DONE]\n\n"
        except Exception as e:
            # 异常时通过 SSE 推送错误
            yield f"data: [ERROR] {str(e)}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream"  # SSE 标准 MIME 类型
    )


@router.post("/agent/submit", response_model=SubmitStreamResponse,
             summary="提交异步任务（推荐，配合 SSE 订阅）")
async def submit_task(request: Request, body: AgentSubmitRequest):
    """
    异步任务提交接口（Submit + SSE 模式）

    立即返回 task_id 和 stream_url，Agent 在后台执行。
    客户端应立即连接 stream_url 订阅事件流，而不是轮询 /status。

    事件流中会依次收到：
      - task_start  任务开始执行
      - token       每段 token 产出（可多次）
      - task_end    任务结束（含最终 result 或 error）
    """
    logging.info(
        "Received submit task request: task_id=%s session_id=%s model=%s query_chars=%s",
        body.task_id, body.session_id, body.model or DEFAULT_MODEL, len(body.query))
    queue = request.app.state.task_queue

    model = body.model or DEFAULT_MODEL
    if model not in ALLOWED_MODELS:
        raise HTTPException(status_code=400, detail="不支持的模型")
    if not settings.LLM_TEMPERATURE_MIN <= body.temperature <= settings.LLM_TEMPERATURE_MAX:
        raise HTTPException(status_code=400, detail="模型温度参数超出允许范围")

    try:
        task = await queue.submit(
            task_id=body.task_id,
            trace_id=body.trace_id,
            actor_role=body.actor_role,
            user_id=body.user_id,
            session_id=body.session_id,
            query=body.query,
            context=body.context,
            knowledge=body.knowledge,
            model=model,
            temperature=body.temperature
        )
    except ValueError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc

    # 构造 SSE 订阅地址，前端直接连接即可
    base_url = str(request.base_url).rstrip("/")
    task_id = task["task_id"]
    stream_url = f"{base_url}/api/v1/agent/stream/{task_id}"

    return SubmitStreamResponse(
        task_id=task_id,
        status=task["status"],
        stream_url=stream_url,
        result=task.get("result"),
        error_msg=task.get("error_msg")
    )



@router.get("/agent/status/{task_id}", response_model=TaskStatusResponse,
            summary="查询任务状态")
async def get_task_status(request: Request, task_id: str):
    """
    异步任务状态轮询接口
    客户端拿到 task_id 后，定期调用此接口查询任务进度和结果
    """
    queue = request.app.state.task_queue
    task = await queue.get_status(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    return TaskStatusResponse(**task)


@router.get("/agent/stream/{task_id}", summary="订阅任务 SSE 事件流")
async def stream_task_events(
        request: Request,
        task_id: str,
        last_event_id_header: int = Header(
            default=0, alias="Last-Event-ID", ge=0),
        last_event_id: int | None = Query(default=None, ge=0)):
    """
    SSE 端点：订阅指定任务的实时事件流。

    客户端连接后会持续收到 SSE 事件直到任务结束。
    若任务已结束，会立即回放最后一条事件并关闭连接。

    SSE 事件格式：
      data: {"event": "task_start", "data": {...}}\n\n
      data: {"event": "token",      "data": "..."}\n\n
      data: {"event": "task_end",   "data": {...}}\n\n
    """
    queue = request.app.state.task_queue

    # 确认任务存在
    task = await queue.get_status(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")

    async def event_generator():
        try:
            resume_from = last_event_id if last_event_id is not None else last_event_id_header
            async for event in queue.subscribe(task_id, resume_from):
                payload = json.dumps(event, ensure_ascii=False)
                yield (f"id: {event['seq_no']}\n"
                       f"event: {event['event']}\n"
                       f"data: {payload}\n\n")
        except asyncio.CancelledError:
            # 客户端断开连接时静默结束
            pass

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no"  # Nginx 禁用缓冲
        }
    )


@router.get("/agent/tasks", summary="获取任务列表")
async def list_tasks(request: Request, user_id: int = None):
    """
    获取所有异步任务列表
    支持按 user_id 查询指定用户的任务
    """
    queue = request.app.state.task_queue
    tasks = await queue.list_tasks(user_id)
    return {"total": len(tasks), "tasks": tasks}


@router.post("/agent/tasks/{task_id}/cancel", response_model=TaskStatusResponse,
             summary="取消异步任务")
async def cancel_task(request: Request, task_id: str):
    task = await request.app.state.task_queue.cancel(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    return TaskStatusResponse(**task)


@router.post("/knowledge/index", summary="创建知识库索引任务")
async def index_document(
        request: Request,
        metadata: str = Form(...),
        file: UploadFile = File(...)):
    try:
        body = KnowledgeIndexRequest.model_validate_json(metadata)
        content = await file.read()
        if not content:
            raise HTTPException(status_code=400, detail="document file is empty")
        if len(content) > request.app.state.settings.KNOWLEDGE_MAX_FILE_SIZE:
            raise HTTPException(status_code=413, detail="document file is too large")
        return await request.app.state.knowledge_service.submit(
            body.model_dump(), content)
    except TaskCapacityError as exc:
        raise HTTPException(status_code=429, detail={
            "error_type": "CAPACITY_EXCEEDED", "message": str(exc)}) from exc
    except ValueError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc


@router.get("/knowledge/index/{task_id}", summary="查询索引任务")
async def index_status(request: Request, task_id: str):
    result = request.app.state.knowledge_service.status(task_id)
    if not result:
        raise HTTPException(status_code=404, detail="Index task not found")
    return result


@router.delete("/knowledge/documents/{document_id}", summary="删除文档向量")
async def delete_document_vectors(
    request: Request,
    document_id: str,
    version: int | None = None,
):
    await request.app.state.knowledge_service.delete_document(document_id, version)
    return {"status": "deleted"}


@router.post("/knowledge/search", summary="知识库检索诊断")
async def search_knowledge(request: Request, body: KnowledgeSearchRequest):
    results = await request.app.state.knowledge_service.search(
        body.kb_id,
        body.query,
        body.document_versions,
        body.top_k,
        body.score_threshold,
    )
    return {"total": len(results), "results": results}
