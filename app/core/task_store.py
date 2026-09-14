"""
基于 SQLite 的轻量级任务快照存储模块。

该模块负责将任务的完整状态（元数据和事件历史）持久化到 SQLite 数据库，
以便在服务重启后能够恢复任务。它还包含了对 `datetime` 对象的自定义序列化逻辑。
"""
import json
import sqlite3
from datetime import datetime
from pathlib import Path


class TaskStore:
    """
    一个使用 SQLite 进行任务持久化的存储类。

    它提供了保存和加载任务快照的功能，并能正确处理 `datetime` 对象的
    JSON 序列化和反序列化。
    """
    def __init__(self, path: str):
        """
        初始化 TaskStore。

        连接到指定的 SQLite 数据库文件，并确保表结构存在。
        同时，启用 WAL (Write-Ahead Logging) 模式以提高并发性能。

        :param path: SQLite 数据库文件的路径。
        """
        db_path = Path(path)
        db_path.parent.mkdir(parents=True, exist_ok=True)
        self._connection = sqlite3.connect(db_path)
        self._connection.execute("PRAGMA journal_mode=WAL")
        self._connection.execute(
            """CREATE TABLE IF NOT EXISTS agent_task_state (
                   task_id TEXT PRIMARY KEY,
                   request_hash TEXT NOT NULL,
                   task_json TEXT NOT NULL,
                   events_json TEXT NOT NULL,
                   updated_at TEXT NOT NULL
               )"""
        )
        self._connection.commit()

    def load_all(self) -> list[tuple[dict, list[dict]]]:
        """
        从数据库加载所有任务及其事件历史。

        在服务启动时由 `TaskQueue` 调用，用于恢复所有任务的内存状态。

        :return: 一个元组列表，每个元组包含 (任务字典, 事件列表)。
        """
        rows = self._connection.execute(
            "SELECT task_json, events_json FROM agent_task_state"
        ).fetchall()
        return [(self._decode_task(task), json.loads(events)) for task, events in rows]

    def save(self, task: dict, events: list[dict]) -> None:
        """
        保存或更新一个任务的快照。

        使用 `INSERT ... ON CONFLICT DO UPDATE` (upsert) 语句，
        确保操作的幂等性。如果任务已存在，则更新；否则，插入新记录。

        :param task: 要保存的任务字典。
        :param events: 该任务的完整事件列表。
        """
        task_json = json.dumps(task, ensure_ascii=False, default=self._json_default)
        events_json = json.dumps(events, ensure_ascii=False, default=self._json_default)
        self._connection.execute(
            """INSERT INTO agent_task_state
                   (task_id, request_hash, task_json, events_json, updated_at)
               VALUES (?, ?, ?, ?, ?)
               ON CONFLICT(task_id) DO UPDATE SET
                   request_hash=excluded.request_hash,
                   task_json=excluded.task_json,
                   events_json=excluded.events_json,
                   updated_at=excluded.updated_at""",
            (task["task_id"], task["_request_hash"], task_json, events_json,
             datetime.now().isoformat())
        )
        self._connection.commit()

    def close(self) -> None:
        """
        关闭与 SQLite 数据库的连接。
        """
        self._connection.close()

    @staticmethod
    def _json_default(value):
        """
        自定义 JSON 序列化函数，用于处理不支持的类型。

        当 `json.dumps` 遇到 `datetime` 对象时，会调用此函数，
        将其转换为一个可被 JSON 识别的特定格式的字典。

        :param value: 要序列化的对象。
        :return: 序列化后的对象。
        """
        if isinstance(value, datetime):
            # 将 datetime 对象转换为 {"__datetime__": "iso_format_string"}
            return {"__datetime__": value.isoformat()}
        raise TypeError(f"Unsupported JSON value: {type(value)!r}")

    @staticmethod
    def _decode_task(raw: str) -> dict:
        """
        自定义 JSON 反序列化钩子，用于解码任务对象。

        当 `json.loads` 解析时，`object_hook` 会检查每个字典，
        如果发现 `{"__datetime__": ...}` 格式，就将其转换回 `datetime` 对象。

        :param raw: 从数据库读取的原始 JSON 字符串。
        :return: 解码后的任务字典，其中包含了正确的 `datetime` 对象。
        """
        def hook(value):
            if set(value) == {"__datetime__"}:
                return datetime.fromisoformat(value["__datetime__"])
            return value
        return json.loads(raw, object_hook=hook)