"""验证真实输出的 JSON 日志遮盖凭据，保留状态和耗时等诊断信息。"""
import json
import logging
import unittest

from app.core.logging_config import JsonFormatter


class LogRedactionTest(unittest.TestCase):
    def test_masks_formatted_arguments_and_personal_numbers(self):
        token = "eyJ" + "sampleheader.samplepayload.samplesignature"
        record = logging.LogRecord("test", logging.INFO, "", 0,
                                   "token=%s phone=%s status=401", (token, "13800138000"), None)
        output = JsonFormatter().format(record)
        self.assertNotIn(token, output)
        self.assertNotIn("13800138000", output)
        self.assertIn("status=401", json.loads(output)["event"])

    def test_masks_key_and_preserves_numeric_metrics(self):
        key = "sk-" + "x" * 32
        record = logging.LogRecord("test", logging.WARNING, "", 0, "provider failed %s", (key,), None)
        record.elapsed_ms = 15
        output = JsonFormatter().format(record)
        self.assertNotIn(key, output)
        self.assertEqual(15, json.loads(output)["elapsed_ms"])


if __name__ == "__main__":
    unittest.main()
