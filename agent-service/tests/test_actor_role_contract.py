import unittest

from pydantic import ValidationError

from app.models.schemas import AgentSubmitRequest
from app.core.task_queue import TaskQueue
from app.tools.models import ActorRole, ActorType
from app.tools.registry import ToolRegistry
from app.tools.definitions import ALL_DEFINITIONS


class _NoopAgent:
    async def stream_process(self, *args, **kwargs):
        if False:
            yield ""


class ActorRoleContractTest(unittest.TestCase):
    def test_accepts_roles_defined_by_java_contract(self):
        request = AgentSubmitRequest(
            task_id="task-1", user_id=7, query="query employees",
            actor_role="SUPER_ADMIN",
        )
        self.assertEqual("SUPER_ADMIN", request.actor_role)

    def test_accepts_customer_role_for_user_actor(self):
        """用户任务必须使用独立主体字段，不能伪装成管理员。"""
        request = AgentSubmitRequest(
            task_id="task-user", actor_id=17, actor_type="USER",
            actor_role="CUSTOMER", agent_profile="USER_ASSISTANT",
            query="show my orders",
        )

        self.assertEqual(17, request.resolved_actor_id)
        self.assertEqual("USER", request.actor_type)
        self.assertEqual("CUSTOMER", request.actor_role)

    def test_rejects_role_that_does_not_match_actor_type(self):
        """主体类型和角色不一致时应在进入任务队列前拒绝。"""
        with self.assertRaises(ValidationError):
            AgentSubmitRequest(
                task_id="task-user", actor_id=17, actor_type="USER",
                actor_role="ADMIN", query="show my orders",
            )

    def test_rejects_unknown_role(self):
        with self.assertRaises(ValidationError):
            AgentSubmitRequest(
                task_id="task-1", user_id=7, query="query employees",
                actor_role="OWNER",
            )


class ActorRoleTaskContextTest(unittest.IsolatedAsyncioTestCase):
    async def test_queue_keeps_java_role_in_private_execution_context(self):
        queue = TaskQueue(_NoopAgent())
        await queue.submit(
            task_id="task-role", user_id=7, query="query employees",
            actor_role="SUPER_ADMIN",
        )
        self.assertEqual(
            "SUPER_ADMIN",
            queue._tasks["task-role"]["_execution"]["actor_role"],
        )

    async def test_queue_keeps_generic_actor_identity(self):
        """任务执行上下文应保存通用主体，而不是仅保存user_id。"""
        queue = TaskQueue(_NoopAgent(), agent_profile="USER_ASSISTANT")
        await queue.submit(
            task_id="task-user", actor_id=17, actor_type="USER",
            actor_role="CUSTOMER", agent_profile="USER_ASSISTANT",
            query="show my orders",
        )

        execution = queue._tasks["task-user"]["_execution"]
        self.assertEqual(17, execution["actor_id"])
        self.assertEqual("USER", execution["actor_type"])
        self.assertEqual("CUSTOMER", execution["actor_role"])


class ActorToolIsolationTest(unittest.TestCase):
    def test_customer_cannot_see_admin_tools(self):
        """新增用户角色不能因默认角色集合而继承管理端工具。"""
        registry = ToolRegistry(ALL_DEFINITIONS)

        names = set(registry.names_for_role(ActorRole.CUSTOMER))
        self.assertNotIn("query_employees", names)
        self.assertNotIn("update_order_status", names)
        self.assertEqual(ActorType.USER, ActorType("USER"))

    def test_customer_sees_only_user_safe_tools(self):
        registry = ToolRegistry(ALL_DEFINITIONS)
        names = set(registry.names_for_role(ActorRole.CUSTOMER))

        self.assertIn("search_products", names)
        self.assertIn("get_cart", names)
        self.assertIn("add_cart_item", names)
        self.assertNotIn("query_employees", names)
        self.assertNotIn("update_order_status", names)


if __name__ == "__main__":
    unittest.main()
