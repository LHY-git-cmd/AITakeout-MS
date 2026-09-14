import asyncio
import os
import tempfile
import unittest
import uuid
from pathlib import Path

import httpx

from app.core.agent import PythonAgent
from app.core.task_queue import TaskQueue
from app.knowledge.embedding import HttpEmbeddingProvider
from app.knowledge.service import KnowledgeService
from app.knowledge.vector_store import QdrantVectorStore


class MockLlmAgent(PythonAgent):
    async def stream_process(self, query, context=None, model=None,
                             temperature=0.7, rag_context=None, refusal=None,
                             task_id=None, session_id=None, trace_id=None):
        if refusal:
            yield refusal
            return
        if not rag_context or "38.00" not in rag_context:
            raise AssertionError("RAG evidence was not injected into Mock LLM")
        yield "宫保鸡丁价格是38元。"


@unittest.skipUnless(
    os.getenv("RUN_RAG_E2E") == "1",
    "set RUN_RAG_E2E=1 to run against real Qdrant and embedding service",
)
class RagEndToEndTest(unittest.IsolatedAsyncioTestCase):
    # 测试从查询到真实Qdrant，再到模拟LLM，最后通过SSE返回结果的端到端流程
    # 这是一个完整的RAG（检索增强生成）流程的端到端测试。
    # 1.  **连接服务**：首先检查并连接到正在运行的Qdrant（向量数据库）和Embedding（文本嵌入）服务。
    # 2.  **创建知识**：创建一个临时的知识库，并将一份包含“宫保鸡丁价格：38.00”的菜单文档进行索引。
    #     这意味着文档内容会被转换成向量并存入Qdrant。
    # 3.  **等待索引**：等待索引任务完成。
    # 4.  **提交查询**：模拟用户提问“宫保鸡丁多少钱？”。
    # 5.  **RAG流程**：
    #     -   系统首先会在Qdrant中检索与问题最相关的文档片段（也就是那份菜单）。
    #     -   然后，将检索到的信息（“价格：38.00”）和原始问题一起发送给一个模拟的LLM。
    # 6.  **验证结果**：
    #     -   模拟的LLM会根据收到的上下文信息，生成答案“宫保鸡丁价格是38元。”。
    #     -   测试会验证最终收到的答案是否正确，并且答案是否引用了正确的原始文档。
    # 7.  **清理**：测试结束后，清理创建的临时知识库和文档。
    #
    # 这个测试确保了从知识索引、信息检索到最终答案生成的整个链条是通畅且正确的。
    async def test_query_to_real_qdrant_to_mock_llm_to_sse(self):
        qdrant_url = os.getenv("QDRANT_URL", "http://127.0.0.1:6333")
        embedding_url = os.getenv("EMBEDDING_BASE_URL", "http://127.0.0.1:8001")
        collection = os.getenv("QDRANT_COLLECTION", "sky_knowledge_v2")
        async with httpx.AsyncClient(timeout=10) as client:
            self.assertEqual(200, (await client.get(f"{qdrant_url}/healthz")).status_code)
            health = await client.get(f"{embedding_url}/health")
            self.assertEqual(200, health.status_code)
            self.assertEqual(1024, health.json()["dimension"])

        unique = uuid.uuid4().hex
        kb_id, document_id = f"e2e-kb-{unique}", f"e2e-doc-{unique}"
        embedding = HttpEmbeddingProvider(embedding_url, "BAAI/bge-m3", 1024, 4)
        vector_store = QdrantVectorStore(qdrant_url, collection, 1024)

        with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as directory:
            service = KnowledgeService(
                embedding,
                vector_store,
                str(Path(directory) / "knowledge.sqlite3"),
                str(Path(directory) / "sources"),
            )
            await service.start()
            try:
                request = {
                    "task_id": f"index-{unique}",
                    "request_hash": unique,
                    "kb_id": kb_id,
                    "document_id": document_id,
                    "document_version": 1,
                    "file_name": "menu.txt",
                    "file_type": "txt",
                    "embedding_model": "BAAI/bge-m3",
                }
                await service.submit(
                    request,
                    "菜品名称：宫保鸡丁\n价格：38.00\n分类：川菜\n描述：麻辣鲜香。".encode("utf-8"),
                )
                for _ in range(120):
                    status = service.status(request["task_id"])
                    if status["status"] in {"completed", "failed"}:
                        break
                    await asyncio.sleep(0.25)
                self.assertEqual("completed", status["status"], status.get("error_msg"))

                queue = TaskQueue(MockLlmAgent(service))
                task_id = f"query-{unique}"
                await queue.submit(
                    task_id=task_id,
                    user_id=1,
                    query="宫保鸡丁多少钱？",
                    knowledge={
                        "kb_id": kb_id,
                        "document_versions": {document_id: 1},
                        "top_k": 8,
                        "score_threshold": 0.35,
                    },
                )
                events = [event async for event in queue.subscribe(task_id)]
                self.assertEqual("task_end", events[-1]["event"])
                self.assertEqual("宫保鸡丁价格是38元。", events[-1]["data"]["result"])
                self.assertEqual(document_id, events[-1]["data"]["citations"][0]["document_id"])
            finally:
                try:
                    await vector_store.delete_document(document_id)
                finally:
                    await service.shutdown()


if __name__ == "__main__":
    unittest.main()