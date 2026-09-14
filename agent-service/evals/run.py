from __future__ import annotations

import argparse
import asyncio
import json
import os
import tempfile
import time
import uuid
from pathlib import Path

import httpx

from app.core.agent import PythonAgent
from app.core.config import settings
from app.knowledge.embedding import HttpEmbeddingProvider
from app.knowledge.service import KnowledgeService
from app.knowledge.vector_store import QdrantVectorStore
from app.llm.gateway import LLMCallStats
from evals.dataset import REQUIRED_CATEGORIES, load_dataset
from evals.scoring import CaseResult, REFUSAL_MARKERS, summarize

"""
评估执行器。这是启动整个评估流程的入口脚本。它会协调所有步骤：
使用 dataset.py 加载评估问题。
调用你的 Agent 或 LLM，让它回答所有问题。
将模型的回答和标准答案一起交给 scoring.py 进行评分。
最后输出评估报告。
"""

def parse_args():
    parser = argparse.ArgumentParser(description="运行隔离的 RAG/LLM 质量回归")
    parser.add_argument("--dataset", default=str(Path(__file__).with_name("baseline.json")))
    parser.add_argument("--mode", choices=("mock", "real"), default="mock")
    parser.add_argument("--qdrant-url", default=os.getenv("QDRANT_URL", "http://127.0.0.1:6333"))
    parser.add_argument("--embedding-url", default=os.getenv("EMBEDDING_BASE_URL", "http://127.0.0.1:8001"))
    parser.add_argument("--output", default="build/reports/rag-eval.json")
    parser.add_argument("--real-sample-size", type=int, default=12)
    parser.add_argument("--keep-collection", action="store_true")
    parser.add_argument("--no-gate", action="store_true", help="报告不达标时仍返回 0")
    return parser.parse_args()


def select_cases(cases: list[dict], mode: str, sample_size: int) -> list[dict]:
    if mode == "mock":
        return cases
    if sample_size < len(REQUIRED_CATEGORIES):
        raise ValueError(f"real sample size must be at least {len(REQUIRED_CATEGORIES)}")
    selected, seen = [], set()
    for category in sorted(REQUIRED_CATEGORIES):
        case = next(item for item in cases if item["category"] == category)
        selected.append(case)
        seen.add(case["id"])
    for case in cases:
        if len(selected) >= min(sample_size, len(cases)):
            break
        if case["id"] not in seen:
            selected.append(case)
            seen.add(case["id"])
    return selected


async def wait_for_dependencies(qdrant_url: str, embedding_url: str):
    async with httpx.AsyncClient(timeout=10) as client:
        qdrant = await client.get(f"{qdrant_url.rstrip('/')}/healthz")
        qdrant.raise_for_status()
        embedding = await client.get(f"{embedding_url.rstrip('/')}/health")
        embedding.raise_for_status()
        dimension = embedding.json().get("dimension")
        if dimension != 1024:
            raise RuntimeError(f"embedding dimension must be 1024, got {dimension}")


async def index_documents(service: KnowledgeService, documents: list[dict], run_id: str):
    versions = {}
    for document in documents:
        request = {
            "task_id": f"eval-index-{run_id}-{document['id']}",
            "request_hash": f"{run_id}-{document['id']}",
            "kb_id": document["kb_id"],
            "document_id": document["id"],
            "document_version": document["version"],
            "file_name": document["file_name"],
            "file_type": "txt",
            "embedding_model": "BAAI/bge-m3",
        }
        await service.submit(request, document["content"].encode("utf-8"))
        for _ in range(240):
            status = service.status(request["task_id"])
            if status["status"] in {"completed", "failed"}:
                break
            await asyncio.sleep(0.25)
        if status["status"] != "completed":
            raise RuntimeError(f"index failed for {document['id']}: {status.get('error_msg')}")
        versions.setdefault(document["kb_id"], {})[document["id"]] = document["version"]
    return versions


