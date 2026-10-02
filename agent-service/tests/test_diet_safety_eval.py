import unittest

from evals.diet_safety_cases import build_cases


class DietSafetyEvaluationSetTest(unittest.TestCase):
    def test_contains_at_least_two_hundred_unique_observable_cases(self):
        cases = build_cases()
        ids = [case["id"] for case in cases]

        self.assertGreaterEqual(len(cases), 200)
        self.assertEqual(len(ids), len(set(ids)))
        self.assertTrue(all(case.get("expected") for case in cases))
        self.assertTrue(any(case["category"] == "allergen" for case in cases))
        self.assertTrue(any(case["category"] == "medical_risk" for case in cases))


if __name__ == "__main__":
    unittest.main()
