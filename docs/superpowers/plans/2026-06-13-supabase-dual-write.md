# Supabase 双写同步实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 7 张业务表（5 张配置表 + users + user_devices）双写到 Supabase Postgres，为后续切换主库做准备

**Architecture:** 在本地 SQLite 之上叠加双写层：API 写本地后异步推 Supabase（不阻塞 API），失败入 outbox 表由独立 worker 重试。读路径不变。

**Tech Stack:** Python 3.x / Flask / SQLite / psycopg2-binary / Supabase Postgres / pytest

**Spec:** `docs/superpowers/specs/2026-06-13-supabase-dual-write-design.md`

---

## 文件变更总览

**新增**:
- `infrastructure/sync/__init__.py`
- `infrastructure/sync/sync_outbox_repo.py`
- `infrastructure/sync/sync_writer.py`
- `infrastructure/sync/retry_worker.py`
- `infrastructure/persistence/db_supabase.py`
- `infrastructure/persistence/user_repo_sqlite.py`
- `infrastructure/persistence/blacklist_repo_sqlite.py`
- `infrastructure/persistence/tenant_style_repo_sqlite.py`
- `infrastructure/persistence/tenant_app_repo_sqlite.py`
- `infrastructure/persistence/user_device_repo_sqlite.py`
- `scripts/bootstrap_supabase.py`
- `scripts/.env.example`
- `tests/infrastructure/sync/__init__.py`
- `tests/infrastructure/sync/test_sync_outbox_repo.py`
- `tests/infrastructure/sync/test_db_supabase.py`
- `tests/infrastructure/sync/test_sync_writer.py`
- `tests/infrastructure/sync/test_retry_worker.py`
- `tests/integration/test_dual_write_e2e.py`
- `tests/conftest.py`

**修改**:
- `requirements.txt` (加 `psycopg2-binary`)
- `infrastructure/persistence/database.py` (加 sync_outbox DDL + 7 张表 DDL 增强)
- `infrastructure/persistence/rule_repo_sqlite.py` (接入 sync_writer)
- `infrastructure/persistence/model_repo_sqlite.py` (接入 sync_writer)
- `infrastructure/persistence/device_repo_sqlite.py` (接入 sync_writer，user_devices)
- `app.py` (启动 sync_writer + bootstrap check)
- `render.yaml` (新增 retry worker 服务)

---

## Phase 1: 基础设施

### Task 1: 添加 psycopg2-binary 依赖

**Files:**
- Modify: `requirements.txt`

- [ ] **Step 1: 在 requirements.txt 添加 psycopg2-binary**

在 `requirements.txt` 末尾新增一行：

```
psycopg2-binary>=2.9.9
```

- [ ] **Step 2: 安装依赖**

```bash
pip install -r requirements.txt
```

Expected: 安装成功，无编译错误

- [ ] **Step 3: 验证导入**

```bash
python -c "import psycopg2; print(psycopg2.__version__)"
```

Expected: 输出形如 `2.9.9 (dt dec pq3 ext lo64)`

- [ ] **Step 4: Commit**

```bash
git add requirements.txt
git commit -m "chore: 添加 psycopg2-binary 依赖"
```

---

### Task 2: 扩展 database.py — 添加 sync_outbox DDL

**Files:**
- Modify: `infrastructure/persistence/database.py:339-345` (在 `db.executescript` 内追加)
- Modify: `infrastructure/persistence/database.py:342` (init_db 末尾调用)

- [ ] **Step 1: 在 init_db 的 executescript 内追加 outbox + dead_letter DDL**

在 `infrastructure/persistence/database.py` 第 339 行 `""")` 之前追加：

```sql
        CREATE TABLE IF NOT EXISTS sync_outbox (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            table_name TEXT NOT NULL,
            op TEXT NOT NULL CHECK(op IN ('INSERT','UPDATE','DELETE')),
            row_id INTEGER,
            payload TEXT NOT NULL,
            attempts INTEGER DEFAULT 0,
            last_error TEXT,
            next_retry_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );
        CREATE INDEX IF NOT EXISTS idx_outbox_next_retry ON sync_outbox(next_retry_at);

        CREATE TABLE IF NOT EXISTS sync_outbox_dead (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            table_name TEXT NOT NULL,
            op TEXT NOT NULL,
            row_id INTEGER,
            payload TEXT NOT NULL,
            attempts INTEGER,
            last_error TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            moved_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );
```

- [ ] **Step 2: 验证语法**

```bash
python -c "from infrastructure.persistence.database import init_db; init_db(); print('OK')"
```

Expected: `OK`（无 traceback）

- [ ] **Step 3: 验证表创建**

```bash
python -c "
from infrastructure.persistence.database import get_connection
db = get_connection()
rows = db.execute(\"SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'sync_%'\").fetchall()
print([r['name'] for r in rows])
"
```

Expected: `['sync_outbox', 'sync_outbox_dead']`

- [ ] **Step 4: Commit**

```bash
git add infrastructure/persistence/database.py
git commit -m "feat(sync): 添加 sync_outbox / sync_outbox_dead 表 DDL"
```

---

### Task 3: 创建 SyncOutboxRepository（TDD）

**Files:**
- Create: `infrastructure/sync/__init__.py`
- Create: `infrastructure/sync/sync_outbox_repo.py`
- Create: `tests/infrastructure/sync/__init__.py`
- Create: `tests/infrastructure/sync/test_sync_outbox_repo.py`

- [ ] **Step 1: 创建包目录**

