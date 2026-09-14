import unittest

from app.core.agent import PythonAgent
from app.core.config import Settings


class ConfigSecurityTest(unittest.TestCase):
    # 测试启动验证是否在不泄露敏感值的情况下，正确列出配置项名称
    # 验证在启动配置检查时，如果出现错误（如此处的LLM_BASE_URL无效），
    # 错误日志会指明是哪个配置项出了问题，但不会打印出该配置项的敏感内容（如LLM_API_KEY）。
    # 这是为了防止API密钥等敏感信息泄露到日志中。
    def test_startup_validation_lists_names_without_values(self):
        secret = "should-never-be-printed"
        config = Settings(
            _env_file=None,
            LLM_PROVIDER="deepseek",
            LLM_BASE_URL="not-a-url",
            LLM_MODEL="model",
            LLM_API_KEY=secret,
        )

        with self.assertRaises(RuntimeError) as raised:
            config.validate_startup()

        self.assertIn("LLM_BASE_URL", str(raised.exception))
        self.assertNotIn(secret, str(raised.exception))

    # 测试LLM请求日志是否排除了prompt和历史记录
    # 验证在记录LLM（大语言模型）请求的日志时，不会包含实际的对话内容（如系统提示或用户问题）。
    # 这是一项重要的安全措施，旨在保护用户隐私和数据安全，防止敏感对话内容被记录到日志里。

    def test_llm_request_log_excludes_prompt_and_history(self):
        messages = [
            {"role": "system", "content": "private system prompt"},
            {"role": "user", "content": "private employee question"},
        ]

        with self.assertLogs("sky.agent.llm", level="INFO") as captured:
            PythonAgent._log_llm_request(
                "task-1", "session-1", "model-1", 0.2, messages, True)

        output = "\n".join(captured.output)
        record = captured.records[0]
        self.assertEqual("task-1", record.task_id)
        self.assertEqual(46, record.prompt_chars)
        self.assertNotIn("private system prompt", output)
        self.assertNotIn("private employee question", output)


if __name__ == "__main__":
    unittest.main()