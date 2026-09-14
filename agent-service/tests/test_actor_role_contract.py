import unittest

from pydantic import ValidationError

from app.models.schemas import AgentSubmitRequest
from app.core.task_queue import TaskQueue


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


if __name__ == "__main__":
    unittest.main()