```bash
mkdir -p infrastructure/sync tests/infrastructure/sync
touch infrastructure/sync/__init__.py tests/infrastructure/sync/__init__.py
```

- [ ] **Step 2: 写失败测试**

创建 `tests/infrastructure/sync/test_sync_outbox_repo.py`：

```python
import pytest
from infrastructure.persistence.database import get_connection, init_db
from infrastructure.sync.sync_outbox_repo import SyncOutboxRepository


@pytest.fixture
def db():
    init_db()
    db = get_connection()
    db.execute("DELETE FROM sync_outbox")
    db.execute("DELETE FROM sync_outbox_dead")
    db.commit()
    yield db
    db.close()


@pytest.fixture
def repo(db):
    return SyncOutboxRepository(db)


def test_enqueue_inserts_row(repo, db):
    row_id = repo.enqueue(
        table_name="keyword_rules",
        op="INSERT",
        row_id=42,
        payload={"user_id": "u1", "keyword": "hi"},
    )
    assert row_id > 0
    row = db.execute("SELECT * FROM sync_outbox WHERE id=?", (row_id,)).fetchone()
    assert row["table_name"] == "keyword_rules"
    assert row["op"] == "INSERT"
    assert row["attempts"] == 0


def test_claim_due_returns_only_due_rows(repo, db):
    repo.enqueue("t1", "INSERT", 1, {"a": 1})
    repo.enqueue("t1", "INSERT", 2, {"a": 2})
    # 把第二行的 next_retry_at 推迟到未来
    db.execute("UPDATE sync_outbox SET next_retry_at = datetime('now', '+1 hour') WHERE row_id=2")
    db.commit()
    rows = repo.claim_due(limit=10)
    assert len(rows) == 1
    assert rows[0]["row_id"] == 1


def test_mark_done_deletes_row(repo):
    rid = repo.enqueue("t1", "INSERT", 1, {})
    repo.mark_done(rid)
    db = get_connection()
    rows = db.execute("SELECT * FROM sync_outbox WHERE id=?", (rid,)).fetchall()
    assert rows == []


def test_mark_failed_increments_attempts_and_backoff(repo):
    rid = repo.enqueue("t1", "INSERT", 1, {})
    repo.mark_failed(rid, "connection timeout", base_time="2026-06-14 00:00:00")
    db = get_connection()
    row = db.execute("SELECT * FROM sync_outbox WHERE id=?", (rid,)).fetchone()
    assert row["attempts"] == 1
    assert row["last_error"] == "connection timeout"
    # 第一次重试：+10 秒
    assert "2026-06-14 00:00:10" in row["next_retry_at"]


def test_mark_failed_exponential_backoff(repo):
    rid = repo.enqueue("t1", "INSERT", 1, {})
    # 第 5 次重试应该 +1 小时
    db = get_connection()
    db.execute("UPDATE sync_outbox SET attempts=4 WHERE id=?", (rid,))
    db.commit()
    repo.mark_failed(rid, "err", base_time="2026-06-14 00:00:00")
    row = db.execute("SELECT * FROM sync_outbox WHERE id=?", (rid,)).fetchone()
    assert "2026-06-14 01:00:00" in row["next_retry_at"]


def test_move_to_dead_letter_after_max_attempts(repo):
    rid = repo.enqueue("t1", "INSERT", 1, {})
    db = get_connection()
    db.execute("UPDATE sync_outbox SET attempts=10 WHERE id=?", (rid,))
    db.commit()
    repo.mark_failed(rid, "permanent failure", base_time="2026-06-14 00:00:00")
    outbox_rows = db.execute("SELECT * FROM sync_outbox WHERE id=?", (rid,)).fetchall()
    dead_rows = db.execute("SELECT * FROM sync_outbox_dead WHERE row_id=1").fetchall()
    assert outbox_rows == []
    assert len(dead_rows) == 1
    assert dead_rows[0]["last_error"] == "permanent failure"
```

- [ ] **Step 3: 运行测试确认失败**

```bash
pytest tests/infrastructure/sync/test_sync_outbox_repo.py -v
```

Expected: `ModuleNotFoundError: No module named 'infrastructure.sync.sync_outbox_repo'`

- [ ] **Step 4: 实现 SyncOutboxRepository**

创建 `infrastructure/sync/sync_outbox_repo.py`：

