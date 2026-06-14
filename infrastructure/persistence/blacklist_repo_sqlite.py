from infrastructure.persistence.database import get_connection
from infrastructure.sync.sync_writer import SyncWriter


class SqliteBlacklistRepository:
    def create(self, user_id: str, type_: str, value: str,
               description: str = "", package_name: str = None,
               is_enabled: bool = True) -> int:
        db = get_connection()
        cur = db.execute(
            """INSERT INTO blacklist (user_id, type, value, description, package_name, is_enabled)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (user_id, type_, value, description, package_name, 1 if is_enabled else 0),
        )
        db.commit()
        row_id = cur.lastrowid
        SyncWriter(db).push("blacklist", "INSERT", row_id, {
            "id": row_id, "user_id": user_id, "type": type_, "value": value,
            "description": description, "package_name": package_name,
            "is_enabled": is_enabled,
        })
        db.close()
        return row_id

    def delete(self, row_id: int, user_id: str) -> None:
        db = get_connection()
        db.execute("DELETE FROM blacklist WHERE id=? AND user_id=?", (row_id, user_id))
        db.commit()
        SyncWriter(db).push("blacklist", "DELETE", row_id, None)
        db.close()