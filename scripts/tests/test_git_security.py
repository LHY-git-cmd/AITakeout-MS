"""在独立临时 Git 仓库验证推送检查，尤其验证最新提交删除文件后历史仍会被拦截。"""
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('git_security', Path(__file__).resolve().parents[1] / 'check-git-security.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class GitSecurityTest(unittest.TestCase):
    def setUp(self):
        self.previous = Path.cwd()
        self.temp = tempfile.TemporaryDirectory(prefix='git-security-test-')
        self.root = Path(self.temp.name).resolve()
        os.chdir(self.root)
        self.git('init', '-q')
        self.git('config', 'user.name', 'Security Test')
        self.git('config', 'user.email', 'security-test@example.invalid')

    def tearDown(self):
        os.chdir(self.previous)
        # 只清理本测试创建的临时目录；绝不把调用者的项目目录作为清理目标。
        assert self.root.parent == Path(tempfile.gettempdir()).resolve()
        assert self.root.name.startswith('git-security-test-')
        self.temp.cleanup()

    def git(self, *args):
        return subprocess.run(['git', *args], check=True, capture_output=True).stdout

    def commit(self, name, value):
        p = self.root / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(value, encoding='utf-8')
        self.git('add', '--', name)
        self.git('commit', '-q', '-m', 'security test fixture')

    def test_safe_history_passes(self):
        self.commit('README.md', 'Public documentation')
        self.assertEqual([], module.inspect(['HEAD'])[1])

    def test_deleted_private_file_still_blocks_push(self):
        self.commit('.env', 'LOCAL_SETTING=fixture')
        self.git('rm', '--', '.env')
        self.git('commit', '-q', '-m', 'delete fixture')
        self.assertIn(('.env', 'private-file-in-history'), module.inspect(['HEAD'])[1])

    def test_api_key_in_regular_source_is_rejected(self):
        self.commit('app.py', 'key = "' + 'sk-' + 'x' * 32 + '"')
        self.assertIn(('app.py', 'credential-format'), module.inspect(['HEAD'])[1])

    def test_config_empty_values_do_not_consume_following_lines(self):
        content = b'CHECKOUT_PREVIEW_SECRET=\nTOOL_TIMEOUT=300\n'
        self.assertFalse(module.configuration_leaks('.env.example', content))
        self.assertTrue(module.configuration_leaks('sky-server/src/main/resources/application-dev.yml', b'password: literal-fixture'))

    def test_simulation_fixtures_are_allowed_but_uploaded_documents_are_not(self):
        self.assertFalse(module.private_path('data/diet-seed/example.csv'))
        self.assertTrue(module.private_path('data/knowledge/upload.txt'))


if __name__ == '__main__':
    unittest.main()
