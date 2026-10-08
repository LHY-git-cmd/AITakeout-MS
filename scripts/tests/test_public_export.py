"""验证发布导出不会带入私有数据、覆盖原文件或绕过敏感内容扫描。"""
import importlib.util
from pathlib import Path
import tempfile
import unittest

_spec = importlib.util.spec_from_file_location("public_export", Path(__file__).resolve().parents[1] / "export-public-source.py")
module = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(module)


class PublicExportTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)
        self.project = self.root / "project"
        self.project.mkdir()

    def tearDown(self):
        self.directory.cleanup()

    def write(self, relative, text):
        path = self.project / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def test_excludes_private_env_databases_and_generated_files(self):
        self.write("README.md", "public documentation")
        self.write(".env", "PRIVATE=local")
        self.write("agent-service/data/tasks.sqlite3", "private records")
        self.write("sky-server/target/report.txt", "private output")
        self.write("frontend/app/src/main.ts", "export const greeting = 'hello'")
        result = module.export(self.project, self.root / "release", None)
        self.assertEqual("passed", result["status"])
        self.assertFalse((self.root / "release/.env").exists())
        self.assertFalse((self.root / "release/agent-service/data").exists())
        self.assertFalse((self.root / "release/sky-server/target").exists())
        self.assertTrue((self.root / "release/frontend/app/src/main.ts").exists())

    def test_blocks_real_credential_even_in_test_file(self):
        secret = "private-test-value-" + "1234567890abcdef"
        env = self.root / "private.env"
        env.write_text("LLM_API_KEY=" + secret)
        self.write("sky-server/src/test/resources/config.txt", secret)
        result = module.export(self.project, self.root / "release", env)
        self.assertEqual("blocked", result["status"])
        self.assertFalse((self.root / "release").exists())
        self.assertNotIn(secret, str(result))

    def test_rejects_existing_output(self):
        dest = self.root / "release"
        dest.mkdir()
        with self.assertRaises(ValueError):
            module.export(self.project, dest, None)

    def test_rejects_output_inside_source(self):
        with self.assertRaises(ValueError):
            module.export(self.project, self.project / "release", None)

    def test_task_identifier_is_not_an_api_key(self):
        self.assertEqual([], module.scan_text('task-owned-by-another-administrator', Path("source.java"), []))

    def test_short_password_assignment_is_detected_but_identifier_is_not(self):
        self.assertEqual([], module.scan_text('private String password;', Path("source.java"), ["password"]))
        self.assertTrue(module.scan_text('DB_PASSWORD=password', Path("config.env"), ["password"]))

    def test_blocks_private_table_records(self):
        self.write("deploy/mysql/dump.sql", "INSERT INTO `employee` VALUES (1, 'example');")
        result = module.export(self.project, self.root / "release", None)
        self.assertEqual("blocked", result["status"])


if __name__ == "__main__":
    unittest.main()
