import httpx

from app.core.trace import current_session_id, current_task_id, current_trace_id


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


class MemoryVectorStore:
    def __init__(self):
        self.points = {}

    async def upsert(self, points):
        for point in points:
            self.points[point["id"]] = point

    async def delete_document(self, document_id, version=None):
        self.points = {
            key: value
            for key, value in self.points.items()
            if not (
                value["payload"]["document_id"] == document_id
                and (
                    version is None
                    or value["payload"]["document_version"] == version
                )
            )
        }

    async def search(self, vector, kb_id, versions, top_k):
        found = []
        for point in self.points.values():
            payload = point["payload"]
            if payload["kb_id"] != kb_id or not payload.get("enabled", True):
                continue
            if (
                versions
                and versions.get(payload["document_id"])
                != payload["document_version"]
            ):
                continue
            score = sum(left * right for left, right in zip(vector, point["vector"]))
            found.append({"score": score, **payload})
        return sorted(found, key=lambda item: item["score"], reverse=True)[:top_k]


class QdrantVectorStore:
    """
    Qdrant 向量存储实现。
    通过 HTTP API 与 Qdrant 服务进行交互，不依赖官方客户端库。
    """
    def __init__(self, base_url, collection, dimension):
        """
        初始化 QdrantVectorStore。

        :param base_url: Qdrant 服务的基础 URL (例如 "http://localhost:6333")。
        :param collection: 要使用的集合名称。
        :param dimension: 向量的维度。
        """
        self.base_url = base_url.rstrip("/")
        self.collection = collection
        self.dimension = dimension

    async def initialize(self):
        """
        初始化集合。如果集合不存在，则创建一个新的。
        """
        async with httpx.AsyncClient(timeout=10) as client:
            # 检查集合是否已存在
            response = await client.get(f"{self.base_url}/collections/{self.collection}")
            if response.status_code == 404:
                # 集合不存在，创建一个新的
                response = await client.put(
                    f"{self.base_url}/collections/{self.collection}",
                    json={"vectors": {"size": self.dimension, "distance": "Cosine"}}
                )
                response.raise_for_status()
                response = await client.get(
                    f"{self.base_url}/collections/{self.collection}")
            response.raise_for_status()
            config = response.json()["result"]["config"]["params"]["vectors"]
            actual_dimension = config.get("size") if isinstance(config, dict) else None
            if actual_dimension != self.dimension:
                raise ValueError(
                    f"Qdrant collection dimension mismatch: "
                    f"collection={self.collection}, expected={self.dimension}, actual={actual_dimension}"
                )

    async def upsert(self, points):
        """
        向集合中批量插入或更新点（向量）。

        :param points: 一个包含点对象的列表。每个点应包含 id, vector, 和 payload。
        """
        await self.initialize()  # 确保集合存在
        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.put(
                f"{self.base_url}/collections/{self.collection}/points?wait=true",
                json={"points": points}
            )
            response.raise_for_status()

    async def delete_document(self, document_id, version=None):
        """
        根据文档 ID 和版本删除相关的点。

        :param document_id: 要删除的文档 ID。
        :param version: (可选) 要删除的文档版本。
        """
        must = [{"key": "document_id", "match": {"value": document_id}}]
        if version is not None:
            must.append({"key": "document_version", "match": {"value": version}})
        
        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.post(
                f"{self.base_url}/collections/{self.collection}/points/delete?wait=true",
                json={"filter": {"must": must}}
            )
            response.raise_for_status()

    async def search(self, vector, kb_id, versions, top_k):
        """
        在集合中搜索与给定向量最相似的点。

        :param vector: 用于搜索的查询向量。
        :param kb_id: 知识库 ID，用于过滤。
        :param versions: 一个字典，包含文档 ID 和版本，用于进一步过滤。
        :param top_k: 要返回的最相似结果的数量。
        :return: 一个包含搜索结果的列表，每个结果包含分数和 payload。
        """
        # 构建过滤条件
        must = [
            {"key": "kb_id", "match": {"value": kb_id}},
            {"key": "enabled", "match": {"value": True}}
        ]
        if versions:
            # 如果指定了版本，则只在这些版本的文档中搜索
            must.append({
                "should": [
                    {"must": [
                        {"key": "document_id", "match": {"value": document_id}},
                        {"key": "document_version", "match": {"value": version}}
                    ]}
                    for document_id, version in versions.items()
                ]
            })

        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.post(
                f"{self.base_url}/collections/{self.collection}/points/search",
                headers=_trace_headers(),
                json={
                    "vector": vector,
                    "limit": top_k,
                    "with_payload": True,
                    "filter": {"must": must}
                }
            )
            response.raise_for_status()
            # 格式化并返回结果
            return [{"score": item["score"], **item["payload"]} for item in response.json()["result"]]
