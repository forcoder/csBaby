from typing import List, Tuple
from domain.entities.reply_history import ReplyHistory
from domain.repositories.history_repo import HistoryRepository
from infrastructure.persistence.database import get_connection
from infrastructure.sync.sync_writer import SyncWriter


class SqliteHistoryRepository(HistoryRepository):
    def create(self, entry: ReplyHistory) -> ReplyHistory:
        db = get_connection()
        cursor = db.execute(
            """INSERT INTO reply_history
            (user_id, original_message, reply_content, source, model_used, confidence,
             response_time_ms, platform, customer_name, house_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (entry.user_id, entry.original_message, entry.reply_content, entry.source,
             entry.model_used, entry.confidence, entry.response_time_ms,
             entry.platform, entry.customer_name, entry.house_name),
        )
        entry.id = cursor.lastrowid
        db.commit()
        payload = {"id": entry.id, "user_id": entry.user_id,
                   "original_message": entry.original_message,
                   "reply_content": entry.reply_content,
                   "source": entry.source, "model_used": entry.model_used,
                   "confidence": entry.confidence,
                   "response_time_ms": entry.response_time_ms,
                   "platform": entry.platform,
                   "customer_name": entry.customer_name,
                   "house_name": entry.house_name}
        SyncWriter(db).push("reply_history", "INSERT", entry.id, payload)
        db.close()
        return entry

    def get_by_device(self, user_id: str, limit: int, offset: int) -> Tuple[List[ReplyHistory], int]:
        db = get_connection()
        rows = db.execute(
            "SELECT * FROM reply_history WHERE user_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            (user_id, limit, offset),
        ).fetchall()
        total = db.execute(
            "SELECT COUNT(*) FROM reply_history WHERE user_id=?", (user_id,)
        ).fetchone()[0]
        db.close()
        items = [ReplyHistory(id=r["id"], user_id=r["user_id"],
                              original_message=r["original_message"], reply_content=r["reply_content"])
                 for r in rows]
        return items, total
