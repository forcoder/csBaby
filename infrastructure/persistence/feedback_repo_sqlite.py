from typing import List
from domain.entities.feedback import Feedback
from domain.repositories.feedback_repo import FeedbackRepository
from infrastructure.persistence.database import get_connection


class SqliteFeedbackRepository(FeedbackRepository):
    def create(self, fb: Feedback) -> Feedback:
        db = get_connection()
        cursor = db.execute(
            """INSERT INTO feedback (user_id, reply_history_id, action, modified_text, rating, comment)
            VALUES (?, ?, ?, ?, ?, ?)""",
            (fb.user_id, fb.reply_history_id, fb.action,
             fb.modified_text, fb.rating, fb.comment),
        )
        fb.id = cursor.lastrowid
        db.commit()
        db.close()
        return fb

    def get_by_device(self, user_id: str, limit: int, offset: int) -> List[Feedback]:
        db = get_connection()
        rows = db.execute(
            "SELECT * FROM feedback WHERE user_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?",
            (user_id, limit, offset),
        ).fetchall()
        db.close()
        return [Feedback(id=r["id"], user_id=r["user_id"], action=r["action"])
                for r in rows]
