"""同步写入器 — 记录数据变更到 sync_changes 表。

使用 MySQL 兼容层 (infrastructure.persistence.database) 写入变更记录，
供 sync 服务轮询拉取增量变更。
"""


class SyncWriter:
    """同步写入器，记录数据变更事件。"""

    def __init__(self, db):
        self._db = db

    def push(self, table: str, operation: str, record_id, data: dict) -> None:
        """写入一条变更记录到 sync_changes 表。

        Args:
            table: 变更的表名
            operation: 操作类型 (INSERT/UPDATE/DELETE)
            record_id: 记录 ID (可为 None)
            data: 变更数据 payload
        """
        import json
        self._db.execute(
            "INSERT INTO sync_changes (table_name, operation, record_id, payload, created_at) "
            "VALUES (?, ?, ?, ?, ?)",
            (table, operation, str(record_id) if record_id else None,
             json.dumps(data, ensure_ascii=False),
             __import__('time').time()),
        )