#!/usr/bin/env python3
"""Create 7 business tables on Supabase Postgres. Idempotent — safe to re-run.

Usage:
    python scripts/bootstrap_supabase.py            # create tables
    python scripts/bootstrap_supabase.py --check    # verify tables exist
"""
import argparse
import sys
from pathlib import Path

# 允许从项目根目录运行
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from infrastructure.persistence.db_supabase import get_connection, health_check

# 7 张表的 DDL（Postgres 风格）
_DDL_STATEMENTS = [
    """CREATE TABLE IF NOT EXISTS users (
        id TEXT PRIMARY KEY,
        phone TEXT UNIQUE NOT NULL,
        password_hash TEXT NOT NULL,
        salt TEXT NOT NULL,
        name TEXT DEFAULT '',
        created_at TIMESTAMPTZ DEFAULT now()
    )""",
    """CREATE TABLE IF NOT EXISTS user_devices (
        user_id TEXT NOT NULL,
        device_id TEXT NOT NULL,
        platform TEXT DEFAULT 'android',
        device_name TEXT DEFAULT '',
        registered_at TIMESTAMPTZ DEFAULT now(),
        PRIMARY KEY (user_id, device_id)
    )""",
    """CREATE TABLE IF NOT EXISTS keyword_rules (
        id BIGINT PRIMARY KEY,
        user_id TEXT NOT NULL,
        keyword TEXT NOT NULL,
        match_type TEXT DEFAULT 'CONTAINS',
        reply_template TEXT NOT NULL,
        category TEXT DEFAULT '',
        target_type TEXT DEFAULT 'ALL',
        target_names TEXT DEFAULT '[]',
        priority INTEGER DEFAULT 0,
        enabled BOOLEAN DEFAULT true,
        created_at TIMESTAMPTZ DEFAULT now(),
        updated_at TIMESTAMPTZ DEFAULT now()
    )""",
    """CREATE TABLE IF NOT EXISTS model_configs (
        id BIGINT PRIMARY KEY,
        user_id TEXT NOT NULL,
        name TEXT NOT NULL,
        model_type TEXT NOT NULL,
        model TEXT NOT NULL,
        api_key TEXT NOT NULL,
        api_endpoint TEXT,
        temperature REAL DEFAULT 0.7,
        max_tokens INTEGER DEFAULT 2000,
        is_default BOOLEAN DEFAULT false,
        enabled BOOLEAN DEFAULT true,
        created_at TIMESTAMPTZ DEFAULT now(),
        updated_at TIMESTAMPTZ DEFAULT now()
    )""",
    """CREATE TABLE IF NOT EXISTS blacklist (
        id BIGINT PRIMARY KEY,
        user_id TEXT NOT NULL,
        type TEXT DEFAULT 'KEYWORD',
        value TEXT NOT NULL,
        description TEXT DEFAULT '',
        package_name TEXT,
        is_enabled BOOLEAN DEFAULT true,
        created_at TIMESTAMPTZ DEFAULT now()
    )""",
    """CREATE TABLE IF NOT EXISTS tenant_style_config (
        user_id TEXT PRIMARY KEY,
        theme TEXT DEFAULT 'light',
        primary_color TEXT DEFAULT '#1976D2',
        accent_color TEXT DEFAULT '#FF4081',
        font_size TEXT DEFAULT 'medium',
        bubble_style TEXT DEFAULT 'rounded',
        avatar_enabled BOOLEAN DEFAULT true,
        show_timestamp BOOLEAN DEFAULT true,
        send_sound BOOLEAN DEFAULT true,
        custom_css TEXT DEFAULT '',
        updated_at TIMESTAMPTZ DEFAULT now()
    )""",
    """CREATE TABLE IF NOT EXISTS tenant_app_config (
        user_id TEXT PRIMARY KEY,
        app_name TEXT DEFAULT '客服小秘',
        welcome_message TEXT DEFAULT '您好，请问有什么可以帮您？',
        offline_message TEXT DEFAULT '当前无客服在线，请稍后再试。',
        auto_reply_enabled BOOLEAN DEFAULT true,
        notification_enabled BOOLEAN DEFAULT true,
        voice_enabled BOOLEAN DEFAULT false,
        language TEXT DEFAULT 'zh-CN',
        session_timeout INTEGER DEFAULT 300,
        max_queue_size INTEGER DEFAULT 50,
        file_upload_enabled BOOLEAN DEFAULT true,
        updated_at TIMESTAMPTZ DEFAULT now()
    )""",
]

_EXPECTED_TABLES = {
    "users", "user_devices", "keyword_rules", "model_configs",
    "blacklist", "tenant_style_config", "tenant_app_config",
}


def bootstrap() -> None:
    if not health_check():
        print("ERROR: Cannot connect to Supabase. Check SUPABASE_DB_URL.", file=sys.stderr)
        sys.exit(1)
    with get_connection() as conn:
        with conn.cursor() as cur:
            for ddl in _DDL_STATEMENTS:
                cur.execute(ddl)
        conn.commit()
    print(f"OK: {len(_DDL_STATEMENTS)} tables ensured.")


def check() -> None:
    if not health_check():
        print("ERROR: Cannot connect to Supabase.", file=sys.stderr)
        sys.exit(1)
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT table_name FROM information_schema.tables "
                "WHERE table_schema='public' AND table_name = ANY(%s)",
                (list(_EXPECTED_TABLES),),
            )
            existing = {r[0] for r in cur.fetchall()}
    missing = _EXPECTED_TABLES - existing
    if missing:
        print(f"MISSING: {sorted(missing)}", file=sys.stderr)
        sys.exit(1)
    print(f"OK: all {len(_EXPECTED_TABLES)} tables present.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Verify tables exist")
    args = parser.parse_args()
    if args.check:
        check()
    else:
        bootstrap()