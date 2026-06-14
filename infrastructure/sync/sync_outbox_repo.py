import json
from typing import List, Optional

# 重试退避策略（秒）
_BACKOFF_SECONDS = [10, 30, 120, 600, 3600]  # 10s, 30s, 2min, 10min, 1h
_MAX_ATTEMPTS = 10


class SyncOutboxRepository:
    def __init__(self, db):
        self.db = db

    def enqueue(self, table_name: str, op: str, row_id: Optional[int], payload: dict,
                last_error: Optional[str] = None) -> int:
        cursor = self.db.execute(
            """INSERT INTO sync_outbox (table_name, op, row_id, payload, last_error)
               VALUES (?, ?, ?, ?, ?)""",
            (table_name, op, row_id, json.dumps(payload, ensure_ascii=False), last_error),
        )
        self.db.commit()
        return cursor.lastrowid

    def claim_due(self, limit: int = 50) -> List[dict]:
        rows = self.db.execute(
            """SELECT * FROM sync_outbox
               WHERE next_retry_at <= CURRENT_TIMESTAMP
               ORDER BY id ASC LIMIT ?""",
            (limit,),
        ).fetchall()
        return [dict(r) for r in rows]

    def mark_done(self, row_id: int) -> None:
        self.db.execute("DELETE FROM sync_outbox WHERE id=?", (row_id,))
        self.db.commit()

    def mark_failed(self, row_id: int, error: str, base_time: Optional[str] = None) -> None:
        row = self.db.execute("SELECT attempts FROM sync_outbox WHERE id=?", (row_id,)).fetchone()
        if not row:
            return
        new_attempts = row["attempts"] + 1
        if new_attempts >= _MAX_ATTEMPTS:
            self._move_to_dead_letter(row_id, new_attempts, error)
            return
        delay = _BACKOFF_SECONDS[min(new_attempts - 1, len(_BACKOFF_SECONDS) - 1)]
        self.db.execute(
            """UPDATE sync_outbox
               SET attempts=?, last_error=?,
                   next_retry_at = datetime(?, '+' || ? || ' seconds'),
                   updated_at=CURRENT_TIMESTAMP
               WHERE id=?""",
            (new_attempts, error, base_time or "now", delay, row_id),
        )
        self.db.commit()

    def _move_to_dead_letter(self, row_id: int, attempts: int, error: str) -> None:
        row = self.db.execute("SELECT * FROM sync_outbox WHERE id=?", (row_id,)).fetchone()
        if not row:
            return
        self.db.execute(
            """INSERT INTO sync_outbox_dead
               (table_name, op, row_id, payload, attempts, last_error)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (row["table_name"], row["op"], row["row_id"], row["payload"], attempts, error),
        )
        self.db.execute("DELETE FROM sync_outbox WHERE id=?", (row_id,))
        self.db.commit()
