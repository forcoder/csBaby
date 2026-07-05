"""Phase 1 双写测试 - T1.1 ~ T1.4 OpenSpec 用例。

约束:
  - mock 掉 RDS_DB_URL 检查 + 真实网络
  - 通过 patch 替换 upsert_to_supabase / upsert_to_mysql
  - 用临时 SQLite 隔离 outbox,验证失败行为

Why:
  Phase 1 要求 sync_writer 在 RDS + Supabase 双写,
  任一失败 → outbox.enqueue(记录失败原因),
  API 写入仍 200 成功(本地 SQLite 已写)。
How:
  覆盖 4 个场景:
    T1.1 双写都成功 → outbox 为空
    T1.2 RDS 失败 → Supabase 写成功,outbox 记录 mysql 失败
    T1.3 Supabase 失败 → RDS 写成功,outbox 记录 supabase 失败
    T1.4 双失败 → outbox 记录,标记双失败,API 不抛
"""
import json
import pytest
from unittest.mock import patch
from infrastructure.persistence.database import get_connection, init_db
from infrastructure.sync.sync_writer import SyncWriter


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


@pytest.fixture
def enable_rds(monkeypatch):
    """让 SyncWriter.push 启用 RDS 双写路径。

    真实生产环境会设 RDS_DB_URL;测试需要 monkeypatch 避免真连。
    """
    monkeypatch.setenv("RDS_DB_URL", "mysql://test:test@localhost:3306/test")


# ========== T1.1 双写成功 ==========

def test_dual_write_both_success_no_outbox(writer, db, enable_rds):
    """T1.1: RDS + Supabase 都成功 → outbox 为空"""
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase", return_value=None), \
         patch("infrastructure.sync.sync_writer.upsert_to_mysql", return_value=None):
        writer.push("keyword_rules", "INSERT", 1, {"id": 1, "keyword": "hi"})
    outbox = db.execute("SELECT * FROM sync_outbox").fetchall()
    assert outbox == [], f"expected empty outbox, got {len(outbox)} rows"


def test_dual_write_both_called_in_order(writer, db, enable_rds):
    """T1.1 强化: 双写都实际被调用一次"""
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase", return_value=None) as mock_supa, \
         patch("infrastructure.sync.sync_writer.upsert_to_mysql", return_value=None) as mock_mysql:
        writer.push("keyword_rules", "INSERT", 1, {"id": 1, "keyword": "hi"})
    assert mock_supa.call_count == 1
    assert mock_mysql.call_count == 1
    # 调用参数一致
    assert mock_supa.call_args == mock_mysql.call_args


# ========== T1.2 RDS 失败回退 ==========

def test_dual_write_rds_failure_enqueues_outbox(writer, db, enable_rds):
    """T1.2: RDS 失败 → Supabase 已写,outbox 记录 mysql 失败"""
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase", return_value=None), \
         patch("infrastructure.sync.sync_writer.upsert_to_mysql",
               side_effect=Exception("rds connection refused")):
        # 不应抛异常
        writer.push("keyword_rules", "INSERT", 1, {"id": 1, "keyword": "hi"})
    outbox = db.execute("SELECT * FROM sync_outbox").fetchall()
    assert len(outbox) == 1
    row = outbox[0]
    assert row["table_name"] == "keyword_rules"
    assert "rds" in row["last_error"].lower() or "mysql" in row["last_error"].lower()
    assert "connection refused" in row["last_error"]


# ========== T1.3 Supabase 失败回退 ==========

def test_dual_write_supabase_failure_enqueues_outbox(writer, db, enable_rds):
    """T1.3: Supabase 失败 → RDS 已写,outbox 记录 supabase 失败"""
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase",
               side_effect=Exception("supabase timeout")), \
         patch("infrastructure.sync.sync_writer.upsert_to_mysql", return_value=None):
        writer.push("keyword_rules", "INSERT", 1, {"id": 1, "keyword": "hi"})
    outbox = db.execute("SELECT * FROM sync_outbox").fetchall()
    assert len(outbox) == 1
    row = outbox[0]
    assert "supabase" in row["last_error"].lower()
    assert "timeout" in row["last_error"]


# ========== T1.4 双失败不静默 ==========

def test_dual_write_both_failure_enqueues(writer, db, enable_rds):
    """T1.4: 双库都失败 → outbox 累积 1 条,记录 last_error(取 mysql 或合并)"""
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase",
               side_effect=Exception("supabase down")), \
         patch("infrastructure.sync.sync_writer.upsert_to_mysql",
               side_effect=Exception("rds down")):
        # API 调用方不应感知到失败
        writer.push("keyword_rules", "INSERT", 1, {"id": 1, "keyword": "hi"})
    outbox = db.execute("SELECT * FROM sync_outbox").fetchall()
    assert len(outbox) == 1
    err = outbox[0]["last_error"]
    # 双失败的 error 信息至少包含一侧的提示
    assert ("rds" in err.lower() or "mysql" in err.lower()
            or "supabase" in err.lower())


# ========== DELETE 操作双写也走 upsert ==========

def test_dual_write_delete_op_does_not_require_payload(writer, db, enable_rds):
    """DELETE 也需双写,RDS/Supabase 两侧都不应被 payload 缺失阻断"""
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase", return_value=None) as mock_supa, \
         patch("infrastructure.sync.sync_writer.upsert_to_mysql", return_value=None) as mock_mysql:
        writer.push("keyword_rules", "DELETE", 42, None)
    assert mock_supa.call_count == 1
    assert mock_mysql.call_count == 1
    outbox = db.execute("SELECT * FROM sync_outbox").fetchall()
    assert outbox == []


# ========== T1.5 RDS_DB_URL 未配置时优雅降级 ==========

def test_dual_write_rds_disabled_gracefully(writer, db, monkeypatch):
    """未设 RDS_DB_URL 时,SyncWriter 仅写 Supabase,不写 outbox(无失败语义)"""
    monkeypatch.delenv("RDS_DB_URL", raising=False)
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase", return_value=None) as mock_supa, \
         patch("infrastructure.sync.sync_writer.upsert_to_mysql") as mock_mysql:
        writer.push("keyword_rules", "INSERT", 1, {"id": 1, "keyword": "hi"})
    assert mock_supa.call_count == 1
    assert mock_mysql.call_count == 0  # 跳过
    outbox = db.execute("SELECT * FROM sync_outbox").fetchall()
    assert outbox == []