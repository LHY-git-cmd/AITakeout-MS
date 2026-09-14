import asyncio
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from app.core.task_queue import TaskCapacityError, TaskQueue
from app.core.trace import current_session_id, current_task_id, current_trace_id


class FakeAgent:
    async def stream_process(self, query, context=None, model=None,
                             temperature=0.7, task_id=None, session_id=None,
                             trace_id=None):
        for token in ("hello", " world"):
            await asyncio.sleep(0)
            yield token


class BlockingAgent:
    async def stream_process(self, query, context=None, model=None,
                             temperature=0.7, task_id=None, session_id=None,
                             trace_id=None):
        yield "partial"
        await asyncio.Event().wait()


class CountingAgent(FakeAgent):
    def __init__(self):
        self.calls = 0

    async def stream_process(self, *args, **kwargs):
        self.calls += 1
        async for token in super().stream_process(*args, **kwargs):
            yield token


class ContextCapturingAgent(FakeAgent):
    def __init__(self):
        self.captured = None

    async def stream_process(self, *args, **kwargs):
        self.captured = (
            current_trace_id.get(), current_task_id.get(),
            current_session_id.get(),
        )
        async for token in super().stream_process(*args, **kwargs):
            yield token


class TaskQueueTest(unittest.IsolatedAsyncioTestCase):
    # 测试已完成的任务是否能按顺序重放事件
    # 这个测试验证了当一个任务完成后，订阅该任务的客户端可以接收到所有按正确顺序排列的事件
    # （如任务开始、token生成、任务结束）。同时，它也测试了从指定序列号开始续订事件的功能。
    async def test_completed_task_replays_ordered_events(self):
        queue = TaskQueue(FakeAgent())
        task = await queue.submit(task_id="task-1", trace_id="trace-1", user_id=1, query="test")
        task_id = task["task_id"]

        events = [event async for event in queue.subscribe(task_id)]

        self.assertEqual([1, 2, 3, 4],
                         [event["seq_no"] for event in events])
        self.assertTrue(all(event["trace_id"] == "trace-1" for event in events))
        self.assertEqual(
            ["task_start", "token", "token", "task_end"],
            [event["event"] for event in events])
        self.assertEqual("hello world", events[-1]["data"]["result"])

        resumed = [event async for event in queue.subscribe(task_id, 2)]
        self.assertEqual([3, 4], [event["seq_no"] for event in resumed])

    # 测试重复的任务ID是否会复用同一个任务
    # 如果用相同的任务ID和请求内容提交两次任务，系统应该只创建一个任务，并返回同一个任务实例。
    # 这可以防止因网络重试等原因造成的重复任务执行。
    async def test_duplicate_task_id_reuses_same_task(self):
        queue = TaskQueue(FakeAgent())
        first = await queue.submit(task_id="same", user_id=1, query="first")
        second = await queue.submit(task_id="same", user_id=1, query="first")

        self.assertEqual(first["task_id"], second["task_id"])

    # 测试当任务ID重复但请求内容不同时，是否会拒绝请求
    # 如果两次提交使用了相同的任务ID，但请求的参数（如查询内容）不同，
    # 第二次提交应该被拒绝，以保证任务的唯一性和确定性。
    async def test_duplicate_task_id_with_different_request_is_rejected(self):
        queue = TaskQueue(FakeAgent())
        await queue.submit(task_id="same", user_id=1, query="first")

        with self.assertRaises(ValueError):
            await queue.submit(task_id="same", user_id=1, query="second")

    # 测试并发重试是否只执行一次
    # 当多个客户端在短时间内用相同的请求并发地提交任务时，
    # 任务队列应该能识别出这是同一个任务，并确保底层的处理逻辑（Agent）只被调用一次。
    async def test_concurrent_retries_execute_only_once(self):
        agent = CountingAgent()
        queue = TaskQueue(agent)
        results = await asyncio.gather(*[
            queue.submit(task_id="concurrent", user_id=1, query="same")
            for _ in range(20)
        ])
        await asyncio.sleep(0.01)

        self.assertEqual({"concurrent"}, {item["task_id"] for item in results})
        self.assertEqual(1, agent.calls)

    # 测试提交任务时是否必须提供任务ID
    # 任务ID是任务的唯一标识，这个测试确保了在提交任务时，如果缺少任务ID，请求会被拒绝。
    async def test_task_id_is_required(self):
        queue = TaskQueue(FakeAgent())
        with self.assertRaises(ValueError):
            await queue.submit(task_id=None, user_id=1, query="test")

    # 测试工作进程是否能传播关联上下文
    # 在复杂的系统中，为了追踪一个完整的请求链路，需要传递一些上下文信息（如trace_id, session_id）。
    # 这个测试验证了当任务被执行时，这些上下文信息能被正确地传递给底层的处理逻辑（Agent）。
    async def test_worker_propagates_correlation_context(self):
        agent = ContextCapturingAgent()
        queue = TaskQueue(agent)
        await queue.submit(
            task_id="task-context", trace_id="trace-context",
            session_id="session-context", user_id=1, query="test",
        )
        events = [
            event async for event in queue.subscribe("task-context")
        ]

        self.assertEqual("task_end", events[-1]["event"])
        self.assertEqual(
            ("trace-context", "task-context", "session-context"),
            agent.captured,
        )

    # 测试取消任务是否能停止工作进程并发布终止事件
    # 这个测试验证了当一个正在运行的任务被取消时，
    # 1. 任务的执行会停止。
    # 2. 任务状态会更新为“cancelled”。
    # 3. 会发布一个“task_cancelled”事件，其中可能包含任务已经生成的部分结果。
    async def test_cancel_stops_worker_and_publishes_terminal_event(self):
        queue = TaskQueue(BlockingAgent())
        task = await queue.submit(task_id="task-cancel", user_id=1, query="test")
        task_id = task["task_id"]

        for _ in range(20):
            status = await queue.get_status(task_id)
            if status["status"] == "running":
                break
            await asyncio.sleep(0)

        status = await queue.cancel(task_id)
        events = [event async for event in queue.subscribe(task_id)]

        self.assertEqual("cancelled", status["status"])
        self.assertEqual("task_cancelled", events[-1]["event"])
        self.assertEqual("partial", events[-1]["data"]["partial_result"])

    # 测试容量限制是否能在不创建任务的情况下拒绝请求
    # 任务队列有并发数和排队长度的限制。这个测试验证了当系统达到容量上限时，
    # 新提交的任务会被立即拒绝（抛出TaskCapacityError），并且不会为这个被拒绝的请求创建任何任务记录。
    async def test_capacity_limit_rejects_without_creating_task(self):
        with (patch("app.core.task_queue.settings.LLM_MAX_CONCURRENCY", 1),
              patch("app.core.task_queue.settings.LLM_MAX_QUEUE_SIZE", 0)):
            queue = TaskQueue(BlockingAgent())
            await queue.submit(task_id="running", user_id=1, query="test")
            with self.assertRaises(TaskCapacityError):
                await queue.submit(task_id="rejected", user_id=1, query="test")
            self.assertIsNone(await queue.get_status("rejected"))
            await queue.cancel("running")

    # 测试用户过滤器是否使用整数ID
    # 任务可以按用户ID进行过滤。这个测试验证了使用正确的用户ID可以查到对应的任务，
    # 而使用错误的用户ID则查不到。
    async def test_user_filter_uses_integer_ids(self):
        queue = TaskQueue(FakeAgent())
        await queue.submit(task_id="task-user", user_id=7, query="test")

        self.assertEqual(1, len(await queue.list_tasks(7)))
        self.assertEqual(0, len(await queue.list_tasks(8)))

    # 测试未完成的任务在重启后是否能恢复
    # 这是一个健壮性测试。它模拟了以下场景：
    # 1. 一个任务正在运行中。
    # 2. 系统（或任务队列）突然关闭。
    # 3. 系统重启后，重新初始化任务队列。
    # 4. 测试验证新的任务队列能够检测到之前未完成的任务，并自动恢复执行，最终将其完成。
    # 这确保了即使在服务中断的情况下，任务也不会丢失。
    async def test_unfinished_task_resumes_after_restart(self):
        with tempfile.TemporaryDirectory() as directory:
            state_path = str(Path(directory) / "tasks.sqlite3")
            first_queue = TaskQueue(BlockingAgent(), state_path)
            await first_queue.start()
            await first_queue.submit(
                task_id="recover", user_id=1, query="test", session_id="s1")
            for _ in range(20):
                status = await first_queue.get_status("recover")
                if status["status"] == "running":
                    break
                await asyncio.sleep(0)
            await first_queue.shutdown()

            second_queue = TaskQueue(FakeAgent(), state_path)
            await second_queue.start()
            events = [event async for event in second_queue.subscribe("recover")]
            status = await second_queue.get_status("recover")

            self.assertEqual("completed", status["status"])
            self.assertEqual("hello world", status["result"])
            self.assertEqual("task_end", events[-1]["event"])
            await second_queue.shutdown()


if __name__ == "__main__":
    unittest.main()