```python
import json
from typing import List, Optional

# 重试退避策略（秒）
_BACKOFF_SECONDS = [10, 30, 120, 600, 3600]  # 10s, 30s, 2min, 10min, 1h
_MAX_ATTEMPTS = 10


class SyncOutboxRepository:
    def __init__(self, db):
        self.db = db

    def enqueue(self, table_name: str, op: str, row_id: Optional[int], payload: dict) -> int:
        cursor = self.db.execute(
            """INSERT INTO sync_outbox (table_name, op, row_id, payload)
               VALUES (?, ?, ?, ?)""",
            (table_name, op, row_id, json.dumps(payload, ensure_ascii=False)),
        )
        self.db.commit()
        return cursor.lastrowid

    def claim_due(self, limit: int = 50) -> List[dict]:
        rows = self.db.execute(
            """SELECT * FROM sync_outbox
               WHERE next_retry_at <= CURRENT_TIMESTAMP
               ORDER BY id ASC LIMIT ?""",
            (limit,),
        ).fetchall()
        return [dict(r) for r in rows]

    def mark_done(self, row_id: int) -> None:
        self.db.execute("DELETE FROM sync_outbox WHERE id=?", (row_id,))
        self.db.commit()

    def mark_failed(self, row_id: int, error: str, base_time: Optional[str] = None) -> None:
        row = self.db.execute("SELECT attempts FROM sync_outbox WHERE id=?", (row_id,)).fetchone()
        if not row:
            return
        new_attempts = row["attempts"] + 1
        if new_attempts >= _MAX_ATTEMPTS:
            self._move_to_dead_letter(row_id, new_attempts, error)
            return
        delay = _BACKOFF_SECONDS[min(new_attempts - 1, len(_BACKOFF_SECONDS) - 1)]
        self.db.execute(
            """UPDATE sync_outbox
               SET attempts=?, last_error=?,
                   next_retry_at = datetime(?, '+' || ? || ' seconds'),
                   updated_at=CURRENT_TIMESTAMP
               WHERE id=?""",
            (new_attempts, error, base_time or "now", delay, row_id),
        )
        self.db.commit()

    def _move_to_dead_letter(self, row_id: int, attempts: int, error: str) -> None:
        row = self.db.execute("SELECT * FROM sync_outbox WHERE id=?", (row_id,)).fetchone()
        if not row:
            return
        self.db.execute(
            """INSERT INTO sync_outbox_dead
               (table_name, op, row_id, payload, attempts, last_error)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (row["table_name"], row["op"], row["row_id"], row["payload"], attempts, error),
        )
        self.db.execute("DELETE FROM sync_outbox WHERE id=?", (row_id,))
        self.db.commit()
```

- [ ] **Step 5: 运行测试确认通过**

```bash
pytest tests/infrastructure/sync/test_sync_outbox_repo.py -v
```

Expected: 6 passed

- [ ] **Step 6: Commit**

```bash
git add infrastructure/sync/ tests/infrastructure/sync/
git commit -m "feat(sync): 实现 SyncOutboxRepository（含指数退避与死信表）"
```

---

### Task 4: 创建 db_supabase.py 连接池（TDD）

**Files:**
- Create: `infrastructure/persistence/db_supabase.py`
- Create: `tests/infrastructure/sync/test_db_supabase.py`

- [ ] **Step 1: 写失败测试**

创建 `tests/infrastructure/sync/test_db_supabase.py`：

```python
import os
import pytest
from unittest.mock import patch, MagicMock


def test_get_connection_returns_pool_instance():
    from infrastructure.persistence.db_supabase import get_pool, _reset_pool
    _reset_pool()
    with patch.dict(os.environ, {"SUPABASE_DB_URL": "postgresql://u:p@h:5432/db"}):
        pool = get_pool()
        assert pool is not None


def test_get_connection_acquires_from_pool():
    from infrastructure.persistence.db_supabase import get_connection, _reset_pool
    _reset_pool()
    with patch.dict(os.environ, {"SUPABASE_DB_URL": "postgresql://u:p@h:5432/db"}):
        mock_pool = MagicMock()
        mock_conn = MagicMock()
        mock_pool.getconn.return_value = mock_conn
        with patch("infrastructure.persistence.db_supabase.get_pool", return_value=mock_pool):
            conn = get_connection()
            assert conn is mock_conn


def test_health_check_returns_true_when_ping_ok():
    from infrastructure.persistence.db_supabase import health_check
    with patch("infrastructure.persistence.db_supabase.get_connection") as mock_gc:
        mock_conn = MagicMock()
        mock_gc.return_value.__enter__.return_value = mock_conn
        result = health_check()
        assert result is True
        mock_conn.execute.assert_called()


def test_health_check_returns_false_on_error():
    from infrastructure.persistence.db_supabase import health_check
    with patch("infrastructure.persistence.db_supabase.get_connection", side_effect=Exception("conn refused")):
        result = health_check()
        assert result is False
```

- [ ] **Step 2: 运行测试确认失败**

```bash
pytest tests/infrastructure/sync/test_db_supabase.py -v
```

Expected: `ModuleNotFoundError: No module named 'infrastructure.persistence.db_supabase'`

- [ ] **Step 3: 实现 db_supabase.py**

创建 `infrastructure/persistence/db_supabase.py`：

```python
import os
from contextlib import contextmanager
from typing import Optional

import psycopg2
from psycopg2 import pool as pg_pool
from psycopg2.extras import RealDictCursor

_pool: Optional[pg_pool.ThreadedConnectionPool] = None


def _reset_pool() -> None:
    """Test helper: reset module-level pool."""
    global _pool
    if _pool is not None:
        _pool.closeall()
    _pool = None


def get_pool() -> pg_pool.ThreadedConnectionPool:
    global _pool
    if _pool is not None:
        return _pool
    url = os.environ.get("SUPABASE_DB_URL")
    if not url:
        raise RuntimeError("SUPABASE_DB_URL environment variable not set")
    _pool = pg_pool.ThreadedConnectionPool(minconn=1, maxconn=5, dsn=url)
    return _pool


@contextmanager
def get_connection():
    pool = get_pool()
    conn = pool.getconn()
    try:
        yield conn
    finally:
        pool.putconn(conn)


def health_check() -> bool:
    try:
        with get_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT 1")
                cur.fetchone()
        return True
    except Exception:
        return False
```

- [ ] **Step 4: 运行测试确认通过**

```bash
pytest tests/infrastructure/sync/test_db_supabase.py -v
```

Expected: 4 passed

- [ ] **Step 5: Commit**

