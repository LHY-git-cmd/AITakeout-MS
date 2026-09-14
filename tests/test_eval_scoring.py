import json
import tempfile
import unittest
from pathlib import Path

from evals.dataset import REQUIRED_CATEGORIES, load_dataset
from evals.scoring import CaseResult, percentile, summarize


class EvalDatasetTest(unittest.TestCase):
    def test_baseline_dataset_is_valid_and_covers_required_categories(self):
        path = Path(__file__).parents[1] / "evals" / "baseline.json"
        data = load_dataset(path)
        self.assertGreaterEqual(len(data["cases"]), 50)
        self.assertLessEqual(len(data["cases"]), 100)
        self.assertEqual(REQUIRED_CATEGORIES,
                         {case["category"] for case in data["cases"]})

    def test_invalid_dataset_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bad.json"
            path.write_text(json.dumps({"documents": [], "cases": []}), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "50-100"):
                load_dataset(path)


class EvalScoringTest(unittest.TestCase):
    def test_percentile_uses_linear_interpolation(self):
        self.assertEqual(2.5, percentile([1, 2, 3, 4], 0.5))
        self.assertEqual(3.85, percentile([1, 2, 3, 4], 0.95))

    def test_release_gates_detect_leak_and_wrong_citation(self):
        result = CaseResult(
            case_id="auth-1", category="unauthorized", should_refuse=True,
            expected_sources=set(), allowed_sources={"public"},
            forbidden_sources={"secret"}, answer_points=[],
            retrieved_sources=["secret"], cited_sources=["secret"],
            answer="机密内容", refused=False,
        )
        report = summarize([result], "mock")
        self.assertFalse(report["passed"])
        self.assertEqual(1, report["metrics"]["unauthorized_leak_count"])
        self.assertFalse(report["gates"]["refusal_accuracy"])

    def test_answer_points_accept_alternatives(self):
        result = CaseResult(
            case_id="fact-1", category="exact_fact", should_refuse=False,
            expected_sources={"menu"}, allowed_sources={"menu"},
            forbidden_sources=set(), answer_points=[["38元", "38.00元"]],
            retrieved_sources=["menu"], cited_sources=["menu"],
            answer="售价为 38.00 元。[来源1]",
        )
        row = result.to_dict()
        self.assertEqual(1.0, row["answer_point_coverage"])
        self.assertTrue(row["passed"])


if __name__ == "__main__":
    unittest.main()
