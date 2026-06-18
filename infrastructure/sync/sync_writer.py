import json
import logging
import time
from typing import Optional

from infrastructure.persistence.db_supabase import get_connection as get_supa_conn
from infrastructure.persistence.db_supabase import put_connection as put_supa_conn
from infrastructure.sync.sync_outbox_repo import SyncOutboxRepository

logger = logging.getLogger(__name__)


# 表名 -> Postgres 表名
# 修复 BUG-R11: 与 Supabase 实际 schema 对齐
#   - model_configs (主 API) → ai_model_configs (Supabase)
#   - blacklist (主 API)    → message_blacklist (Supabase)
#   - feedback / optimization_metrics 表已在 Supabase 补全 (deploy/supabase_missing_tables.sql)
#   - user_devices / devices / tenant_*: 仍不推送 (device token 敏感 / schema 未对齐)
_TABLE_NAME_MAP = {
    "users": "users",
    "keyword_rules": "keyword_rules",
    "model_configs": "ai_model_configs",
    "blacklist": "message_blacklist",
    "feedback": "feedback",
    "reply_history": "reply_history",
    "optimization_metrics": "optimization_metrics",
    "user_style_profiles": "user_style_profiles",
    "app_configs": "app_configs",
    "scenarios": "scenarios",
}


# 表 → 主键列 (Supabase 实际主键)
#   - app_configs: package_name (无 id 列)
#   - users / user_style_profiles: id (但 user_style_profiles 也用 user_id upsert)
_TABLE_PRIMARY_KEY = {
    "users": "id",
    "keyword_rules": "id",
    "model_configs": "id",
    "blacklist": "id",
    "reply_history": "id",
    "user_style_profiles": "id",
    "app_configs": "package_name",
    "scenarios": "id",
    "feedback": "id",
    "optimization_metrics": "id",
}


# 列名重命名: 主 API payload 字段 → Supabase 列名
_FIELD_RENAME = {
    "keyword_rules": {"target_names": "target_names_json"},
    "user_style_profiles": {},
    "reply_history": {},
    "model_configs": {},
    "blacklist": {},
    "feedback": {},
    "optimization_metrics": {},
    "app_configs": {},
    "scenarios": {},
    "users": {},
}


# 主 API 0/1 整数字段 → Supabase boolean 字段
_BOOLEAN_FIELDS = {
    "keyword_rules": ["enabled"],
    "reply_history": ["style_applied", "modified"],
    "model_configs": ["is_default", "is_enabled"],
    "blacklist": ["is_enabled"],
    "feedback": [],
    "optimization_metrics": [],
    "user_style_profiles": [],
    "app_configs": ["is_monitored"],
    "scenarios": [],
    "users": ["deleted"],
}


def _transform_payload(table: str, payload: dict) -> dict:
    """适配主 API SQLite payload → Supabase tenant 隔离 schema。

    转换:
      1. user_id → tenant_id (单租户语义, 每个用户即自己的租户)
      2. 列名重命名 (e.g. target_names → target_names_json)
      3. 0/1 整数 → boolean (Supabase 严格类型)
      4. 补 sync_version (毫秒时间戳, 增量同步用)
    """
    out = dict(payload)
    # user_id → tenant_id (如果有 user_id 而没 tenant_id)
    if "user_id" in out and "tenant_id" not in out:
        out["tenant_id"] = out.pop("user_id")
    elif "user_id" in out and "tenant_id" in out:
        # 同时存在 → 删 user_id 避免 Supabase 报 column does not exist
        out.pop("user_id")
    # 列名重命名
    for old_name, new_name in _FIELD_RENAME.get(table, {}).items():
        if old_name in out:
            out[new_name] = out.pop(old_name)
    # 0/1 整数 → boolean
    for field in _BOOLEAN_FIELDS.get(table, []):
        if field in out and isinstance(out[field], int):
            out[field] = bool(out[field])
    # 补 sync_version
    if "sync_version" not in out:
        out["sync_version"] = int(time.time() * 1000)
    return out


def upsert_to_supabase(table: str, op: str, row_id: Optional[int], payload: Optional[dict]) -> None:
    """Push single row to Supabase via psycopg2.

    BUG-R11 修复: 转换 payload (user_id → tenant_id + 补 sync_version) 后再写入。
    """
    supa_table = _TABLE_NAME_MAP.get(table)
    if not supa_table:
        raise ValueError(f"Unknown table for sync: {table}")

    conn = get_supa_conn()
    try:
        with conn.cursor() as cur:
            if op == "DELETE":
                pk = _TABLE_PRIMARY_KEY.get(supa_table, "id")
                if pk == "id":
                    cur.execute(f"DELETE FROM {supa_table} WHERE id=%s", (row_id,))
                else:
                    # app_configs 等用其他主键, 需要 payload
                    if not payload:
                        raise ValueError(f"DELETE on {supa_table} requires payload (pk={pk})")
                    cur.execute(f"DELETE FROM {supa_table} WHERE {pk}=%s", (payload.get(pk),))
            else:
                if not payload:
                    raise ValueError(f"INSERT/UPDATE requires payload, got empty for {table}")
                transformed = _transform_payload(table, payload)
                cols = list(transformed.keys())
                values = [transformed[c] for c in cols]
                placeholders = ",".join(["%s"] * len(cols))
                col_list = ",".join(cols)
                pk = _TABLE_PRIMARY_KEY.get(supa_table, "id")
                update_set = ",".join([f"{c}=EXCLUDED.{c}" for c in cols if c != pk])
                sql = (
                    f"INSERT INTO {supa_table} ({col_list}) VALUES ({placeholders}) "
                    f"ON CONFLICT ({pk}) DO UPDATE SET {update_set}"
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