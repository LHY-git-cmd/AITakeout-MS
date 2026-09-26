"""运行用户Agent确定性评测，输出路由、槽位和工具权限的回归报告。"""
import argparse
import asyncio
import json
from pathlib import Path

from app.tools.definitions import build_default_registry
from app.user_agent.graph import UserAgentWorkflow


def load_dataset(path: Path) -> dict:
    """加载并校验合成评测集，避免评测缺少关键字段后静默通过。"""
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("version") != 1 or not isinstance(data.get("cases"), list):
        raise ValueError("user agent dataset version/cases invalid")
    return data


async def evaluate(data: dict) -> dict:
    """执行每条工作流用例，并汇总可发布门禁。"""
    workflow = UserAgentWorkflow()
    registry = build_default_registry()
    rows = []
    for case in data["cases"]:
        if case["category"] == "permission":
            names = set(registry.names_for_role(case["actor_role"]))
            failures = [name for name in case.get("allowed_tools", []) if name not in names]
            failures += [name for name in case.get("forbidden_tools", []) if name in names]
            rows.append({"id": case["id"], "passed": not failures, "failures": failures})
            continue
        result = await workflow.run(
            task_id=f"eval-{case['id']}", session_id=None,
            query=case["query"], context=None,
        )
        failures = []
        if case.get("error"):
            if result.get("error", {}).get("code") != case["error"]:
                failures.append("error")
        else:
            if result.get("intent") != case["intent"]:
                failures.append("intent")
            for key, expected in case.get("slots", {}).items():
                if result.get("slots", {}).get(key) != expected:
                    failures.append(f"slot:{key}")
        rows.append({"id": case["id"], "passed": not failures, "failures": failures})
    passed = sum(row["passed"] for row in rows)
    return {
        "case_count": len(rows), "passed_count": passed,
        "pass_rate": round(passed / len(rows), 4) if rows else 0,
        "gates": {"routing_and_security": passed == len(rows)},
        "passed": passed == len(rows), "cases": rows,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", type=Path, default=Path(__file__).with_name("user_agent_baseline.json"))
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = asyncio.run(evaluate(load_dataset(args.dataset)))
    payload = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.write_text(payload, encoding="utf-8")
    else:
        print(payload, end="")
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
