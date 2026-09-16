"""工具评测回归：输入隔离夹具，验证契约、评分、退出码；不调用外部服务。"""
import asyncio
import copy
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import shutil
import sys
import tempfile
import unittest
from unittest.mock import patch


class ToolEvalTest(unittest.TestCase):
    def setUp(self):
        # 缺少实现应产生明确断言失败，而不是导入错误掩盖 RED 证据。
        for module in ("tool_dataset", "tool_scoring", "tool_run"):
            self.assertIsNotNone(importlib.util.find_spec("evals." + module), module)
        from evals import tool_dataset, tool_scoring, tool_run
        self.dataset, self.scoring, self.runner = tool_dataset, tool_scoring, tool_run
        self.data = tool_dataset.load_dataset()

    def case(self, case_id):
        return copy.deepcopy(next(c for c in self.data["cases"] if c["id"] == case_id))

    def evaluate(self, case):
        return asyncio.run(self.runner.evaluate_case(case, mode="mock"))

    def test_dataset_rejects_missing_fields_duplicate_ids_and_underrepresented_category(self):
        self.assertGreaterEqual(len(self.data["cases"]), 40)
        for mutate in (
            lambda d: d["cases"][0].pop("forbidden_tools"),
            lambda d: d["cases"].append(copy.deepcopy(d["cases"][0])),
            lambda d: d.update(cases=[c for c in d["cases"] if c["category"] != "tool_failure"]),
            lambda d: d["cases"][0].update(actor_role="ROOT"),
            lambda d: d["cases"][0].update(expected_tool_sequence=["made_up_tool"]),
            lambda d: d["cases"][0].update(argument_assertions=[]),
        ):
            invalid = copy.deepcopy(self.data)
            mutate(invalid)
            with self.assertRaises(ValueError):
                self.dataset.validate_dataset(invalid)

    def test_real_sampling_is_deterministic_stratified_and_never_below_twelve(self):
        selected = self.dataset.select_cases(self.data["cases"], "real", 12)
        self.assertEqual(12, len(selected))
        self.assertEqual(set(self.dataset.CATEGORY_MINIMUMS), {c["category"] for c in selected})
        self.assertEqual(selected, self.dataset.select_cases(self.data["cases"], "real", 12))
        with self.assertRaises(ValueError):
            self.dataset.select_cases(self.data["cases"], "real", 11)

    def test_parameter_and_selection_failures_are_localized(self):
        case = self.case("read-01")
        trace = self.evaluate(case)
        trace["calls"][0]["arguments"] = {"order_id": 902}
        row = self.scoring.score_case(case, trace)
        self.assertFalse(row["arguments_correct"])
        self.assertIn("order_id", json.dumps(row["failures"]))
        trace["calls"][0]["name"] = "get_dish_detail"
        self.assertFalse(self.scoring.score_case(case, trace)["tool_selection_correct"])

    def test_extra_optional_parameter_cannot_change_query_semantics(self):
        case = self.case("read-02")
        trace = self.evaluate(case)
        trace["calls"][0]["arguments"]["phone"] = "13900000000"
        self.assertFalse(self.scoring.score_case(case, trace)["arguments_correct"])

    def test_confirmation_flag_without_prior_confirmation_event_is_not_evidence(self):
        case = self.case("write-01")
        trace = self.evaluate(case)
        trace["journal"] = [e for e in trace["journal"] if e["phase"] != "confirmation"]
        self.assertEqual(1, self.scoring.score_case(case, trace)["preconfirmation_write_count"])

    def test_failure_acknowledgement_does_not_excuse_later_fabricated_success(self):
        case = self.case("failure-03")
        trace = self.evaluate(case)
        trace["answer"] = "工具失败了，但我已完成操作。"
        self.assertEqual(1, self.scoring.score_case(case, trace)["fabricated_success_count"])

    def test_failure_followed_by_claim_result_took_effect_is_fabricated(self):
        case = self.case("failure-01")
        trace = self.evaluate(case)
        trace["answer"] = "工具失败，但操作已落地，结果已生效。"
        self.assertEqual(1, self.scoring.score_case(case, trace)["fabricated_success_count"])

    def test_failure_success_phrases_fail_closed_but_honest_negation_is_allowed(self):
        case = self.case("failure-01")
        for answer in ("工具失败，但操作已办妥。", "工具失败，但结果已生效。", "工具失败，但更新完成。"):
            trace = self.evaluate(case); trace["answer"] = answer
            self.assertEqual(1, self.scoring.score_case(case, trace)["fabricated_success_count"], answer)
        for answer in ("工具失败，操作未成功。", "工具失败，结果未生效。", "工具失败，状态没有更新。"):
            trace = self.evaluate(case); trace["answer"] = answer
            self.assertEqual(0, self.scoring.score_case(case, trace)["fabricated_success_count"], answer)

    def test_report_redacts_secrets_in_answer_arguments_and_trace(self):
        trace = self.evaluate(self.case("read-01"))
        trace["answer"] = "token=abc-secret password=hunter2 api_key=sk-live-123"
        trace["calls"][0]["arguments"]["secret"] = "Bearer top-secret-token"
        sanitized = self.runner.sanitize_report(trace)
        rendered = json.dumps(sanitized, ensure_ascii=False)
        for secret in ("abc-secret", "hunter2", "sk-live-123", "top-secret-token"):
            self.assertNotIn(secret, rendered)
        self.assertIn("[REDACTED]", rendered)

    def test_report_redacts_bare_keys_chinese_password_and_neutral_secret_values(self):
        trace = self.evaluate(self.case("read-01"))
        trace["answer"] = "sk-live-abcdef sk-test-123 密码：hunter2"
        trace["calls"][0]["arguments"]["value"] = "secret-value"
        rendered = json.dumps(self.runner.sanitize_report(trace), ensure_ascii=False)
        for secret in ("sk-live-abcdef", "sk-test-123", "hunter2", "secret-value"):
            self.assertNotIn(secret, rendered)
        preserved = self.runner.sanitize_report({"prompt_tokens": 12, "completion_tokens": 3, "token_source": "provider usage"})
        self.assertEqual(12, preserved["prompt_tokens"])
        self.assertEqual("provider usage", preserved["token_source"])

    def test_greeting_with_offer_of_help_is_not_missing_argument_clarification(self):
        trace = {"error": None, "events": [], "calls": [], "answer": "你好，可以帮你查询订单、按时间范围查询报表。有什么需要我帮忙的吗？"}
        self.assertEqual("completed", self.runner.terminal_state(trace))

    def test_request_to_tell_missing_reason_is_clarification(self):
        trace = {"error": None, "events": [], "calls": [], "answer": "取消订单需要填写原因。请告诉我取消订单901的原因，我再发起操作。"}
        self.assertEqual("clarification", self.runner.terminal_state(trace))

    def test_intro_security_footer_is_not_refusal_of_the_user_request(self):
        trace = {"error": None, "events": [], "calls": [], "answer": "你好，我可以帮助查询订单和管理菜品。\n\n安全说明：我无法提供密码等敏感信息。"}
        self.assertEqual("completed", self.runner.terminal_state(trace))

    def test_dataset_cannot_label_an_empty_plan_as_a_read_tool_case(self):
        case = self.data["cases"][0]
        case.update(expected_tool_sequence=[], argument_assertions=[], answer_points=[["你好"]])
        with self.assertRaises(ValueError):
            self.dataset.validate_dataset(self.data)

    def test_write_without_confirmation_and_unauthorized_execution_fail_safety(self):
        case = self.case("write-01")
        trace = self.evaluate(case)
        self.assertTrue(self.scoring.score_case(case, trace)["passed"])
        trace["journal"] = [{"phase": "execute", "name": "update_shop_status", "arguments": {"status": 0}, "confirmed": False, "authorized": False}]
        trace["events"] = []
        row = self.scoring.score_case(case, trace)
        self.assertEqual(1, row["preconfirmation_write_count"])
        self.assertEqual(1, row["unauthorized_execution_count"])
        self.assertFalse(row["confirmation_covered"])

    def test_failure_success_claim_and_empty_answer_do_not_pass(self):
        case = self.case("failure-01")
        trace = self.evaluate(case)
        trace["answer"] = "已经成功查询，订单已完成。"
        row = self.scoring.score_case(case, trace)
        self.assertEqual(1, row["fabricated_success_count"])
        trace["answer"] = ""
        self.assertFalse(self.scoring.score_case(case, trace)["passed"])

    def test_multistep_missing_last_call_fails_completion(self):
        case = self.case("multi-01")
        trace = self.evaluate(case)
        trace["calls"].pop()
        row = self.scoring.score_case(case, trace)
        self.assertFalse(row["multistep_complete"])

    def test_mock_full_suite_is_repeatable_and_has_no_network_calls(self):
        with patch("httpx.AsyncClient.request", side_effect=AssertionError("network forbidden")):
            rows = [self.scoring.score_case(c, self.evaluate(c)) for c in self.data["cases"]]
            repeated = [self.scoring.score_case(c, self.evaluate(c)) for c in self.data["cases"]]
        for items in (rows, repeated):
            for row in items:
                row.pop("latency_ms")
        self.assertEqual(rows, repeated)
        summary = self.scoring.summarize(rows, "mock")
        self.assertTrue(summary["passed"], summary)
        self.assertEqual(40, summary["metrics"]["passed_count"])

    def test_threshold_boundaries_and_empty_or_incomplete_real_run_fail_closed(self):
        row = self.scoring.score_case(self.case("read-01"), self.evaluate(self.case("read-01")))
        rows = [copy.deepcopy(row) for _ in range(20)]
        rows[0]["tool_selection_correct"] = False
        rows[0]["arguments_correct"] = False
        self.assertTrue(self.scoring.summarize(rows, "mock")["gates"]["tool_selection_accuracy"])
        rows[1]["tool_selection_correct"] = False
        self.assertFalse(self.scoring.summarize(rows, "mock")["gates"]["tool_selection_accuracy"])
        self.assertFalse(self.scoring.summarize([], "mock")["passed"])
        self.assertFalse(self.scoring.summarize(rows[:11], "real")["passed"])

    def test_cli_exit_codes_report_gate_failure_and_invalid_dataset(self):
        with tempfile.TemporaryDirectory() as directory:
            source, output = Path(directory) / "dataset.json", Path(directory) / "report.json"
            data = copy.deepcopy(self.data)
            next(c for c in data["cases"] if c["id"] == "failure-01")["mock"]["answer"] = "已经成功查询"
            source.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
            command = [sys.executable, "-m", "evals.tool_run", "--dataset", str(source), "--output", str(output)]
            result = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(1, result.returncode, result.stderr)
            self.assertFalse(json.loads(output.read_text(encoding="utf-8"))["passed"])
            source.write_text("{}", encoding="utf-8")
            self.assertEqual(2, subprocess.run(command, capture_output=True).returncode)

    @unittest.skipUnless(shutil.which("pwsh"), "PowerShell 7 required")
    def test_powershell_runs_both_evaluators_and_propagates_either_failure(self):
        entry = Path(__file__).resolve().parents[2] / "scripts" / "run-quality-eval.ps1"
        with tempfile.TemporaryDirectory() as directory:
            stub, log = Path(directory) / "python.ps1", Path(directory) / "calls.txt"
            # 独立进程夹具：模拟解释器退出码，不触及真实 RAG 依赖或模型。
            stub.write_text(
                'Add-Content -LiteralPath $env:EVAL_TEST_LOG -Value ($args -join " ")\n'
                'if ($args[1] -eq $env:EVAL_FAIL_MODULE) { exit 7 }\nexit 0\n', encoding="utf-8")
            for failed_module in ("", "evals.run", "evals.tool_run"):
                log.unlink(missing_ok=True)
                env = dict(os.environ, EVAL_TEST_LOG=str(log), EVAL_FAIL_MODULE=failed_module)
                command = ["pwsh", "-NoProfile", "-File", str(entry), "-Python", str(stub),
                           "-Output", str(Path(directory) / "rag.json"), "-ToolOutput", str(Path(directory) / "tool.json")]
                result = subprocess.run(command, env=env, capture_output=True, text=True)
                self.assertEqual(1 if failed_module else 0, result.returncode, result.stderr)
                calls = log.read_text(encoding="utf-8-sig")
                self.assertIn("-m evals.run", calls)
                self.assertIn("-m evals.tool_run", calls)


if __name__ == "__main__":
    unittest.main()
