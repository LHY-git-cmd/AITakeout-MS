"""验证管理端添加菜品的权限、批次、金额和模拟营养边界。"""
import unittest
from app.tools.context import ToolContext
from app.tools.models import ToolResult
from app.tools.orchestrator import ToolOrchestrator
from tests.test_tool_orchestrator import FakeGateway, call, response
from app.tools.definitions import build_admin_registry, build_user_registry
from app.tools.models import ActorRole
from app.tools.registry import ToolRegistryError


def test_batch():
    return {"test_data": True, "dishes": [
        {"name": f"测试菜品{i}", "category_id": 1, "price": "18.50",
         "description": "测试配方", "nutrition": {
             "serving_size_g": 200, "energy_kcal": 250, "protein_g": 10,
             "fat_g": 8, "carbohydrate_g": 30, "dietary_fiber_g": 3,
             "sugar_g": 2, "sodium_mg": 300, "source_type": "SIMULATED",
             "source_reference": "Agent生成的测试数据",
             "ingredients": [{"code": "RICE", "name": "米饭", "amount_g": 150}],
             "allergens": [{"code": "SOY", "status": "UNKNOWN", "source_reference": "测试估算"}]}}
        for i in range(3)]}


class CreateDishesToolTest(unittest.TestCase):
    def setUp(self):
        self.registry = build_admin_registry()

    def test_admin_only_and_requires_confirmation(self):
        definition = self.registry.definition_for_role("create_dishes", ActorRole.ADMIN)
        self.assertTrue(definition.requires_confirmation)
        self.assertNotIn("create_dishes", build_user_registry().names_for_role(ActorRole.CUSTOMER))
        self.assertIn("query_dish_categories", self.registry.names_for_role(ActorRole.ADMIN))

    def test_valid_three_dishes_without_images(self):
        result = self.registry.validate_arguments("create_dishes", ActorRole.ADMIN, test_batch())
        self.assertEqual(3, len(result.dishes))
        self.assertIsNone(result.dishes[0].image)

    def test_test_batch_must_have_three_complete_simulated_profiles(self):
        for mutation in ("count", "nutrition", "source", "precision", "duplicate", "status"):
            with self.subTest(mutation=mutation):
                args = test_batch()
                if mutation == "count": args["dishes"].pop()
                if mutation == "nutrition": args["dishes"][0].pop("nutrition")
                if mutation == "source": args["dishes"][0]["nutrition"]["source_type"] = "VERIFIED"
                if mutation == "precision": args["dishes"][0]["price"] = "1.001"
                if mutation == "duplicate": args["dishes"][1]["name"] = args["dishes"][0]["name"]
                if mutation == "status": args["dishes"][0]["status"] = 1
                with self.assertRaises(ToolRegistryError):
                    self.registry.validate_arguments("create_dishes", ActorRole.ADMIN, args)

    def test_formal_dish_does_not_require_invented_nutrition(self):
        result = self.registry.validate_arguments("create_dishes", ActorRole.ADMIN, {
            "test_data": False, "dishes": [{"name": "米饭", "category_id": 1, "price": 2}]})
        self.assertIsNone(result.dishes[0].nutrition)


class CreateDishConfirmationFlowTest(unittest.IsolatedAsyncioTestCase):
    async def test_preview_is_forwarded_and_only_approved_operations_execute(self):
        for decision in ("CONFIRMED", "REJECTED", "EXPIRED"):
            with self.subTest(decision=decision):
                class Client:
                    writes = 0
                    async def execute(self, definition, arguments, context, call_id):
                        return ToolResult(tool_call_id=call_id, status="confirmation_required", data={
                            "confirmation_id": "confirm", "summary": "添加三道测试菜品",
                            "preview": {"test_data": True, "dishes": arguments["dishes"]}})
                    async def confirmation_status(self, confirmation_id, context):
                        return {"status": decision}
                    async def execute_confirmed(self, confirmation_id, context):
                        self.writes += 1
                        return ToolResult(tool_call_id="create", status="success", data={"dishes": [{"id": 1}]})
                client = Client()
                gateway = FakeGateway([response(calls=[call("create", "create_dishes", test_batch())]),
                                       response(content="操作已结束")])
                outputs = [output async for output in ToolOrchestrator(gateway, build_admin_registry(), client).run(
                    [{"role": "user", "content": "是，生成测试数据"}],
                    context=ToolContext(task_id="task", trace_id="trace", employee_id=9, actor_role="ADMIN"),
                    model="test-model", temperature=0.2)]
                preview = next(o.data["preview"] for o in outputs if o.event == "tool_confirmation_required")
                self.assertEqual(3, len(preview["dishes"]))
                self.assertEqual(1 if decision == "CONFIRMED" else 0, client.writes)


if __name__ == "__main__":
    unittest.main()
