"""
异步任务队列模块（支持 SSE 事件流广播）。

该模块实现了一个内存中的任务管理器，其核心设计是为了支持服务发送事件 (SSE)，
允许客户端实时订阅任务的执行进度。

核心设计流程：
  1. `submit()`: 客户端提交一个任务，方法立即返回一个 `task_id`，并在后台通过 `asyncio.create_task` 启动一个工作协程来执行任务，不阻塞当前请求。
  2. `_run_task()`: 后台工作协程调用 `agent.stream_process()` 来执行核心逻辑（如调用 LLM），并逐个 token 地接收生成结果。
  3. `_publish()`: 每当 `_run_task` 收到新的事件（如一个 token），它会通过此方法将事件广播给所有订阅了该任务的客户端。这是一个典型的 "fan-out" 模式。
  4. `subscribe()`: SSE 连接的端点会调用此方法。它会为每个订阅者创建一个 `asyncio.Queue`，并异步地从中迭代事件，将事件流式地发送给客户端。

事件格式（统一为字典，序列化后作为 SSE 的 data 字段）：
  - `{"event": "task_start", "data": {...}}`: 任务开始事件。
  - `{"event": "token", "data": "当前生成的 token 文本"}`: 新 token 生成事件。
  - `{"event": "task_end", "data": {"status": "completed|failed", "result": "...", "error": "..."}}`: 任务结束事件。

生产环境优化建议：
  - 当前实现是基于内存的 `asyncio.Queue`，这意味着它只能在单个服务实例中工作。
  - 在生产环境中，为了支持多实例横向扩展，应将事件广播机制从内存队列替换为 Redis Pub/Sub。
"""
import asyncio
import hashlib
import json
import inspect
from collections import Counter
from datetime import datetime
from typing import AsyncGenerator, Dict, Optional, Set
from app.core.agent import PythonAgent
from app.core.task_store import TaskStore
from app.core.config import settings
from app.core.trace import current_session_id, current_task_id, current_trace_id
from app.tools.orchestrator import AgentOutput


class TaskCapacityError(ValueError):
    """Agent 已达到运行与排队容量。"""