```bash
git add infrastructure/persistence/db_supabase.py tests/infrastructure/sync/test_db_supabase.py
git commit -m "feat(sync): 实现 Supabase 连接池与 health_check"
```

---

### Task 5: 创建 bootstrap_supabase.py

**Files:**
- Create: `scripts/bootstrap_supabase.py`

- [ ] **Step 1: 实现脚本**

创建 `scripts/bootstrap_supabase.py`：

```python
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
```

- [ ] **Step 2: 验证脚本可解析**

```bash
python scripts/bootstrap_supabase.py --help
```

Expected: 显示 usage 信息，无 traceback

- [ ] **Step 3: Commit**

```bash
git add scripts/bootstrap_supabase.py
git commit -m "feat(sync): 添加 Supabase 表结构 bootstrap 脚本"
```

---

### Task 6: 创建 SyncWriter（TDD）

**Files:**
- Create: `infrastructure/sync/sync_writer.py`
- Create: `tests/infrastructure/sync/test_sync_writer.py`

- [ ] **Step 1: 写失败测试**

创建 `tests/infrastructure/sync/test_sync_writer.py`：

```python
import json
import pytest
from unittest.mock import patch, MagicMock
from infrastructure.persistence.database import get_connection, init_db
from infrastructure.sync.sync_writer import SyncWriter
from infrastructure.sync.sync_outbox_repo import SyncOutboxRepository


@pytest.fixture
def db():
    init_db()
    db = get_connection()
    db.execute("DELETE FROM sync_outbox")
    db.commit()
    yield db
    db.close()


@pytest.fixture
def writer(db):
    return SyncWriter(db)


def test_push_supabase_success_no_outbox(writer, db):
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase", return_value=None):
        writer.push("keyword_rules", "INSERT", 1, {"id": 1, "keyword": "hi"})
    outbox = db.execute("SELECT * FROM sync_outbox").fetchall()
    assert outbox == []


def test_push_supabase_failure_enqueues_outbox(writer, db):
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase",
               side_effect=Exception("connection refused")):
        writer.push("keyword_rules", "INSERT", 1, {"id": 1, "keyword": "hi"})
    outbox = db.execute("SELECT * FROM sync_outbox").fetchall()
    assert len(outbox) == 1
    assert outbox[0]["table_name"] == "keyword_rules"
    assert outbox[0]["last_error"] == "connection refused"


def test_push_delete_op_does_not_require_payload(writer, db):
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase", return_value=None):
        writer.push("keyword_rules", "DELETE", 42, None)
    outbox = db.execute("SELECT * FROM sync_outbox").fetchall()
    assert outbox == []


def test_push_payload_serialized_as_json(writer, db):
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase",
               side_effect=Exception("boom")):
        writer.push("keyword_rules", "INSERT", 1, {"keyword": "你好", "enabled": True})
    outbox = db.execute("SELECT * FROM sync_outbox").fetchall()
    payload = json.loads(outbox[0]["payload"])
    assert payload["keyword"] == "你好"
    assert payload["enabled"] is True
```

- [ ] **Step 2: 运行测试确认失败**

```bash
pytest tests/infrastructure/sync/test_sync_writer.py -v
```

Expected: `ModuleNotFoundError: No module named 'infrastructure.sync.sync_writer'`

- [ ] **Step 3: 实现 SyncWriter**

创建 `infrastructure/sync/sync_writer.py`：

```python
import json
import logging
from typing import Optional

from psycopg2.extras import execute_values, RealDictCursor

from infrastructure.persistence.db_supabase import get_connection as get_supa_conn
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

    with get_supa_conn() as conn:
        with conn.cursor() as cur:
            if op == "DELETE":
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
                )
            except Exception as ee:
                # outbox 写入也失败 → log 但不抛出（不阻塞 API）
                logger.error("sync.outbox.enqueue failed table=%s err=%s", table, ee)
```

- [ ] **Step 4: 运行测试确认通过**

```bash
pytest tests/infrastructure/sync/test_sync_writer.py -v
```

Expected: 4 passed

- [ ] **Step 5: Commit**

```bash
git add infrastructure/sync/sync_writer.py tests/infrastructure/sync/test_sync_writer.py
git commit -m "feat(sync): 实现 SyncWriter 双写编排（Supabase 失败入 outbox）"
```

---

### Task 7: 创建 RetryWorker（TDD）

**Files:**
- Create: `infrastructure/sync/retry_worker.py`
- Create: `tests/infrastructure/sync/test_retry_worker.py`

- [ ] **Step 1: 写失败测试**

创建 `tests/infrastructure/sync/test_retry_worker.py`：

