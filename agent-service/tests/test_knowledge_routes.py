from types import SimpleNamespace

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api.routes import router


class FakeKnowledgeService:
    """记录路由调用，用于验证管理与用户知识域不会串用。"""

    def __init__(self):
        self.calls = []

    async def submit(self, metadata, content):
        self.calls.append(("submit", metadata, content))
        return {"task_id": metadata["task_id"], "status": "pending"}

    def status(self, task_id):
        self.calls.append(("status", task_id))
        return {"task_id": task_id, "status": "completed"}

    async def delete_document(self, document_id, version):
        self.calls.append(("delete", document_id, version))

    async def update_release(self, release_id, documents, active):
        self.calls.append(("release", release_id, documents, active))


def client_and_services():
    app = FastAPI()
    app.include_router(router)
    admin, user = FakeKnowledgeService(), FakeKnowledgeService()
    app.state.knowledge_services = {
        "ADMIN_ASSISTANT": admin,
        "USER_ASSISTANT": user,
    }
    app.state.settings = SimpleNamespace(KNOWLEDGE_MAX_FILE_SIZE=1024)
    return TestClient(app), admin, user


def test_index_and_status_are_dispatched_to_user_profile():
    client, admin, user = client_and_services()
    metadata = """{
      "task_id":"task-1","kb_id":"kb-1","document_id":"doc-1",
      "document_version":1,"file_name":"policy.txt","file_type":"txt",
      "embedding_model":"hash-384","request_hash":"hash","category":"GENERAL"
    }"""
    response = client.post(
        "/api/v1/knowledge/index",
        data={"metadata": metadata, "agent_profile": "USER_ASSISTANT"},
        files={"file": ("policy.txt", b"public policy", "text/plain")},
    )
    assert response.status_code == 200
    assert not admin.calls
    assert user.calls[0][0] == "submit"

    response = client.get(
        "/api/v1/knowledge/index/task-1?agent_profile=USER_ASSISTANT")
    assert response.status_code == 200
    assert user.calls[-1] == ("status", "task-1")


def test_release_metadata_defaults_to_user_profile():
    client, admin, user = client_and_services()
    response = client.post(
        "/api/v1/knowledge/releases/release-1",
        json={
            "active": True,
            "documents": [{"document_id": "doc-1", "document_version": 2}],
        },
    )
    assert response.status_code == 200
    assert not admin.calls
    assert user.calls == [(
        "release", "release-1",
        [{"document_id": "doc-1", "document_version": 2}], True,
    )]
