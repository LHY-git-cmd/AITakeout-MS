import hashlib
import math
import re

import httpx

from app.core.trace import current_session_id, current_task_id, current_trace_id


class HashEmbeddingProvider:
    """确定性的本地基线实现，生产环境可替换为 Embedding API。"""
    dimension = 384
    model = "hash-384"

    async def embed(self, texts: list[str]) -> list[list[float]]:
        return [self._one(text) for text in texts]

    def _one(self, text):
        vector = [0.0] * self.dimension
        terms = re.findall(r"[\w]+|[\u4e00-\u9fff]", text.lower())
        for term in terms:
            digest = hashlib.sha256(term.encode("utf-8")).digest()
            index = int.from_bytes(digest[:4], "big") % self.dimension
            vector[index] += -1.0 if digest[4] & 1 else 1.0
        norm = math.sqrt(sum(value * value for value in vector)) or 1.0
        return [value / norm for value in vector]


class HttpEmbeddingProvider:
    """通过独立的 OpenAI 兼容服务生成语义向量。"""

    def __init__(self, base_url, model, dimension=1024, batch_size=16):
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.dimension = dimension
        self.batch_size = batch_size

    async def embed(self, texts: list[str]) -> list[list[float]]:
        vectors = []
        async with httpx.AsyncClient(timeout=120) as client:
            for start in range(0, len(texts), self.batch_size):
                batch = texts[start:start + self.batch_size]
                response = await client.post(
                    f"{self.base_url}/v1/embeddings",
                    json={"model": self.model, "input": batch},
                    headers=self._trace_headers(),
                )
                response.raise_for_status()
                data = sorted(response.json()["data"], key=lambda item: item["index"])
                batch_vectors = [item["embedding"] for item in data]
                if any(len(vector) != self.dimension for vector in batch_vectors):
                    raise ValueError(
                        f"embedding dimension mismatch, expected {self.dimension}"
                    )
                vectors.extend(batch_vectors)
        return vectors

    @staticmethod
    def _trace_headers():
        headers = {}
        for header, value in (
            ("X-Trace-ID", current_trace_id.get()),
            ("X-Task-ID", current_task_id.get()),
            ("X-Session-ID", current_session_id.get()),
        ):
            if value:
                headers[header] = value
        return headers