class TaskQueue:
    """
    一个支持 SSE 事件广播的异步任务队列管理器。

    它在内存中管理任务的状态、事件历史和订阅者列表，并协调后台任务的执行。

    关键内部数据结构：
      - `_tasks[task_id]`: 存储任务的元信息，如状态、结果、创建时间等。
      - `_subscribers[task_id]`: 一个集合，包含所有订阅了该任务事件流的 `asyncio.Queue` 实例。
      - `_events[task_id]`: 一个列表，按顺序记录了该任务产生的所有事件历史。
      - `_worker_tasks[task_id]`: 指向实际在后台执行模型调用的 `asyncio.Task` 对象。
    """

    def __init__(self, agent: PythonAgent, state_path: Optional[str] = None):
        """
        初始化任务队列管理器。

        :param agent: `PythonAgent` 的实例，用于执行实际的任务逻辑。
        :param state_path: (可选) 用于持久化任务状态的 SQLite 数据库路径。如果提供，则会启用状态持久化。
        """
        self.agent = agent
        self._tasks: Dict[str, dict] = {}
        self._subscribers: Dict[str, Set[asyncio.Queue]] = {}
        self._events: Dict[str, list[dict]] = {}
        self._worker_tasks: Dict[str, asyncio.Task] = {}
        self._lock = asyncio.Lock()
        self._store = TaskStore(state_path) if state_path else None
        self._shutting_down = False
        self._execution_slots = asyncio.Semaphore(settings.LLM_MAX_CONCURRENCY)
        if self._store:
            # 如果配置了持久化，则在启动时加载所有历史任务和事件。
            for task, events in self._store.load_all():
                task_id = task["task_id"]
                self._tasks[task_id] = task
                self._events[task_id] = events
                self._subscribers[task_id] = set()

    # ------------------------------------------------------------------
    #  公共 API：启动、关闭、提交、查询、订阅、取消
    # ------------------------------------------------------------------

    async def start(self) -> None:
        """
        启动服务并恢复持久化的未完成任务。
        
        在应用启动时调用，以确保因服务重启而中断的任务可以被重新执行。
        """
        async with self._lock:
            unfinished = []
            for task_id, task in self._tasks.items():
                if task["status"] in ("pending", "running"):
                    task["status"] = "pending"
                    task["updated_at"] = datetime.now()
                    self._persist_locked(task_id)
                    unfinished.append(task_id)
        for task_id in unfinished:
            await self._start_worker(task_id)

    async def shutdown(self) -> None:
        """
        平滑关闭服务。
        
        取消所有正在运行的后台工作协程，但保留其在数据库中的状态，
        以便服务下次启动时可以恢复。
        """
        self._shutting_down = True
        async with self._lock:
            workers = list(self._worker_tasks.values())
        for worker in workers:
            worker.cancel()
        if workers:
            await asyncio.gather(*workers, return_exceptions=True)
        if self._store:
            self._store.close()

    async def submit(self, user_id: int, query: str,
                     context: Optional[dict] = None,
                     knowledge: Optional[dict] = None,
                     task_id: str = None,
                     trace_id: Optional[str] = None,
                     actor_role: str = "ADMIN",
                     session_id: Optional[str] = None,
                     model: str = None,
                     temperature: float = 0.2) -> dict:
        """
        提交一个异步任务，并立即返回任务信息。

        这是一个幂等操作。如果使用相同的 `task_id` 和请求参数重复提交，
        它将直接返回现有任务的状态，而不会重复执行。

        :param user_id: 用户 ID。
        :param actor_role: Java确定的管理员角色快照，仅用于工具可见性筛选。
        :param query: 用户的查询。
        :param context: 上下文信息，如历史对话。
        :param knowledge: 知识库相关参数。
        :param task_id: 任务的唯一标识符。
        :param session_id: 会话 ID。
        :param model: 使用的 LLM 模型。
        :param temperature: LLM 采样温度。
        :return: 包含任务当前状态的字典。
        """
        if not task_id:
            raise ValueError("task_id is required")
        model = model or settings.LLM_MODEL
        execution = {
            "trace_id": trace_id or task_id,
            "session_id": session_id,
            "user_id": user_id,
            "actor_role": actor_role,
            "query": query,
            "context": context,
            "knowledge": knowledge,
            "model": model,
            "temperature": temperature
        }
        request_hash = self._request_hash(execution)

        async with self._lock:
            if task_id in self._tasks:
                # 幂等性检查：如果任务已存在且请求内容相同，则直接返回状态。
                existing = self._tasks[task_id]
                if existing["_request_hash"] != request_hash:
                    raise ValueError(f"task_id reused with different request: {task_id}")
                return self._public_task(existing)

            active_count = sum(
                task["status"] in ("pending", "running")
                for task in self._tasks.values()
            )
            capacity = settings.LLM_MAX_CONCURRENCY + settings.LLM_MAX_QUEUE_SIZE
            if active_count >= capacity:
                raise TaskCapacityError("Agent task capacity exceeded")
            
            # 创建新的任务记录
            self._tasks[task_id] = {
                "task_id": task_id,
                "trace_id": trace_id or task_id,
                "user_id": user_id,
                "query": query,
                "status": "pending",
                "result": None,
                "error_msg": None,
                "created_at": datetime.now(),
                "updated_at": None,
                "_request_hash": request_hash,
                "_execution": execution
            }
            self._subscribers[task_id] = set()
            self._events[task_id] = []
            self._persist_locked(task_id)

        await self._start_worker(task_id)
        return await self.get_status(task_id)

    async def subscribe(self, task_id: str,
                        last_event_id: int = 0) -> AsyncGenerator[dict, None]:
        """
        订阅指定任务的事件流，供 SSE 端点调用。

        此方法实现了智能的事件回放和实时订阅机制：
          1. 如果任务已经结束，它会回放 `last_event_id` 之后的所有历史事件，然后立即结束。
          2. 如果任务仍在进行中，它会先回放历史事件，然后创建一个新的 `asyncio.Queue` 加入到该任务的订阅者列表中，并开始等待接收实时事件。
          3. 当接收到任务结束事件（如 `task_end`）后，会自动退订并释放资源。

        :param task_id: 要订阅的任务 ID。
        :param last_event_id: 客户端收到的最后一个事件的序列号，用于断线重连时回放错过的事件。
        :yield: 任务产生的事件字典。
        """
        terminal_events = {"task_end", "task_error", "task_cancelled"}
        queue: asyncio.Queue = asyncio.Queue()

        # 在同一把锁内获取历史事件快照并注册实时订阅，避免在切换期间丢失事件。
        async with self._lock:
            task = self._tasks.get(task_id)
            if task is None:
                return
            history = [event for event in self._events.get(task_id, [])
                       if event["seq_no"] > last_event_id]
            is_terminal = task["status"] in ("completed", "failed", "cancelled")
            if not is_terminal:
                self._subscribers.setdefault(task_id, set()).add(queue)

        # 1. 先回放历史事件
        for event in history:
            yield event

        if is_terminal:
            return

        # 2. 开始等待并产出实时事件
        try:
            while True:
                event = await queue.get()
                yield event
                if event["event"] in terminal_events:
                    break
        finally:
            # 3. 无论正常结束还是客户端断连，都从订阅者列表中清理自己。
            async with self._lock:
                subs = self._subscribers.get(task_id)
                if subs and queue in subs:
                    subs.discard(queue)

    async def get_status(self, task_id: str) -> Optional[dict]:
        """
        查询任务的详细状态。
        
        这可以用于传统的轮询模式客户端。
        """
        async with self._lock:
            task = self._tasks.get(task_id)
            return self._public_task(task) if task else None

    async def list_tasks(self, user_id: Optional[int] = None) -> list:
        """
        获取任务列表，可以按用户 ID 进行筛选。
        """
        async with self._lock:
            values = self._tasks.values()
            if user_id is not None:
                values = (v for v in values if v["user_id"] == user_id)
            return [self._public_task(value) for value in values]

    async def metrics_snapshot(self) -> dict:
        async with self._lock:
            statuses = Counter(task["status"] for task in self._tasks.values())
            return {
                "tasks": dict(statuses),
                "running": statuses.get("running", 0),
                "queued": statuses.get("pending", 0),
                "capacity": settings.LLM_MAX_CONCURRENCY + settings.LLM_MAX_QUEUE_SIZE,
            }

    async def cancel(self, task_id: str) -> Optional[dict]:
        """
        取消一个正在运行的任务。这是一个幂等操作。
        
        它会取消后台的工作协程，并等待协程完成清理工作并写入最终状态。
        """
        async with self._lock:
            task = self._tasks.get(task_id)
            if task is None:
                return None
            if task["status"] in ("completed", "failed", "cancelled"):
                return self._public_task(task)
            worker = self._worker_tasks.get(task_id)

        if worker and not worker.done():
            worker.cancel()
            try:
                await worker
            except asyncio.CancelledError:
                pass
        return await self.get_status(task_id)

    # ------------------------------------------------------------------
    #  内部方法：事件发布、后台执行、持久化等
    # ------------------------------------------------------------------

    async def _publish(self, task_id: str, event_name: str, data):
        """
        核心的事件发布方法。

        它将一个新事件广播给该任务的所有订阅者（fan-out），
        并同时将事件记录到历史事件列表中。
        """
        async with self._lock:
            events = self._events.setdefault(task_id, [])
            trace_id = self._tasks.get(task_id, {}).get("trace_id", task_id)
            event = {
                "task_id": task_id,
                "trace_id": trace_id,
                "seq_no": len(events) + 1,
                "event": event_name,
                "data": data
            }
            events.append(event)
            self._persist_locked(task_id)
            # 获取当前所有订阅者的队列副本，以在锁外进行操作
            subs = list(self._subscribers.get(task_id, set()))

        for q in subs:
            await q.put(event)

    async def _run_task(self, task_id: str):
        """
        后台执行任务的工作协程。

        这是任务执行的入口点，它负责：
          1. 更新任务状态为 "running"。
          2. 发布 "task_start" 事件。
          3. 调用 `agent.stream_process()` 并逐个 token 地发布 "token" 事件。
          4. 在任务结束或失败时，发布 "task_end" 或 "task_error" 事件。
        """
        full_result = ""
        try:
            # pending 任务在此等待执行槽，因此队列长度有明确上限。
            async with self._execution_slots:
                async with self._lock:
                    execution = self._tasks[task_id]["_execution"]
                    self._tasks[task_id]["status"] = "running"
                    self._tasks[task_id]["updated_at"] = datetime.now()
                    self._persist_locked(task_id)

                await self._publish(task_id, "task_start", {
                    "task_id": task_id,
                    "status": "running"
                })
                trace_token = current_trace_id.set(execution.get("trace_id") or task_id)
                task_token = current_task_id.set(task_id)
                session_token = current_session_id.set(execution.get("session_id"))
                try:
                    await self._execute_stream(task_id, execution, full_result)
                finally:
                    current_session_id.reset(session_token)
                    current_task_id.reset(task_token)
                    current_trace_id.reset(trace_token)
                return
        except asyncio.CancelledError:
            full_result = getattr(asyncio.current_task(), "partial_result", full_result)
            async with self._lock:
                self._tasks[task_id]["status"] = (
                    "pending" if self._shutting_down else "cancelled")
                self._tasks[task_id]["updated_at"] = datetime.now()
                self._persist_locked(task_id)

            if not self._shutting_down:
                await self._publish(task_id, "task_cancelled", {
                    "status": "cancelled", "partial_result": full_result})
        except Exception as e:
            full_result = getattr(e, "partial_result", full_result)
            error_msg = str(e)
            error_type = getattr(e, "error_type", "UNKNOWN")
            async with self._lock:
                self._tasks[task_id]["status"] = "failed"
                self._tasks[task_id]["error_msg"] = error_msg
                self._tasks[task_id]["updated_at"] = datetime.now()
                self._persist_locked(task_id)
            await self._publish(task_id, "task_error", {
                "status": "failed", "error_type": error_type,
                "error_msg": error_msg, "partial_result": full_result})
        finally:
            async with self._lock:
                self._worker_tasks.pop(task_id, None)

    async def _execute_stream(self, task_id: str, execution: dict, full_result: str):
        """在已取得执行槽后运行一次流式任务。"""
        try:
            rag_context, citations, refusal = (None, [], None)
            if execution.get("knowledge") and hasattr(self.agent, "prepare_rag"):
                rag_context, citations, refusal = await self.agent.prepare_rag(
                    execution["query"], execution["context"], execution["knowledge"])
            stream_args = {
                "model": execution["model"],
                "temperature": execution["temperature"],
                "task_id": task_id,
                "session_id": execution.get("session_id"),
                "trace_id": execution.get("trace_id"),
            }
            parameters = inspect.signature(self.agent.stream_process).parameters.values()
            supports_tool_context = any(
                value.name == "employee_id" for value in parameters)
            if supports_tool_context:
                stream_args.update(
                    employee_id=execution.get("user_id"),
                    actor_role=execution.get("actor_role"),
                )
            if rag_context or refusal:
                stream_args.update(rag_context=rag_context, refusal=refusal)
            async for token in self.agent.stream_process(
                    execution["query"], execution["context"], **stream_args):
                if isinstance(token, AgentOutput):
                    if token.event == "token":
                        content = str(token.data.get("content", ""))
                        full_result += content
                    await self._publish(task_id, token.event, token.data)
                else:
                    full_result += token
                    await self._publish(task_id, "token", {"content": token})

            # 成功
            async with self._lock:
                self._tasks[task_id]["status"] = "completed"
                self._tasks[task_id]["result"] = full_result
                self._tasks[task_id]["updated_at"] = datetime.now()
                self._persist_locked(task_id)

            await self._publish(task_id, "task_end", {
                "status": "completed",
                "result": full_result,
                "citations": citations
            })
        except BaseException as exc:
            # 将部分结果附到异常，供统一终态处理使用。
            setattr(exc, "partial_result", full_result)
            current = asyncio.current_task()
            if current is not None:
                setattr(current, "partial_result", full_result)
            raise

    async def _start_worker(self, task_id: str) -> None:
        worker = asyncio.create_task(
            self._run_task(task_id), name=f"agent-task-{task_id}")
        async with self._lock:
            self._worker_tasks[task_id] = worker

    def _persist_locked(self, task_id: str) -> None:
        if self._store:
            self._store.save(self._tasks[task_id], self._events.get(task_id, []))

    @staticmethod
    def _request_hash(execution: dict) -> str:
        canonical = json.dumps(
            execution, ensure_ascii=False, sort_keys=True,
            separators=(",", ":"), default=str)
        return hashlib.sha256(canonical.encode("utf-8")).hexdigest()

    @staticmethod
    def _public_task(task: dict) -> dict:
        return {key: value for key, value in task.items() if not key.startswith("_")}