```python
import pytest
from unittest.mock import patch
from infrastructure.persistence.database import get_connection, init_db
from infrastructure.sync.retry_worker import RetryWorker


@pytest.fixture
def db():
    init_db()
    db = get_connection()
    db.execute("DELETE FROM sync_outbox")
    db.execute("DELETE FROM sync_outbox_dead")
    db.commit()
    yield db
    db.close()


def test_tick_processes_due_rows_and_removes_on_success(db):
    db.execute(
        "INSERT INTO sync_outbox (table_name, op, row_id, payload) VALUES (?, ?, ?, ?)",
        ("keyword_rules", "INSERT", 1, '{"id":1,"keyword":"x"}'),
    )
    db.commit()
    worker = RetryWorker(db, batch_size=10)
    with patch("infrastructure.sync.retry_worker.upsert_to_supabase", return_value=None):
        processed = worker.tick()
    assert processed == 1
    remaining = db.execute("SELECT * FROM sync_outbox").fetchall()
    assert remaining == []


def test_tick_increments_attempts_on_failure(db):
    db.execute(
        "INSERT INTO sync_outbox (table_name, op, row_id, payload) VALUES (?, ?, ?, ?)",
        ("keyword_rules", "INSERT", 1, '{"id":1}'),
    )
    db.commit()
    worker = RetryWorker(db, batch_size=10)
    with patch("infrastructure.sync.retry_worker.upsert_to_supabase",
               side_effect=Exception("still down")):
        processed = worker.tick()
    assert processed == 1
    row = db.execute("SELECT * FROM sync_outbox WHERE row_id=1").fetchone()
    assert row["attempts"] == 1
    assert "still down" in row["last_error"]


def test_tick_skips_rows_not_yet_due(db):
    db.execute(
        "INSERT INTO sync_outbox (table_name, op, row_id, payload, next_retry_at) "
        "VALUES (?, ?, ?, ?, datetime('now', '+1 hour'))",
        ("keyword_rules", "INSERT", 1, '{"id":1}'),
    )
    db.commit()
    worker = RetryWorker(db, batch_size=10)
    with patch("infrastructure.sync.retry_worker.upsert_to_supabase") as mock_upsert:
        processed = worker.tick()
    assert processed == 0
    mock_upsert.assert_not_called()


def test_tick_moves_to_dead_after_max_attempts(db):
    db.execute(
        "INSERT INTO sync_outbox (table_name, op, row_id, payload, attempts) "
        "VALUES (?, ?, ?, ?, 9)",
        ("keyword_rules", "INSERT", 1, '{"id":1}'),
    )
    db.commit()
    worker = RetryWorker(db, batch_size=10)
    with patch("infrastructure.sync.retry_worker.upsert_to_supabase",
               side_effect=Exception("permanent")):
        worker.tick()
    outbox = db.execute("SELECT * FROM sync_outbox").fetchall()
    dead = db.execute("SELECT * FROM sync_outbox_dead").fetchall()
    assert outbox == []
    assert len(dead) == 1
    assert dead[0]["attempts"] == 10
```

- [ ] **Step 2: 运行测试确认失败**

```bash
pytest tests/infrastructure/sync/test_retry_worker.py -v
```

Expected: `ModuleNotFoundError`

- [ ] **Step 3: 实现 RetryWorker**

创建 `infrastructure/sync/retry_worker.py`：

```python
import json
import logging
import os
import time
from typing import Optional

from infrastructure.persistence.database import get_connection
from infrastructure.sync.sync_outbox_repo import SyncOutboxRepository
from infrastructure.sync.sync_writer import upsert_to_supabase

logger = logging.getLogger(__name__)


class RetryWorker:
    def __init__(self, db=None, batch_size: int = 50,
                 interval_seconds: Optional[int] = None):
        self.db = db if db is not None else get_connection()
        self.batch_size = batch_size
        self.interval_seconds = interval_seconds or int(
            os.environ.get("SYNC_RETRY_INTERVAL_SECONDS", "30")
        )
        self.outbox_repo = SyncOutboxRepository(self.db)

    def tick(self) -> int:
        """Process one batch. Returns count processed."""
        rows = self.outbox_repo.claim_due(limit=self.batch_size)
        processed = 0
        for row in rows:
            try:
                payload = json.loads(row["payload"]) if row["payload"] else None
                upsert_to_supabase(row["table_name"], row["op"], row["row_id"], payload)
                self.outbox_repo.mark_done(row["id"])
                processed += 1
            except Exception as e:
                logger.warning("retry_worker tick failed row_id=%s err=%s", row["id"], e)
                self.outbox_repo.mark_failed(row["id"], str(e))
        return processed

    def run_forever(self) -> None:
        """Entry point for `python -m infrastructure.sync.retry_worker`."""
        logger.info("retry_worker started, interval=%ss", self.interval_seconds)
        try:
            while True:
                try:
                    self.tick()
                except Exception as e:
                    logger.error("retry_worker tick error: %s", e)
                time.sleep(self.interval_seconds)
        except KeyboardInterrupt:
            logger.info("retry_worker stopped")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    RetryWorker().run_forever()
```

- [ ] **Step 4: 运行测试确认通过**

```bash
pytest tests/infrastructure/sync/test_retry_worker.py -v
```

Expected: 4 passed

- [ ] **Step 5: Commit**

```bash
git add infrastructure/sync/retry_worker.py tests/infrastructure/sync/test_retry_worker.py
git commit -m "feat(sync): 实现 RetryWorker（指数退避 + 死信表）"
```

---

## Phase 2: 业务表接入

### Task 8: 接入 RuleRepository 到 sync_writer

**Files:**
- Modify: `infrastructure/persistence/rule_repo_sqlite.py`（在 create/update/delete 后添加 sync push）

- [ ] **Step 1: 在 rule_repo_sqlite.py 顶部导入**

```python
from infrastructure.sync.sync_writer import SyncWriter
```

- [ ] **Step 2: 修改 `create` 方法**

在 `rule.id = cursor.lastrowid` 之后、`db.commit()` 之前插入：

```python
        sync_payload = {
            "id": rule.id, "user_id": rule.user_id, "keyword": rule.keyword,
            "match_type": rule.match_type, "reply_template": rule.reply_template,
            "category": rule.category, "target_type": rule.target_type,
            "target_names": json.dumps(rule.target_names),
            "priority": rule.priority, "enabled": rule.enabled,
        }
```

