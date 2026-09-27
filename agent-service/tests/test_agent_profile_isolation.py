import tempfile
import unittest
from pathlib import Path

from app.core.task_queue import TaskQueue
from app.tools.definitions import build_admin_registry, build_user_registry
from app.tools.models import ActorRole


class _EchoAgent:
    """用于验证两个Profile任务状态互不干扰的最小Agent替身。"""

    async def stream_process(self, query, context=None, **kwargs):
        yield query


class AgentProfileRegistryIsolationTest(unittest.TestCase):
    def test_user_registry_physically_excludes_admin_tools(self):
        names = set(build_user_registry().names_for_role(ActorRole.CUSTOMER))
        self.assertIn("search_products", names)
        self.assertNotIn("query_employees", names)
        self.assertNotIn("update_order_status", names)

    def test_admin_registry_physically_excludes_user_tools(self):
        registry = build_admin_registry()
        admin_names = set(registry.names_for_role(ActorRole.ADMIN))
        self.assertIn("query_employees", admin_names)
        self.assertNotIn("get_cart", registry._definitions)
        self.assertNotIn("add_cart_item", registry._definitions)


class AgentProfileTaskIsolationTest(unittest.IsolatedAsyncioTestCase):
    async def test_same_task_id_is_isolated_by_database_and_queue(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            admin_db = root / "admin.sqlite3"
            user_db = root / "user.sqlite3"
            admin = TaskQueue(_EchoAgent(), str(admin_db), "ADMIN_ASSISTANT")
            user = TaskQueue(_EchoAgent(), str(user_db), "USER_ASSISTANT")
            try:
                await admin.submit(
                    task_id="same-id", actor_id=1, actor_type="ADMIN",
                    actor_role="ADMIN", query="admin-result")
                await user.submit(
                    task_id="same-id", actor_id=1, actor_type="USER",
                    actor_role="CUSTOMER", agent_profile="USER_ASSISTANT",
                    query="user-result")
                # 订阅会等待终态事件，比读取可能已被清理的内部worker引用稳定。
                _ = [event async for event in admin.subscribe("same-id")]
                _ = [event async for event in user.subscribe("same-id")]

                self.assertNotEqual(admin_db, user_db)
                self.assertEqual("admin-result", (await admin.get_status("same-id"))["result"])
                self.assertEqual("user-result", (await user.get_status("same-id"))["result"])
            finally:
                await admin.shutdown()
                await user.shutdown()

    async def test_profile_and_actor_type_mismatch_is_rejected(self):
        user = TaskQueue(_EchoAgent(), agent_profile="USER_ASSISTANT")
        with self.assertRaisesRegex(ValueError, "actor_type does not match"):
            await user.submit(
                task_id="wrong-domain", actor_id=1, actor_type="ADMIN",
                actor_role="ADMIN", agent_profile="USER_ASSISTANT", query="x")
