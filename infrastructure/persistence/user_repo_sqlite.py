from typing import Optional

from infrastructure.persistence.database import get_connection
from infrastructure.sync.sync_writer import SyncWriter


class SqliteUserRepository:
    def create(self, user_id: str, phone: str, password_hash: str,
               salt: str, name: str = "") -> dict:
        db = get_connection()
        db.execute(
            """INSERT INTO users (id, phone, password_hash, salt, name)
               VALUES (?, ?, ?, ?, ?)""",
            (user_id, phone, password_hash, salt, name),
        )
        db.commit()
        payload = {"id": user_id, "phone": phone, "password_hash": password_hash,
                   "salt": salt, "name": name}
        SyncWriter(db).push("users", "INSERT", None, payload)
        db.close()
        return payload

    def update(self, user_id: str, name: str) -> None:
        db = get_connection()
        db.execute("UPDATE users SET name=? WHERE id=?", (name, user_id))
        db.commit()
        row = db.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
        if row:
            payload = {"id": row["id"], "phone": row["phone"],
                       "password_hash": row["password_hash"], "salt": row["salt"],
                       "name": row["name"]}
            SyncWriter(db).push("users", "UPDATE", None, payload)
        db.close()

    def get_by_id(self, user_id: str) -> Optional[dict]:
        db = get_connection()
        row = db.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
        db.close()
        return dict(row) if row else None