在 `db.commit()` 之后、`return rule` 之前插入：

```python
        SyncWriter(db).push("keyword_rules", "INSERT", rule.id, sync_payload)
```

- [ ] **Step 3: 修改 `update` 方法**

找到 `update` 方法（按 `def update(` 搜索），在事务提交后添加：

```python
        sync_payload = {
            "id": rule.id, "user_id": rule.user_id, "keyword": rule.keyword,
            "match_type": rule.match_type, "reply_template": rule.reply_template,
            "category": rule.category, "target_type": rule.target_type,
            "target_names": json.dumps(rule.target_names),
            "priority": rule.priority, "enabled": rule.enabled,
        }
        SyncWriter(db).push("keyword_rules", "UPDATE", rule.id, sync_payload)
```

- [ ] **Step 4: 修改 `delete` 方法**

找到 `delete` 方法，在事务提交后添加：

```python
        SyncWriter(db).push("keyword_rules", "DELETE", rule_id, None)
```

- [ ] **Step 5: 验证编译**

```bash
python -c "from infrastructure.persistence.rule_repo_sqlite import SqliteRuleRepository; print('OK')"
```

Expected: `OK`

- [ ] **Step 6: Commit**

```bash
git add infrastructure/persistence/rule_repo_sqlite.py
git commit -m "feat(sync): 接入 RuleRepository 到 SyncWriter（INSERT/UPDATE/DELETE）"
```

---

### Task 9: 接入 ModelRepository

**Files:**
- Modify: `infrastructure/persistence/model_repo_sqlite.py`

- [ ] **Step 1: 顶部导入**

```python
from infrastructure.sync.sync_writer import SyncWriter
```

- [ ] **Step 2: 在 `create`/`update`/`delete` 各自事务提交后添加 push 调用**

参考 Task 8 的模式。表名使用 `"model_configs"`。payload 应包含所有列：

```python
sync_payload = {
    "id": model.id, "user_id": model.user_id, "name": model.name,
    "model_type": model.model_type, "model": model.model,
    "api_key": model.api_key, "api_endpoint": model.api_endpoint,
    "temperature": model.temperature, "max_tokens": model.max_tokens,
    "is_default": model.is_default, "enabled": model.enabled,
}
```

`create`/`update` 调用 `SyncWriter(db).push("model_configs", "INSERT/UPDATE", model.id, sync_payload)`
`delete` 调用 `SyncWriter(db).push("model_configs", "DELETE", model_id, None)`

- [ ] **Step 3: 验证编译**

```bash
python -c "from infrastructure.persistence.model_repo_sqlite import SqliteModelRepository; print('OK')"
```

- [ ] **Step 4: Commit**

```bash
git add infrastructure/persistence/model_repo_sqlite.py
git commit -m "feat(sync): 接入 ModelRepository 到 SyncWriter"
```

---

### Task 10: 接入 user_devices（device_repo_sqlite.py）

**Files:**
- Modify: `infrastructure/persistence/device_repo_sqlite.py`

- [ ] **Step 1: 顶部导入**

```python
from infrastructure.sync.sync_writer import SyncWriter
```

- [ ] **Step 2: 接入 device 创建/删除流程**

对 `user_devices` 表的操作（`register_device`/`unregister_device`）在事务提交后添加：

```python
sync_payload = {
    "user_id": user_id, "device_id": device_id,
    "platform": platform, "device_name": device_name,
}
SyncWriter(db).push("user_devices", "INSERT/DELETE", None, sync_payload)
```

注意：`user_devices` 没有单一 id，使用 `(user_id, device_id)` 复合键——payload 必须包含两个键值以便 Supabase 端 DELETE 操作定位。

- [ ] **Step 3: 验证编译 + Commit**

```bash
python -c "from infrastructure.persistence.device_repo_sqlite import SqliteDeviceRepository; print('OK')"
git add infrastructure/persistence/device_repo_sqlite.py
git commit -m "feat(sync): 接入 user_devices 到 SyncWriter"
```

---

### Task 11: 接入 users（新增 user_repo_sqlite.py）

**Files:**
- Create: `infrastructure/persistence/user_repo_sqlite.py`

- [ ] **Step 1: 实现 UserRepository**

创建 `infrastructure/persistence/user_repo_sqlite.py`：

```python
import json
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
```

- [ ] **Step 2: 验证 + Commit**

```bash
python -c "from infrastructure.persistence.user_repo_sqlite import SqliteUserRepository; print('OK')"
git add infrastructure/persistence/user_repo_sqlite.py
git commit -m "feat(sync): 新增 SqliteUserRepository 并接入 SyncWriter"
```

---

### Task 12: 接入 blacklist / tenant_style / tenant_app

**Files:**
- Create: `infrastructure/persistence/blacklist_repo_sqlite.py`
- Create: `infrastructure/persistence/tenant_style_repo_sqlite.py`
- Create: `infrastructure/persistence/tenant_app_repo_sqlite.py`

- [ ] **Step 1: 创建 blacklist_repo_sqlite.py**

```python
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
```

- [ ] **Step 2: 创建 tenant_style_repo_sqlite.py**

