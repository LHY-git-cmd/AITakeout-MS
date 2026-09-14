import json
import unittest

from app.tools import (
    AdminRole,
    ToolAccess,
    ToolDefinition,
    ToolRegistry,
    ToolRegistryError,
    build_default_registry,
)
from app.tools.definitions.employee import QueryEmployeesArguments


class ToolRegistryTest(unittest.TestCase):
    def setUp(self):
        self.registry = build_default_registry()

    def test_default_registry_contains_expected_business_tools(self):
        names = set(self.registry.names_for_role(AdminRole.ADMIN))

        self.assertIn("query_employees", names)
        self.assertIn("query_knowledge_bases", names)
        self.assertIn("query_orders", names)
        self.assertIn("update_order_status", names)
        self.assertIn("update_dish", names)
        self.assertIn("update_setmeal", names)
        self.assertIn("update_shop_status", names)

        # 本阶段没有注册员工和知识库写工具，因此普通管理员无法发现或调用它们。
        self.assertNotIn("create_employee", names)
        self.assertNotIn("delete_knowledge_base", names)

    def test_super_admin_sees_every_registered_tool(self):
        super_names = set(self.registry.names_for_role(AdminRole.SUPER_ADMIN))
        admin_names = set(self.registry.names_for_role(AdminRole.ADMIN))
        self.assertEqual(super_names, admin_names)
        self.assertEqual(18, len(super_names))

    def test_registry_filters_role_restricted_definition(self):
        restricted = ToolDefinition(
            name="test_super_admin_operation",
            description="test only",
            arguments_model=QueryEmployeesArguments,
            operation="test.super-admin",
            allowed_roles=frozenset({AdminRole.SUPER_ADMIN}),
        )
        registry = ToolRegistry((restricted,))

        self.assertEqual((), registry.names_for_role(AdminRole.ADMIN))
        self.assertEqual(("test_super_admin_operation",),
                         registry.names_for_role(AdminRole.SUPER_ADMIN))
        with self.assertRaises(ToolRegistryError) as raised:
            registry.definition_for_role("test_super_admin_operation", AdminRole.ADMIN)
        self.assertEqual("TOOL_PERMISSION_DENIED", raised.exception.code)

    def test_openai_schema_forbids_unknown_fields_and_limits_page_size(self):
        schemas = self.registry.schemas_for_role(AdminRole.ADMIN)
        employee = next(
            item for item in schemas
            if item["function"]["name"] == "query_employees"
        )
        parameters = employee["function"]["parameters"]

        self.assertFalse(parameters["additionalProperties"])
        self.assertEqual(50, parameters["properties"]["page_size"]["maximum"])

    def test_validation_rejects_unknown_tool_invalid_json_and_extra_fields(self):
        with self.assertRaises(ToolRegistryError) as unknown:
            self.registry.validate_arguments("drop_database", AdminRole.SUPER_ADMIN, {})
        self.assertEqual("TOOL_NOT_FOUND", unknown.exception.code)

        with self.assertRaises(ToolRegistryError) as invalid_json:
            self.registry.validate_arguments("query_orders", AdminRole.ADMIN, "{")
        self.assertEqual("INVALID_TOOL_ARGUMENTS", invalid_json.exception.code)

        with self.assertRaises(ToolRegistryError) as extra:
            self.registry.validate_arguments(
                "query_orders", AdminRole.ADMIN,
                {"page": 1, "page_size": 20, "sql": "select * from employee"},
            )
        self.assertEqual("INVALID_TOOL_ARGUMENTS", extra.exception.code)

    def test_order_write_requires_reason_and_all_writes_require_confirmation(self):
        with self.assertRaises(ToolRegistryError):
            self.registry.validate_arguments(
                "update_order_status", AdminRole.ADMIN,
                json.dumps({"order_id": 1, "action": "cancel"}),
            )

        arguments = self.registry.validate_arguments(
            "update_order_status", AdminRole.ADMIN,
            {"order_id": 1, "action": "cancel", "reason": "顾客要求取消"},
        )
        self.assertEqual("cancel", arguments.action)

        for name in self.registry.names_for_role(AdminRole.ADMIN):
            definition = self.registry.definition_for_role(name, AdminRole.ADMIN)
            if definition.access == ToolAccess.WRITE:
                self.assertTrue(definition.requires_confirmation)

    def test_date_range_and_partial_updates_are_bounded(self):
        with self.assertRaises(ToolRegistryError):
            self.registry.validate_arguments(
                "get_business_report", AdminRole.ADMIN,
                {"begin_time": "2026-09-13T10:00:00-04:00",
                 "end_time": "2026-09-12T10:00:00-04:00"},
            )

        with self.assertRaises(ToolRegistryError):
            self.registry.validate_arguments(
                "update_dish", AdminRole.ADMIN, {"dish_id": 10},
            )

        update = self.registry.validate_arguments(
            "update_dish", AdminRole.ADMIN,
            {"dish_id": 10, "status": 0},
        )
        self.assertEqual(0, update.status)


if __name__ == "__main__":
    unittest.main()
