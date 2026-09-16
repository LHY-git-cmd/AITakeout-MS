"""用途：加载并严格校验隔离工具评测数据，确定性分层抽样。

输入：JSON 数据集路径；输出：已校验字典/用例列表。外部调用：无。
供 tool_run 导入；契约错误抛 ValueError，由入口转换为退出码 2。
"""
import json
from collections import Counter
from pathlib import Path

from app.tools.definitions import build_default_registry
from app.tools.models import AdminRole


CATEGORY_MINIMUMS = {"read_only": 10, "no_tool": 5, "missing_arguments": 5,
                     "multi_step": 6, "write_confirmation": 6,
                     "unauthorized": 4, "tool_failure": 4}
DEFAULT_DATASET = Path(__file__).with_name("tool_baseline.json")


def load_dataset(path=DEFAULT_DATASET):
    data = json.loads(Path(path).read_text(encoding="utf-8-sig"))
    validate_dataset(data)
    return data


def validate_dataset(data):
    if not isinstance(data, dict) or data.get("version") != 1:
        raise ValueError("dataset version must be 1")
    cases = data.get("cases")
    if not isinstance(cases, list) or len(cases) < 40:
        raise ValueError("dataset must contain at least 40 cases")
    registry, ids, counts = build_default_registry(), set(), Counter()
    required = {"id", "category", "actor_role", "allowed_roles", "question",
                "expected_tool_sequence", "argument_assertions", "confirmation_required",
                "forbidden_tools", "forbidden_output", "answer_points",
                "expected_terminal_state", "fixture", "mock"}
    for case in cases:
        if not isinstance(case, dict) or not required.issubset(case):
            raise ValueError("case is missing required fields")
        identity = case["id"]
        if not isinstance(identity, str) or not identity or identity in ids:
            raise ValueError("case IDs must be unique nonempty strings")
        ids.add(identity)
        if case["category"] not in CATEGORY_MINIMUMS:
            raise ValueError(f"{identity}: unknown category")
        counts[case["category"]] += 1
        role = AdminRole(case["actor_role"])
        if not case["allowed_roles"] or any(r not in AdminRole for r in case["allowed_roles"]):
            raise ValueError(f"{identity}: invalid allowed roles")
        if role not in case["allowed_roles"]:
            raise ValueError(f"{identity}: actor outside allowed roles")
        if not isinstance(case["question"], str) or not case["question"].strip():
            raise ValueError(f"{identity}: question required")
        sequence, assertions = case["expected_tool_sequence"], case["argument_assertions"]
        if not isinstance(sequence, list) or not isinstance(assertions, list) or len(sequence) != len(assertions):
            raise ValueError(f"{identity}: parameter assertions must align with tool sequence")
        writes = False
        for name, arguments in zip(sequence, assertions):
            definition = registry.definition_for_role(name, role)
            registry.validate_arguments(name, role, arguments)
            writes |= definition.requires_confirmation
        if type(case["confirmation_required"]) is not bool or case["confirmation_required"] != writes:
            raise ValueError(f"{identity}: confirmation expectation contradicts production schema")
        if case["category"] == "read_only" and (not sequence or writes):
            raise ValueError(f"{identity}: read-only case requires read tools")
        if case["expected_terminal_state"] not in {"completed", "clarification", "refused", "rejected", "expired", "failed"}:
            raise ValueError(f"{identity}: invalid terminal state")
        for key in ("forbidden_tools", "forbidden_output"):
            if not isinstance(case[key], list) or any(not isinstance(v, str) or not v for v in case[key]):
                raise ValueError(f"{identity}: invalid {key}")
        if not isinstance(case["answer_points"], list) or any(
                not isinstance(point, list) or not point or any(not isinstance(s, str) or not s for s in point)
                for point in case["answer_points"]):
            raise ValueError(f"{identity}: invalid answer points")
        if not sequence and not case["answer_points"]:
            raise ValueError(f"{identity}: no-tool cases need answer assertions")
        if case["category"] == "multi_step" and len(sequence) < 2:
            raise ValueError(f"{identity}: multistep case needs multiple tools")
        fixture, mock = case["fixture"], case["mock"]
        if fixture.get("confirmation") not in {"CONFIRMED", "REJECTED", "EXPIRED"}:
            raise ValueError(f"{identity}: invalid confirmation fixture")
        if not isinstance(fixture.get("fail_tools"), list) or not isinstance(fixture.get("denied_kb_ids"), list):
            raise ValueError(f"{identity}: invalid tool fixtures")
        if not isinstance(mock.get("turns"), list) or not isinstance(mock.get("answer"), str):
            raise ValueError(f"{identity}: invalid mock transcript")
        for turn in mock["turns"]:
            if not isinstance(turn, list) or not turn or any(
                    not isinstance(call, dict) or not isinstance(call.get("name"), str)
                    or not isinstance(call.get("arguments"), dict) for call in turn):
                raise ValueError(f"{identity}: invalid mock tool turn")
    for category, minimum in CATEGORY_MINIMUMS.items():
        if counts[category] < minimum:
            raise ValueError(f"{category}: requires at least {minimum} cases")


def select_cases(cases, mode, sample_size=12):
    if mode == "mock":
        return list(cases)
    if mode != "real" or sample_size < 12 or sample_size > len(cases):
        raise ValueError("real sample size must be between 12 and dataset size")
    # 首轮涵盖七类，第二轮优先确认、越权、失败、多步骤，再补只读。
    order = ["write_confirmation", "unauthorized", "tool_failure", "multi_step",
             "read_only", "no_tool", "missing_arguments"]
    groups = {category: [c for c in cases if c["category"] == category] for category in order}
    selected = []
    while len(selected) < sample_size:
        for category in order:
            if groups[category] and len(selected) < sample_size:
                selected.append(groups[category].pop(0))
    return selected
