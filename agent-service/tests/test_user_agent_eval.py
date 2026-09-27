"""验证用户Agent评测集本身可执行且路由、越权和注入门禁全部通过。"""
import asyncio
import json
import unittest
from pathlib import Path

from evals.user_agent_run import evaluate, load_dataset


class UserAgentEvaluationTest(unittest.TestCase):
    def test_baseline_passes_all_security_and_routing_cases(self):
        dataset = load_dataset(Path(__file__).parents[1] / "evals" / "user_agent_baseline.json")
        report = asyncio.run(evaluate(dataset))
        self.assertEqual(12, report["case_count"])
        self.assertEqual(12, report["passed_count"])
        self.assertTrue(report["gates"]["routing_and_security"])


if __name__ == "__main__":
    unittest.main()