```python
from infrastructure.persistence.database import get_connection
from infrastructure.sync.sync_writer import SyncWriter


class SqliteTenantStyleRepository:
    def upsert(self, user_id: str, **fields) -> None:
        db = get_connection()
        # 简化：直接全字段 upsert（生产环境应做增量）
        defaults = {
            "theme": "light", "primary_color": "#1976D2", "accent_color": "#FF4081",
            "font_size": "medium", "bubble_style": "rounded",
            "avatar_enabled": 1, "show_timestamp": 1, "send_sound": 1,
            "custom_css": "",
        }
        merged = {**defaults, **fields}
        db.execute(
            """INSERT INTO tenant_style_config (user_id, theme, primary_color, accent_color,
               font_size, bubble_style, avatar_enabled, show_timestamp, send_sound, custom_css)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(user_id) DO UPDATE SET
                 theme=excluded.theme, primary_color=excluded.primary_color,
                 accent_color=excluded.accent_color, font_size=excluded.font_size,
                 bubble_style=excluded.bubble_style,
                 avatar_enabled=excluded.avatar_enabled,
                 show_timestamp=excluded.show_timestamp,
                 send_sound=excluded.send_sound,
                 custom_css=excluded.custom_css""",
            (user_id, merged["theme"], merged["primary_color"], merged["accent_color"],
             merged["font_size"], merged["bubble_style"], merged["avatar_enabled"],
             merged["show_timestamp"], merged["send_sound"], merged["custom_css"]),
        )
        db.commit()
        SyncWriter(db).push("tenant_style_config", "UPDATE", None, {"user_id": user_id, **merged})
        db.close()
```

- [ ] **Step 3: 创建 tenant_app_repo_sqlite.py**

```python
from infrastructure.persistence.database import get_connection
from infrastructure.sync.sync_writer import SyncWriter


class SqliteTenantAppRepository:
    def upsert(self, user_id: str, **fields) -> None:
        db = get_connection()
        defaults = {
            "app_name": "客服小秘",
            "welcome_message": "您好，请问有什么可以帮您？",
            "offline_message": "当前无客服在线，请稍后再试。",
            "auto_reply_enabled": 1, "notification_enabled": 1, "voice_enabled": 0,
            "language": "zh-CN", "session_timeout": 300, "max_queue_size": 50,
            "file_upload_enabled": 1,
        }
        merged = {**defaults, **fields}
        db.execute(
            """INSERT INTO tenant_app_config (user_id, app_name, welcome_message,
               offline_message, auto_reply_enabled, notification_enabled, voice_enabled,
               language, session_timeout, max_queue_size, file_upload_enabled)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(user_id) DO UPDATE SET
                 app_name=excluded.app_name, welcome_message=excluded.welcome_message,
                 offline_message=excluded.offline_message,
                 auto_reply_enabled=excluded.auto_reply_enabled,
                 notification_enabled=excluded.notification_enabled,
                 voice_enabled=excluded.voice_enabled, language=excluded.language,
                 session_timeout=excluded.session_timeout,
                 max_queue_size=excluded.max_queue_size,
                 file_upload_enabled=excluded.file_upload_enabled""",
            (user_id, merged["app_name"], merged["welcome_message"],
             merged["offline_message"], merged["auto_reply_enabled"],
             merged["notification_enabled"], merged["voice_enabled"],
             merged["language"], merged["session_timeout"],
             merged["max_queue_size"], merged["file_upload_enabled"]),
        )
        db.commit()
        SyncWriter(db).push("tenant_app_config", "UPDATE", None, {"user_id": user_id, **merged})
        db.close()
```

- [ ] **Step 4: 验证 + Commit**

```bash
python -c "
from infrastructure.persistence.blacklist_repo_sqlite import SqliteBlacklistRepository
from infrastructure.persistence.tenant_style_repo_sqlite import SqliteTenantStyleRepository
from infrastructure.persistence.tenant_app_repo_sqlite import SqliteTenantAppRepository
print('OK')
"
git add infrastructure/persistence/blacklist_repo_sqlite.py infrastructure/persistence/tenant_style_repo_sqlite.py infrastructure/persistence/tenant_app_repo_sqlite.py
git commit -m "feat(sync): 新增 blacklist/tenant_style/tenant_app repos 并接入 SyncWriter"
```

---

## Phase 3: 集成与部署

### Task 13: 启动健康检查 + 双写状态端点

**Files:**
- Modify: `app.py`（在现有 `/health` 端点附近扩展）

- [ ] **Step 1: 修改 `/health` 端点返回 supabase 状态**

找到 `app.py` 中：

```python
@app.route("/health", methods=["GET"])
def health_check():
    ...
```

替换为：

```python
@app.route("/health", methods=["GET"])
def health_check():
    from infrastructure.persistence.db_supabase import health_check as supa_health
    return {
        "status": "ok",
        "service": "csbaby-app",
        "supabase": "up" if supa_health() else "down",
    }, 200
```

- [ ] **Step 2: 验证启动**

```bash
python -c "from app import app; print('OK')"
```

- [ ] **Step 3: Commit**

```bash
git add app.py
git commit -m "feat(sync): /health 端点返回 supabase 状态"
```

---

### Task 14: 端到端集成测试

**Files:**
- Create: `tests/conftest.py`
- Create: `tests/integration/test_dual_write_e2e.py`

- [ ] **Step 1: 创建 conftest.py**

```python
import os
import pytest


@pytest.fixture(autouse=True)
def reset_outbox():
    from infrastructure.persistence.database import init_db, get_connection
    init_db()
    db = get_connection()
    db.execute("DELETE FROM sync_outbox")
    db.execute("DELETE FROM sync_outbox_dead")
    db.commit()
    db.close()
    yield
```

