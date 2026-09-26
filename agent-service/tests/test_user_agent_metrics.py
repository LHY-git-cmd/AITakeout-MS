"""验证用户Agent指标只输出聚合值，并覆盖意图、工具和首响应延迟。"""
import unittest

from app.user_agent.metrics import UserAgentMetrics


class UserAgentMetricsTest(unittest.IsolatedAsyncioTestCase):
    async def test_snapshot_and_prometheus_do_not_include_message_content(self):
        metrics = UserAgentMetrics()
        await metrics.task_started("task-1")
        await metrics.event("task-1", "workflow_routed", {"intent": "recommendation", "query": "隐私文本"})
        await metrics.event("task-1", "tool_completed", {
            "tool_name": "search_products", "status": "success", "data": {"name": "隐私文本"},
        })
        await metrics.event("task-1", "message_delta", {"content": "隐私文本"})
        await metrics.task_finished("task-1", "completed")

        snapshot = await metrics.snapshot()
        exposition = await metrics.prometheus()
        self.assertEqual(1, snapshot["intents"]["recommendation"])
        self.assertIn("search_products:success", snapshot["tools"])
        self.assertIn("sky_user_agent_first_response_ms", exposition)
        self.assertNotIn("隐私文本", exposition)


if __name__ == "__main__":
    unittest.main()
