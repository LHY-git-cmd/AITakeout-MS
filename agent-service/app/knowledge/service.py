import asyncio
import json
import re
import sqlite3
from datetime import datetime
from pathlib import Path

from app.knowledge.chunker import StructureChunker
from app.knowledge.parser import DocumentParser


class KnowledgeService:
    """
    知识库服务。
    负责管理知识文档的索引、搜索和状态。
    """

    def __init__(self, embedding, vector_store, state_path,
                 source_path="data/knowledge_sources"):
        """
        初始化知识库服务。

        :param embedding: 嵌入提供者实例。
        :param vector_store: 向量存储实例。
        :param state_path: 用于存储索引任务状态的 SQLite 数据库路径。
        """
        self.embedding = embedding
        self.vector_store = vector_store
        self.parser = DocumentParser()
        self.chunker = StructureChunker()
        self.source_path = Path(source_path).resolve()
        self.source_path.mkdir(parents=True, exist_ok=True)

        # 初始化状态数据库
        path = Path(state_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(path)
        self.db.execute(
            """
            create table if not exists knowledge_index_task(
                task_id text primary key,
                request_hash text not null,
                request_json text not null,
                status text not null,
                progress integer not null,
                chunk_count integer not null,
                error_msg text,
                updated_at text not null
            )
            """
        )
        self.db.commit()

        self.lock = asyncio.Lock()
        self.workers = {}
        self.stopping = False

    async def start(self):
        """
        启动服务，恢复未完成的索引任务。
        """
        initialize = getattr(self.vector_store, "initialize", None)
        if initialize is not None:
            await initialize()
        rows = self.db.execute(
            """
            select task_id, request_json
            from knowledge_index_task
            where status not in ('completed', 'failed')
            """
        ).fetchall()
        for task_id, request_json in rows:
            self._launch(task_id, json.loads(request_json))

    async def shutdown(self):
        """
        平滑关闭服务，取消所有正在运行的任务。
        """
        self.stopping = True
        for worker in list(self.workers.values()):
            worker.cancel()
        if self.workers:
            await asyncio.gather(*self.workers.values(), return_exceptions=True)
        self.db.close()

    async def submit(self, request: dict, file_bytes: bytes | None = None):
        """
        提交一个新的文档索引任务。

        :param request: 包含任务信息的字典。
        :return: 任务状态字典。
        """
        task_id, request_hash = request["task_id"], request["request_hash"]
        async with self.lock:
            row = self.db.execute(
                "select request_hash from knowledge_index_task where task_id = ?",
                (task_id,),
            ).fetchone()
            if row:
                if row[0] != request_hash:
                    raise ValueError(
                        "task_id reused with different index request"
                    )
                return self.status(task_id)
            if file_bytes is not None:
                suffix = "." + request["file_type"].lower().lstrip(".")
                document_dir = self.source_path / request["document_id"]
                document_dir.mkdir(parents=True, exist_ok=True)
                source_file = (document_dir / f"{request['document_version']}{suffix}").resolve()
                if self.source_path not in source_file.parents:
                    raise ValueError("invalid knowledge source path")
                await asyncio.to_thread(source_file.write_bytes, file_bytes)
                request = {**request, "source_path": str(source_file)}
            elif not request.get("source_path") and not request.get("file_path"):
                raise ValueError("document file is required")
            self.db.execute(
                "insert into knowledge_index_task values(?, ?, ?, ?, ?, ?, ?, ?)",
                (
                    task_id,
                    request_hash,
                    json.dumps(request, ensure_ascii=False),
                    "pending",
                    0,
                    0,
                    None,
                    datetime.now().isoformat(),
                ),
            )
            self.db.commit()
            self._launch(task_id, request)
            return self.status(task_id)

    def status(self, task_id):
        """
        获取指定索引任务的状态。

        :param task_id: 任务 ID。
        :return: 包含任务状态的字典，如果任务不存在则返回 None。
        """
        row = self.db.execute(
            """
            select status, progress, chunk_count, error_msg, updated_at
            from knowledge_index_task
            where task_id = ?
            """,
            (task_id,),
        ).fetchone()
        if not row:
            return None
        return {
            "task_id": task_id,
            "status": row[0],
            "progress": row[1],
            "chunk_count": row[2],
            "error_msg": row[3],
            "updated_at": row[4],
        }

    async def delete_document(self, document_id, version=None):
        """
        从向量存储中删除一个文档（或特定版本）。

        :param document_id: 要删除的文档 ID。
        :param version: 可选，要删除的文档的特定版本。
        """
        await self.vector_store.delete_document(document_id, version)

    async def search(self, kb_id, query, versions=None, top_k=8, threshold=0.2):
        """
        在知识库中搜索相关内容。

        :param kb_id: 知识库 ID。
        :param query: 用户查询字符串。
        :param versions: 用于过滤的文档版本字典。
        :param top_k: 返回结果的最大数量。
        :param threshold: 分数阈值，低于此值的结果将被过滤。
        :return: 一个去重和过滤后的结果列表。
        """
        # Java 负责确定有效文档版本；版本范围为空时，禁止退化为历史数据全量检索。
        if not versions:
            return []
        vector = (await self.embedding.embed([query]))[0]
        # 向量库先扩大候选集，再用问题与正文的词面重合度轻量重排。
        # 这能避免“投诉/退款”“账号/密码”等主题相近文档抢占 Top K，
        # 同时保留 BGE 作为主要召回信号。
        candidate_k = min(12, max(top_k * 4, 8))
        results = await self.vector_store.search(
            vector,
            kb_id,
            versions,
            candidate_k,
        )
        for item in results:
            item["rerank_score"] = (
                item["score"] * 0.75
                + self._lexical_score(query, item.get("content", "")) * 0.25
            )
        results.sort(key=lambda item: item["rerank_score"], reverse=True)
        deduped, seen = [], set()
        for item in results:
            if item["score"] < threshold or item["content_hash"] in seen:
                continue
            seen.add(item["content_hash"])
            deduped.append(item)
            if len(deduped) >= min(top_k, 8):
                break
        return deduped

    @staticmethod
    def _lexical_score(query: str, content: str) -> float:
        """返回问题词元在候选正文中的覆盖率，用于向量召回后的重排。"""
        def terms(text):
            normalized = re.sub(r"\s+", "", text.lower())
            chinese = re.findall(r"[\u4e00-\u9fff]+", normalized)
            bigrams = {
                word[index:index + 2]
                for word in chinese
                for index in range(max(0, len(word) - 1))
            }
            words = set(re.findall(r"[a-z0-9]+", normalized))
            return bigrams | words

        query_terms = terms(query)
        if not query_terms:
            return 0.0
        return len(query_terms & terms(content)) / len(query_terms)

    def _launch(self, task_id, request):
        """
        启动一个后台任务来执行文档索引。
        """
        if task_id not in self.workers or self.workers[task_id].done():
            self.workers[task_id] = asyncio.create_task(self._run(task_id, request))

    async def _run(self, task_id, request):
        """
        文档索引的实际执行逻辑。
        包括：解析、分块、向量化、存储。
        """
        try:
            requested_model = request.get("embedding_model")
            actual_model = getattr(self.embedding, "model", None)
            if requested_model and actual_model and requested_model != actual_model:
                raise ValueError(
                    f"embedding model mismatch: requested={requested_model}, actual={actual_model}"
                )
            self._update(task_id, "parsing", 10)
            sections = await asyncio.to_thread(
                self.parser.parse,
                request.get("source_path") or request["file_path"],
                request["file_type"],
            )
            metadata = {
                key: request[key]
                for key in (
                    "kb_id",
                    "document_id",
                    "document_version",
                    "file_name",
                )
            }
            chunks = self.chunker.split(sections, metadata)
            if not chunks:
                raise ValueError("document contains no indexable text")
            self._update(task_id, "embedding", 45)
            vectors = await self.embedding.embed([chunk["content"] for chunk in chunks])
            points = [
                {
                    "id": chunk["chunk_id"],
                    "vector": vector,
                    "payload": chunk,
                }
                for chunk, vector in zip(chunks, vectors)
            ]
            await self.vector_store.delete_document(
                request["document_id"],
                request["document_version"],
            )
            await self.vector_store.upsert(points)
            self._update(task_id, "completed", 100, len(chunks))
        except asyncio.CancelledError:
            if not self.stopping:
                self._update(
                    task_id,
                    "failed",
                    0,
                    error="index task cancelled",
                )
        except Exception as exc:
            self._update(task_id, "failed", 0, error=str(exc)[:1000])
        finally:
            self.workers.pop(task_id, None)

    def _update(self, task_id, status, progress, chunks=0, error=None):
        """
        更新任务在数据库中的状态。
        """
        self.db.execute(
            """
            update knowledge_index_task
            set status = ?, progress = ?, chunk_count = ?, error_msg = ?, updated_at = ?
            where task_id = ?
            """,
            (
                status,
                progress,
                chunks,
                error,
                datetime.now().isoformat(),
                task_id,
            ),
        )
        self.db.commit()