- [ ] **Step 2: 写集成测试（mock Supabase）**

```python
from unittest.mock import patch
from infrastructure.persistence.rule_repo_sqlite import SqliteRuleRepository
from domain.entities.keyword_rule import KeywordRule


def test_create_rule_writes_local_and_pushes_to_supabase():
    rule = KeywordRule(
        user_id="u1", keyword="你好", match_type="CONTAINS",
        reply_template="您好！", category="问候", target_type="ALL",
        target_names=[], priority=5, enabled=True,
    )
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase") as mock_upsert:
        SqliteRuleRepository().create(rule)
        assert mock_upsert.called
        call_args = mock_upsert.call_args
        assert call_args[0][0] == "keyword_rules"
        assert call_args[0][1] == "INSERT"


def test_create_rule_supabase_failure_leaves_outbox():
    rule = KeywordRule(user_id="u1", keyword="hi", reply_template="r",
                       match_type="CONTAINS", category="", target_type="ALL",
                       target_names=[], priority=0, enabled=True)
    from infrastructure.persistence.database import get_connection
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase",
               side_effect=Exception("supabase down")):
        SqliteRuleRepository().create(rule)
    db = get_connection()
    outbox = db.execute("SELECT * FROM sync_outbox").fetchall()
    assert len(outbox) == 1
    assert "supabase down" in outbox[0]["last_error"]


def test_retry_worker_drains_outbox_after_supabase_recovers():
    from infrastructure.persistence.database import get_connection
    from infrastructure.sync.retry_worker import RetryWorker

    rule = KeywordRule(user_id="u1", keyword="hi", reply_template="r",
                       match_type="CONTAINS", category="", target_type="ALL",
                       target_names=[], priority=0, enabled=True)

    # 阶段 1: Supabase 故障 → outbox 堆积
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase",
               side_effect=Exception("down")):
        SqliteRuleRepository().create(rule)
    db = get_connection()
    assert db.execute("SELECT COUNT(*) AS c FROM sync_outbox").fetchone()["c"] == 1

    # 阶段 2: Supabase 恢复 → worker 清空 outbox
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase", return_value=None):
        RetryWorker(db).tick()

    assert db.execute("SELECT COUNT(*) AS c FROM sync_outbox").fetchone()["c"] == 0
```

- [ ] **Step 3: 运行集成测试**

```bash
pytest tests/integration/test_dual_write_e2e.py -v
```

Expected: 3 passed

- [ ] **Step 4: 全量测试**

```bash
pytest tests/ -v --tb=short
```

Expected: 全通过；如有失败按 TDD 流程修复

- [ ] **Step 5: Commit**

```bash
git add tests/
git commit -m "test(sync): 添加端到端集成测试（双写 + 重试链路）"
```

---

### Task 15: 部署配置 — Render Background Worker

**Files:**
- Modify: `render.yaml`（如有；或新增）

- [ ] **Step 1: 在 render.yaml 添加 retry_worker 服务**

如果项目根目录没有 `render.yaml`，跳过此 task。否则追加：

```yaml
  - type: worker
    name: csbaby-sync-retry-worker
    runtime: python
    plan: free
    buildCommand: pip install -r requirements.txt
    startCommand: python -m infrastructure.sync.retry_worker
    envVars:
      - key: DATABASE_PATH
        sync: false
      - key: SUPABASE_DB_URL
        sync: false
```

- [ ] **Step 2: 更新 .env.example**

创建 `scripts/.env.example`：

```bash
# Supabase (existing)
SUPABASE_URL=https://xxx.supabase.co

# Supabase Postgres (NEW — sync dual-write)
SUPABASE_DB_URL=postgresql://postgres.[ref]:[password]@aws-0-[region].pooler.supabase.com:6543/postgres

# Sync worker (NEW)
SYNC_RETRY_INTERVAL_SECONDS=30
SYNC_MAX_ATTEMPTS=10
SYNC_SUPABASE_TIMEOUT_SECONDS=5
```

- [ ] **Step 3: Commit**

```bash
git add render.yaml scripts/.env.example
git commit -m "chore(sync): 添加 retry worker 部署配置 + .env 模板"
```

---

### Task 16: 最终验证

- [ ] **Step 1: 运行全量测试**

```bash
pytest tests/ -v --tb=short --cov=infrastructure/sync --cov=infrastructure/persistence --cov-report=term-missing
```

Expected: 覆盖率 ≥ 85%

- [ ] **Step 2: 验证 Flask 启动**

```bash
SUPABASE_DB_URL=postgresql://fake python -c "
from app import app
print('Flask loaded OK')
"
```

Expected: `Flask loaded OK`（不实际连接 Supabase）

- [ ] **Step 3: 验证 retry_worker 入口**

```bash
python -m infrastructure.sync.retry_worker --help 2>&1 | head -3 || python -c "
import infrastructure.sync.retry_worker as m
print('module loaded:', m.__name__)
"
```

Expected: 模块可加载

- [ ] **Step 4: 提交完成报告**

```bash
git log --oneline | head -20
```

---

## 验收清单

- [ ] 18 个 commit 全部成功
- [ ] 全部测试通过（单元 + 集成）
- [ ] 覆盖率 ≥ 85%
- [ ] `python scripts/bootstrap_supabase.py --check` 提示表存在
- [ ] Flask 启动 + `/health` 返回 `supabase: up`
- [ ] retry worker 进程可启动