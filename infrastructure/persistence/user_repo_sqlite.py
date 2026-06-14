from typing import Optional

from infrastructure.persistence.database import get_connection
from infrastructure.sync.sync_writer import SyncWriter


class SqliteUserRepository:
    def create(self, user_id: str, phone: str, password_hash: str,
               salt: str, name: str = "", email: Optional[str] = None) -> dict:
        db = get_connection()
        db.execute(
            """INSERT INTO users (id, phone, password_hash, salt, email, name)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (user_id, phone, password_hash, salt, email, name),
        )
        db.commit()
        # Sync payload MUST NOT include password_hash/salt — sensitive credentials
        # stay in the local SQLite only. Supabase holds identity fields for analytics.
        sync_payload = {"id": user_id, "phone": phone, "email": email, "name": name}
        SyncWriter(db).push("users", "INSERT", None, sync_payload)
        db.close()
        return {"id": user_id, "phone": phone, "email": email, "name": name}

    def update(self, user_id: str, name: str) -> None:
        db = get_connection()
        db.execute("UPDATE users SET name=? WHERE id=?", (name, user_id))
        db.commit()
        row = db.execute("SELECT id, phone, email, name FROM users WHERE id=?", (user_id,)).fetchone()
        if row:
            sync_payload = {"id": row["id"], "phone": row["phone"],
                           "email": row["email"], "name": row["name"]}
            SyncWriter(db).push("users", "UPDATE", None, sync_payload)
        db.close()

    def get_by_id(self, user_id: str) -> Optional[dict]:
        db = get_connection()
        row = db.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
        db.close()
        return dict(row) if row else None

    def get_by_phone(self, phone: str) -> Optional[dict]:
        """Lookup user by phone (legacy field, required for old clients)."""
        db = get_connection()
        row = db.execute("SELECT * FROM users WHERE phone=?", (phone,)).fetchone()
        db.close()
        return dict(row) if row else None

    def get_by_email(self, email: str) -> Optional[dict]:
        """Lookup user by email (for /api/auth/user/login email path)."""
        if not email:
            return None
        db = get_connection()
        row = db.execute("SELECT * FROM users WHERE email=? COLLATE NOCASE",
                         (email.strip().lower(),)).fetchone()
        db.close()
        return dict(row) if row else None

    def get_by_identifier(self, identifier: str) -> Optional[dict]:
        """Lookup by phone OR email — auto-detect based on format.

        Used by /api/auth/user/login to accept either field. A string
        containing '@' is treated as email; everything else as phone.
        """
        if not identifier:
            return None
        if "@" in identifier:
            return self.get_by_email(identifier)
        return self.get_by_phone(identifier)