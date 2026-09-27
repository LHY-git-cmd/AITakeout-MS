"""将旧Qdrant知识集合安全复制到管理端集合；不会删除或修改源集合。"""
import argparse
import os
from typing import Any

import httpx


def _request(client: httpx.Client, method: str, path: str,
             payload: dict[str, Any] | None = None) -> dict[str, Any]:
    """调用Qdrant REST API并统一校验响应。"""
    response = client.request(method, path, json=payload)
    response.raise_for_status()
    return response.json()


def main() -> None:
    """默认仅检查配置；传入--execute后才创建目标集合并复制向量。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", default="sky_knowledge_v2")
    parser.add_argument(
        "--target",
        default=os.getenv("ADMIN_QDRANT_COLLECTION", "sky_admin_internal_knowledge"),
    )
    parser.add_argument("--qdrant-url", default=os.getenv("QDRANT_URL", "http://localhost:6333"))
    parser.add_argument("--batch-size", type=int, default=128)
    parser.add_argument("--execute", action="store_true")
    args = parser.parse_args()
    if args.source == args.target:
        raise SystemExit("source and target collections must be different")

    with httpx.Client(base_url=args.qdrant_url.rstrip("/"), timeout=30) as client:
        source = _request(client, "GET", f"/collections/{args.source}")["result"]
        source_count = source.get("points_count", 0)
        if not args.execute:
            print(f"DRY RUN: copy {source_count} points: {args.source} -> {args.target}")
            print("Re-run with --execute to perform the non-destructive copy.")
            return

        # 目标不存在时复用源集合的向量结构；目标已存在时只做幂等upsert。
        target_response = client.get(f"/collections/{args.target}")
        if target_response.status_code == 404:
            params = source["config"]["params"]
            create = {"vectors": params["vectors"]}
            if params.get("sparse_vectors"):
                create["sparse_vectors"] = params["sparse_vectors"]
            _request(client, "PUT", f"/collections/{args.target}", create)
        else:
            target_response.raise_for_status()

        copied = 0
        offset: Any = None
        while True:
            body: dict[str, Any] = {
                "limit": args.batch_size,
                "with_payload": True,
                "with_vector": True,
            }
            if offset is not None:
                body["offset"] = offset
            page = _request(
                client, "POST", f"/collections/{args.source}/points/scroll", body
            )["result"]
            points = page.get("points", [])
            if points:
                _request(
                    client,
                    "PUT",
                    f"/collections/{args.target}/points?wait=true",
                    {"points": points},
                )
                copied += len(points)
            offset = page.get("next_page_offset")
            if offset is None:
                break
        print(f"Copied {copied} points. Source collection was preserved.")


if __name__ == "__main__":
    main()
