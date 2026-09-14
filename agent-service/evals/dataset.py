from __future__ import annotations

import json
from pathlib import Path
from typing import Any

"""
    数据集加载器。这个脚本负责读取和解析 baseline.json 文件，
    将其转换成程序可以方便使用的数据结构（比如一个对象列表），
    为评估运行做准备。
"""

REQUIRED_CATEGORIES = {
    "exact_fact", "paraphrase", "multi_document", "no_evidence",
    "unauthorized", "prompt_injection",
}


def load_dataset(path: str | Path) -> dict[str, Any]:
    source = Path(path)
    data = json.loads(source.read_text(encoding="utf-8"))
    cases = data.get("cases", [])
    documents = data.get("documents", [])
    errors = []
    if not 50 <= len(cases) <= 100:
        errors.append(f"cases must contain 50-100 entries, got {len(cases)}")
    case_ids = [case.get("id") for case in cases]
    if len(case_ids) != len(set(case_ids)):
        errors.append("case ids must be unique")
    document_ids = {document.get("id") for document in documents}
    if None in document_ids or len(document_ids) != len(documents):
        errors.append("document ids must be present and unique")
    missing_categories = REQUIRED_CATEGORIES - {case.get("category") for case in cases}
    if missing_categories:
        errors.append("missing categories: " + ", ".join(sorted(missing_categories)))
    for case in cases:
        prefix = f"case {case.get('id', '<unknown>')}"
        for field in ("question", "kb_id", "allowed_sources", "should_refuse"):
            if field not in case:
                errors.append(f"{prefix}: missing {field}")
        referenced = set(case.get("expected_sources", [])) | set(case.get("allowed_sources", [])) | set(case.get("forbidden_sources", []))
        unknown = referenced - document_ids
        if unknown:
            errors.append(f"{prefix}: unknown sources {sorted(unknown)}")
        if not case.get("should_refuse") and not case.get("answer_points"):
            errors.append(f"{prefix}: answerable case needs answer_points")
    if errors:
        raise ValueError("invalid evaluation dataset:\n- " + "\n- ".join(errors))
    return data
