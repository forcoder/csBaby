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
        # Sync payload MUST NOT include password_hash/salt — sensitive credentials
        # stay in the local SQLite only. Supabase holds identity fields for analytics.
        sync_payload = {"id": user_id, "phone": phone, "name": name}
        SyncWriter(db).push("users", "INSERT", None, sync_payload)
        db.close()
        return {"id": user_id, "phone": phone, "name": name}

    def update(self, user_id: str, name: str) -> None:
        db = get_connection()
        db.execute("UPDATE users SET name=? WHERE id=?", (name, user_id))
        db.commit()
        row = db.execute("SELECT id, phone, name FROM users WHERE id=?", (user_id,)).fetchone()
        if row:
            sync_payload = {"id": row["id"], "phone": row["phone"], "name": row["name"]}
            SyncWriter(db).push("users", "UPDATE", None, sync_payload)
        db.close()

    def get_by_id(self, user_id: str) -> Optional[dict]:
        db = get_connection()
        row = db.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
        db.close()
        return dict(row) if row else None