import json
import logging
import os
import time
from typing import Optional

from infrastructure.persistence.db_supabase import get_connection as get_supa_conn
from infrastructure.persistence.db_supabase import put_connection as put_supa_conn
from infrastructure.persistence.db_mysql import get_connection as get_mysql_conn
from infrastructure.persistence.db_mysql import put_connection as put_mysql_conn
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
    # keyword_rules: 补 keyword_hash = md5(keyword + reply_template) (唯一性约束)
    if table == "keyword_rules":
        import hashlib
        kw = out.get("keyword", "") or ""
        reply = out.get("reply_template", "") or ""
        out["keyword_hash"] = hashlib.md5((kw + reply).encode("utf-8")).hexdigest()
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
                # keyword_rules 用 (tenant_id, keyword_hash) 做 upsert, 避免同 keyword+reply 重复
                if supa_table == "keyword_rules":
                    conflict_cols = "(tenant_id, keyword_hash)"
                else:
                    conflict_cols = f"({pk})"
                update_set = ",".join([f"{c}=EXCLUDED.{c}" for c in cols if c != pk])
                sql = (
                    f"INSERT INTO {supa_table} ({col_list}) VALUES ({placeholders}) "
                    f"ON CONFLICT {conflict_cols} DO UPDATE SET {update_set}"
                )
                cur.execute(sql, values)
        conn.commit()
    finally:
        put_supa_conn(conn)


def upsert_to_mysql(table: str, op: str, row_id: Optional[int], payload: Optional[dict]) -> None:
    """Push single row to Aliyun RDS MySQL via pymysql.

    Phase 1 双写镜像 - 与 upsert_to_supabase 行为对齐,使用 MySQL 方言:
      - ON CONFLICT → ON DUPLICATE KEY UPDATE
      - BOOLEAN → TINYINT(1) (依赖 schema 已统一)
      - 其余字段名/类型与 supabase 一致

    RDS 缺配置时 RuntimeError("RDS_DB_URL not set"),由 SyncWriter.push 兜底到 outbox。
    """
    mysql_table = _TABLE_NAME_MAP.get(table)
    if not mysql_table:
        raise ValueError(f"Unknown table for sync: {table}")

    conn = get_mysql_conn()
    try:
        with conn.cursor() as cur:
            if op == "DELETE":
                pk = _TABLE_PRIMARY_KEY.get(mysql_table, "id")
                if pk == "id":
                    cur.execute(f"DELETE FROM {mysql_table} WHERE id=%s", (row_id,))
                else:
                    if not payload:
                        raise ValueError(f"DELETE on {mysql_table} requires payload (pk={pk})")
                    cur.execute(f"DELETE FROM {mysql_table} WHERE {pk}=%s", (payload.get(pk),))
            else:
                if not payload:
                    raise ValueError(f"INSERT/UPDATE requires payload, got empty for {table}")
                transformed = _transform_payload(table, payload)
                cols = list(transformed.keys())
                values = [transformed[c] for c in cols]
                placeholders = ",".join(["%s"] * len(cols))
                col_list = ",".join(cols)
                pk = _TABLE_PRIMARY_KEY.get(mysql_table, "id")
                # keyword_rules 用 (tenant_id, keyword_hash) 做 upsert
                # 假设 RDS 上已有 uk_tenant_keyword_hash 唯一索引 (Phase 2 验证)
                # 若仅 uk_tenant_keyword,MySQL 会按 (tenant_id, keyword_hash) 判定冲突
                if mysql_table == "keyword_rules":
                    conflict_cols = "(tenant_id, keyword_hash)"
                else:
                    conflict_cols = f"({pk})"
                update_set = ",".join([f"{c}=VALUES({c})" for c in cols if c != pk])
                sql = (
                    f"INSERT INTO {mysql_table} ({col_list}) VALUES ({placeholders}) "
                    f"ON DUPLICATE KEY UPDATE {update_set}"
                )
                cur.execute(sql, values)
        conn.commit()
    finally:
        put_mysql_conn(conn)


class SyncWriter:
    """Dual-write orchestrator: push to Supabase + RDS MySQL; on either failure, enqueue to outbox.

    Phase 1 行为:
      - push() 同时尝试 upsert_to_supabase 与 upsert_to_mysql
      - 任一成功 / 任一失败 都不阻塞 API 调用方
      - 任一失败 → outbox.enqueue(记录失败原因),由 retry_worker 后续补单
      - 双失败 → outbox.enqueue 记录 last_error(双侧错误合并)
    """

    def __init__(self, db):
        self.db = db
        self.outbox_repo = SyncOutboxRepository(db)

    def push(self, table: str, op: str, row_id: Optional[int], payload: Optional[dict]) -> None:
        errors: list[str] = []

        # 双写路径 1: Supabase (PostgreSQL)
        try:
            upsert_to_supabase(table, op, row_id, payload)
            logger.debug("sync.push supabase success table=%s op=%s row_id=%s", table, op, row_id)
        except Exception as e:
            err = f"supabase: {e}"
            logger.warning("sync.push supabase failed table=%s op=%s row_id=%s err=%s",
                           table, op, row_id, e)
            errors.append(err)

        # 双写路径 2: RDS MySQL (Phase 1 新增镜像)
        # 配置缺失 (RDS_DB_URL 未设) 视为「未启用 RDS 镜像」,静默跳过,
        # 不写 outbox (语义: API 仍按 Phase 0 行为只写 Supabase)。
        # 真连不上才视为失败,需要 outbox 兜底。
        if not os.environ.get("RDS_DB_URL"):
            logger.debug("sync.push mysql skipped (RDS_DB_URL not configured)")
        else:
            try:
                upsert_to_mysql(table, op, row_id, payload)
                logger.debug("sync.push mysql success table=%s op=%s row_id=%s", table, op, row_id)
            except Exception as e:
                err = f"mysql: {e}"
                logger.warning("sync.push mysql failed table=%s op=%s row_id=%s err=%s",
                               table, op, row_id, e)
                errors.append(err)

        # 任一失败 → outbox.enqueue 兜底
        if errors:
            last_error = " | ".join(errors)
            try:
                self.outbox_repo.enqueue(
                    table_name=table,
                    op=op,
                    row_id=row_id,
                    payload=payload or {},
                    last_error=last_error,
                )
            except Exception as ee:
                # outbox 写入也失败 → log 但不抛出(不阻塞 API)
                logger.error("sync.outbox.enqueue failed table=%s err=%s", table, ee)