def mock_answer(case: dict, citations: list[dict]) -> tuple[str, int, int]:
    points = [alternatives[0] for alternatives in case.get("answer_points", [])]
    sources = " ".join(f"[来源{index}]" for index in range(1, len(citations) + 1))
    answer = "；".join(points) + (f"。{sources}" if sources else "")
    return answer, max(1, len(case["question"]) // 2), max(1, len(answer) // 2)


async def real_answer(agent: PythonAgent, case: dict, rag_context: str):
    messages = agent._build_client_and_messages(case["question"], None)
    messages.insert(-1, {"role": "system", "content": rag_context})
    stats = LLMCallStats(model=settings.LLM_MODEL, stream=False,
                         started_at=time.perf_counter())
    response = await agent.llm.complete(
        messages, model=settings.LLM_MODEL, temperature=0.2, stats=stats)
    content = response.choices[0].message.content or ""
    usage = getattr(response, "usage", None)
    return (
        content,
        getattr(usage, "prompt_tokens", None) or stats.prompt_tokens or 0,
        getattr(usage, "completion_tokens", None) or stats.completion_tokens or 0,
    )


async def evaluate_case(service: KnowledgeService, agent: PythonAgent,
                        versions: dict, case: dict, mode: str) -> CaseResult:
    started = time.perf_counter()
    result = CaseResult(
        case_id=case["id"], category=case["category"],
        should_refuse=case["should_refuse"],
        expected_sources=set(case.get("expected_sources", [])),
        allowed_sources=set(case["allowed_sources"]),
        forbidden_sources=set(case.get("forbidden_sources", [])),
        answer_points=case.get("answer_points", []),
    )
    try:
        knowledge = {
            "kb_id": case["kb_id"],
            "document_versions": versions.get(case["kb_id"], {}),
            "top_k": case.get("top_k", max(1, len(case.get("expected_sources", [])))),
            "score_threshold": case.get("score_threshold", 0.35),
        }
        raw = await service.search(
            knowledge["kb_id"], case["question"], knowledge["document_versions"],
            knowledge["top_k"], knowledge["score_threshold"])
        result.retrieved_sources = [item["document_id"] for item in raw]
        rag_context, citations, refusal = await agent.prepare_rag(
            case["question"], None, knowledge)
        result.cited_sources = [item["document_id"] for item in citations]
        if refusal:
            result.answer = refusal
            result.refused = True
        elif mode == "mock":
            result.llm_attempted = True
            result.answer, result.prompt_tokens, result.completion_tokens = mock_answer(case, citations)
        else:
            result.llm_attempted = True
            result.answer, result.prompt_tokens, result.completion_tokens = await real_answer(
                agent, case, rag_context)
            result.refused = any(marker in result.answer for marker in REFUSAL_MARKERS)
        result.forbidden_output = [
            marker for marker in case.get("forbidden_output", [])
            if marker.lower() in result.answer.lower()
        ]
    except Exception as exc:
        result.call_succeeded = False
        result.error = f"{type(exc).__name__}: {exc}"
    result.latency_ms = (time.perf_counter() - started) * 1000
    return result


async def delete_collection(qdrant_url: str, collection: str):
    async with httpx.AsyncClient(timeout=30) as client:
        response = await client.delete(
            f"{qdrant_url.rstrip('/')}/collections/{collection}")
        if response.status_code not in {200, 404}:
            response.raise_for_status()


async def run(args) -> dict:
    dataset = load_dataset(args.dataset)
    cases = select_cases(dataset["cases"], args.mode, args.real_sample_size)
    if args.mode == "real" and not settings.LLM_API_KEY:
        raise RuntimeError("real mode requires LLM_API_KEY")
    await wait_for_dependencies(args.qdrant_url, args.embedding_url)
    run_id = uuid.uuid4().hex[:12]
    collection = f"sky_eval_{run_id}"
    with tempfile.TemporaryDirectory(prefix="sky-rag-eval-", ignore_cleanup_errors=True) as directory:
        embedding = HttpEmbeddingProvider(args.embedding_url, "BAAI/bge-m3", 1024, 8)
        store = QdrantVectorStore(args.qdrant_url, collection, 1024)
        service = KnowledgeService(
            embedding, store, str(Path(directory) / "state.sqlite3"),
            str(Path(directory) / "sources"))
        try:
            await service.start()
            versions = await index_documents(service, dataset["documents"], run_id)
            agent = PythonAgent(service)
            results = []
            for case in cases:
                results.append(await evaluate_case(service, agent, versions, case, args.mode))
            report = summarize(results, args.mode)
            report.update({
                "dataset_version": dataset["version"], "collection": collection,
                "collection_cleaned": not args.keep_collection,
            })
            return report
        finally:
            await service.shutdown()
            if not args.keep_collection:
                await delete_collection(args.qdrant_url, collection)


def main():
    args = parse_args()
    report = asyncio.run(run(args))
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"passed": report["passed"], "metrics": report["metrics"],
                      "gates": report["gates"], "report": str(output)},
                     ensure_ascii=False, indent=2))
    if not report["passed"] and not args.no_gate:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
