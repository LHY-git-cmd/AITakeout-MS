"""Redis分布式运行时：任务流、事件回放、租约和跨实例取消信号。"""

import json
import socket
import time
from typing import Any

try:
    from redis.asyncio import Redis
except ImportError:  # 本地开发未安装Redis依赖时仍可使用SQLite模式。
    Redis = None


class RedisRuntime:
    """对Redis key/stream协议做统一封装，避免业务代码散落字符串常量。"""

    def __init__(self, url: str, lease_seconds: int = 30, consumer_name: str | None = None):
        if Redis is None:
            raise RuntimeError("redis package is required when REDIS_RUNTIME_ENABLED=true")
        self.redis = Redis.from_url(url, decode_responses=True)
        self.lease_seconds = lease_seconds
        self.consumer_name = consumer_name or f"{socket.gethostname()}-{id(self)}"

    @staticmethod
    def task_key(task_id: str) -> str:
        return f"sky:user-agent:task:{task_id}"

    @staticmethod
    def event_stream_key(task_id: str) -> str:
        return f"sky:user-agent:event:{task_id}"

    @staticmethod
    def task_stream_key() -> str:
        return "sky:user-agent:task-stream"

    async def enqueue_task(self, task: dict[str, Any]) -> str:
        """把任务写入跨实例任务流；Worker可用Consumer Group竞争消费。"""
        return await self.redis.xadd(self.task_stream_key(), {
            "task_id": task["task_id"],
            "profile": task.get("agent_profile", "USER_ASSISTANT"),
            "payload": json.dumps(task, ensure_ascii=False, default=str),
        }, maxlen=10000, approximate=True)

    async def ensure_consumer_group(self, group: str) -> None:
        try:
            await self.redis.xgroup_create(self.task_stream_key(), group, id="0", mkstream=True)
        except Exception as exception:
            if "BUSYGROUP" not in str(exception):
                raise

    async def consume_tasks(self, group: str, count: int = 10, block_ms: int = 1000):
        """读取新任务；调用方负责处理后XACK。"""
        await self.ensure_consumer_group(group)
        return await self.redis.xreadgroup(group, self.consumer_name,
                                           {self.task_stream_key(): ">"}, count=count,
                                           block=block_ms)

    async def ack_task(self, group: str, stream_id: str) -> None:
        await self.redis.xack(self.task_stream_key(), group, stream_id)

    async def save_task(self, task: dict[str, Any]) -> None:
        """保存非权威任务快照；订单等业务事实仍以Java/MySQL为准。"""
        payload = {key: json.dumps(value, ensure_ascii=False, default=str)
                   for key, value in task.items() if not key.startswith("_")}
        if payload:
            await self.redis.hset(self.task_key(task["task_id"]), mapping=payload)

    async def append_event(self, task_id: str, event: dict[str, Any]) -> str:
        """追加可重放事件，事件序号由Stream ID保证单调性。"""
        return await self.redis.xadd(self.event_stream_key(task_id), {
            "event": json.dumps(event, ensure_ascii=False, default=str),
            "seq_no": str(event.get("seq_no", 0)),
        }, maxlen=2000, approximate=True)

    async def acquire_lease(self, task_id: str) -> bool:
        key = f"sky:user-agent:lease:{task_id}"
        return bool(await self.redis.set(key, self.consumer_name,
                                          nx=True, ex=self.lease_seconds))

    async def renew_lease(self, task_id: str) -> bool:
        key = f"sky:user-agent:lease:{task_id}"
        owner = await self.redis.get(key)
        if owner != self.consumer_name:
            return False
        return bool(await self.redis.expire(key, self.lease_seconds))

    async def release_lease(self, task_id: str) -> None:
        key = f"sky:user-agent:lease:{task_id}"
        owner = await self.redis.get(key)
        if owner == self.consumer_name:
            await self.redis.delete(key)

    async def request_cancel(self, task_id: str) -> None:
        await self.redis.set(f"sky:user-agent:cancel:{task_id}", "1", ex=3600)

    async def is_cancelled(self, task_id: str) -> bool:
        return bool(await self.redis.exists(f"sky:user-agent:cancel:{task_id}"))

    async def close(self) -> None:
        await self.redis.aclose()
