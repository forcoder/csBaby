import json
import logging
from typing import Optional

from infrastructure.persistence.db_supabase import get_connection as get_supa_conn
from infrastructure.persistence.db_supabase import put_connection as put_supa_conn
from infrastructure.sync.sync_outbox_repo import SyncOutboxRepository

logger = logging.getLogger(__name__)


# 表名 -> Postgres 表名（一致即可）
_TABLE_NAME_MAP = {
    "users": "users",
    "user_devices": "user_devices",
    "keyword_rules": "keyword_rules",
    "model_configs": "model_configs",
    "blacklist": "blacklist",
    "tenant_style_config": "tenant_style_config",
    "tenant_app_config": "tenant_app_config",
}


def upsert_to_supabase(table: str, op: str, row_id: Optional[int], payload: Optional[dict]) -> None:
    """Push single row to Supabase via psycopg2."""
    supa_table = _TABLE_NAME_MAP.get(table)
    if not supa_table:
        raise ValueError(f"Unknown table for sync: {table}")

    conn = get_supa_conn()
    try:
        with conn.cursor() as cur:
            if op == "DELETE":
                if supa_table == "user_devices" and payload:
                    cur.execute(
                        "DELETE FROM user_devices WHERE user_id=%s AND device_id=%s",
                        (payload.get("user_id"), payload.get("device_id")),
                    )
                elif supa_table == "users" and payload:
                    cur.execute("DELETE FROM users WHERE id=%s", (payload.get("id"),))
                elif supa_table in ("tenant_style_config", "tenant_app_config") and payload:
                    cur.execute(
                        f"DELETE FROM {supa_table} WHERE user_id=%s",
                        (payload.get("user_id"),),
                    )
                else:
                    cur.execute(f"DELETE FROM {supa_table} WHERE id=%s", (row_id,))
            else:
                if not payload:
                    raise ValueError(f"INSERT/UPDATE requires payload, got empty for {table}")
                cols = list(payload.keys())
                values = [payload[c] for c in cols]
                placeholders = ",".join(["%s"] * len(cols))
                col_list = ",".join(cols)
                update_set = ",".join([f"{c}=EXCLUDED.{c}" for c in cols if c != "id"])
                sql = (
                    f"INSERT INTO {supa_table} ({col_list}) VALUES ({placeholders}) "
                    f"ON CONFLICT (id) DO UPDATE SET {update_set}"
                )
                cur.execute(sql, values)
        conn.commit()
    finally:
        put_supa_conn(conn)


class SyncWriter:
    """Dual-write orchestrator: push to Supabase; on failure, enqueue to outbox."""

    def __init__(self, db):
        self.db = db
        self.outbox_repo = SyncOutboxRepository(db)

    def push(self, table: str, op: str, row_id: Optional[int], payload: Optional[dict]) -> None:
        try:
            upsert_to_supabase(table, op, row_id, payload)
            logger.debug("sync.push success table=%s op=%s row_id=%s", table, op, row_id)
        except Exception as e:
            logger.warning("sync.push failed table=%s op=%s row_id=%s err=%s",
                           table, op, row_id, e)
            try:
                self.outbox_repo.enqueue(
                    table_name=table,
                    op=op,
                    row_id=row_id,
                    payload=payload or {},
                    last_error=str(e),
                )
            except Exception as ee:
                # outbox 写入也失败 → log 但不抛出（不阻塞 API）
                logger.error("sync.outbox.enqueue failed table=%s err=%s", table, ee)