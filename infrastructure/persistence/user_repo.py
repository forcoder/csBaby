from typing import Optional
from infrastructure.persistence.database import get_connection


class SqliteUserRepository:
    """用户仓库（基于 MySQL 兼容层，非 SQLite）。

    通过 infrastructure.persistence.database 的 MySQL 兼容层操作数据库。
    """

    def get_by_phone(self, phone: str) -> Optional[dict]:
        db = get_connection()
        row = db.execute(
            "SELECT * FROM users WHERE phone = ?",
            (phone,),
        ).fetchone()
        db.close()
        return dict(row) if row else None

    def get_by_email(self, email: str) -> Optional[dict]:
        db = get_connection()
        row = db.execute(
            "SELECT * FROM users WHERE email = ?",
            (email,),
        ).fetchone()
        db.close()
        return dict(row) if row else None

    def get_by_identifier(self, identifier: str) -> Optional[dict]:
        db = get_connection()
        row = db.execute(
            "SELECT * FROM users WHERE phone = ? OR email = ?",
            (identifier, identifier),
        ).fetchone()
        db.close()
        return dict(row) if row else None