"""Integration tests: verify the 4 newly wired repos invoke SyncWriter correctly.

These tests pin the dual-write contract: every mutating call must push the
resulting row to Supabase (mocked here) so the sync feature works for
knowledge base feedback, reply history, optimization metrics, and device
state.
"""
import json
import os
import pytest
from unittest.mock import patch

import infrastructure.persistence.database as db_mod
from infrastructure.persistence.database import init_db
from infrastructure.sync.sync_writer import _TABLE_NAME_MAP


@pytest.fixture
def conn(db_file):
    """A single SQLite connection used for the whole test.

    We avoid stacking multiple connections on the same file because
    Python 3.14 + WAL can lock under concurrent writers. Instead, all
    repo / fixture / assertion work shares this one handle.
    """
    os.environ["DATABASE_PATH"] = db_file
    db_mod.DATABASE_PATH = db_file
    init_db()
    conn = db_mod.get_connection()
    conn.execute("DELETE FROM sync_outbox")
    conn.commit()
    yield conn
    conn.close()


def _seed_user(conn, user_id="dev-1", phone="13800138000", name="alice"):
    conn.execute(
        "INSERT OR IGNORE INTO users (id, phone, password_hash, salt, name) "
        "VALUES (?, ?, ?, ?, ?)",
        (user_id, phone, "h", "s", name),
    )
    conn.commit()


def _calls_for(mock, table_name):
    return [c for c in mock.call_args_list if c.args[0] == table_name]


# ---------- Table registration ----------

def test_devices_table_is_registered():
    assert _TABLE_NAME_MAP["devices"] == "devices"


def test_feedback_table_is_registered():
    assert _TABLE_NAME_MAP["feedback"] == "feedback"


def test_reply_history_table_is_registered():
    assert _TABLE_NAME_MAP["reply_history"] == "reply_history"


def test_optimization_metrics_table_is_registered():
    assert _TABLE_NAME_MAP["optimization_metrics"] == "optimization_metrics"


# ---------- feedback_repo ----------

def test_feedback_create_pushes_to_sync(conn):
    _seed_user(conn, user_id="dev-1")
    from domain.entities.feedback import Feedback
    from infrastructure.persistence.feedback_repo_sqlite import SqliteFeedbackRepository

    repo = SqliteFeedbackRepository()
    # reply_history_id must be None unless a reply_history row with that id
    # exists (FK constraint). Dataclass default is 0 → would violate FK.
    fb = Feedback(user_id="dev-1", action="accepted",
                  modified_text="", rating=5, comment="good",
                  reply_history_id=None)

    with patch("infrastructure.sync.sync_writer.upsert_to_supabase") as mock_upsert:
        repo.create(fb)

    calls = _calls_for(mock_upsert, "feedback")
    assert len(calls) == 1
    op, row_id, payload = calls[0].args[1], calls[0].args[2], calls[0].args[3]
    assert op == "INSERT"
    assert row_id == fb.id
    assert payload["user_id"] == "dev-1"
    assert payload["action"] == "accepted"
    assert payload["rating"] == 5
    assert payload["comment"] == "good"


def test_feedback_failure_enqueues_outbox(conn):
    _seed_user(conn, user_id="dev-1b")
    from domain.entities.feedback import Feedback
    from infrastructure.persistence.feedback_repo_sqlite import SqliteFeedbackRepository

    repo = SqliteFeedbackRepository()
    fb = Feedback(user_id="dev-1b", action="rejected", comment="bad",
                  reply_history_id=None)

    with patch("infrastructure.sync.sync_writer.upsert_to_supabase",
               side_effect=Exception("network down")):
        repo.create(fb)

    rows = conn.execute("SELECT * FROM sync_outbox WHERE table_name='feedback'").fetchall()
    assert len(rows) == 1
    assert rows[0]["last_error"] == "network down"
    payload = json.loads(rows[0]["payload"])
    assert payload["action"] == "rejected"


# ---------- history_repo ----------

def test_history_create_pushes_to_sync(conn):
    _seed_user(conn, user_id="dev-2")
    from domain.entities.reply_history import ReplyHistory
    from infrastructure.persistence.history_repo_sqlite import SqliteHistoryRepository

    repo = SqliteHistoryRepository()
    entry = ReplyHistory(user_id="dev-2", original_message="hi",
                         reply_content="hello", source="ai", model_used="gpt-4",
                         confidence=0.9, response_time_ms=120,
                         platform="android", customer_name="张三", house_name="默认")

    with patch("infrastructure.sync.sync_writer.upsert_to_supabase") as mock_upsert:
        repo.create(entry)

    calls = _calls_for(mock_upsert, "reply_history")
    assert len(calls) == 1
    op, payload = calls[0].args[1], calls[0].args[3]
    assert op == "INSERT"
    assert payload["user_id"] == "dev-2"
    assert payload["model_used"] == "gpt-4"
    assert payload["response_time_ms"] == 120
    assert payload["customer_name"] == "张三"


def test_history_create_failure_enqueues_outbox(conn):
    _seed_user(conn, user_id="dev-2b")
    from domain.entities.reply_history import ReplyHistory
    from infrastructure.persistence.history_repo_sqlite import SqliteHistoryRepository

    repo = SqliteHistoryRepository()
    entry = ReplyHistory(user_id="dev-2b", original_message="x", reply_content="y")

    with patch("infrastructure.sync.sync_writer.upsert_to_supabase",
               side_effect=Exception("timeout")):
        repo.create(entry)

    rows = conn.execute("SELECT * FROM sync_outbox WHERE table_name='reply_history'").fetchall()
    assert len(rows) == 1
    assert json.loads(rows[0]["payload"])["original_message"] == "x"


# ---------- metrics_repo ----------

def test_metrics_increment_pushes_to_sync(conn):
    _seed_user(conn, user_id="dev-3")
    from infrastructure.persistence.metrics_repo_sqlite import SqliteMetricsRepository

    repo = SqliteMetricsRepository()
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase") as mock_upsert:
        repo.increment_metric("dev-3", "generated")
        repo.increment_metric("dev-3", "accepted")
        repo.increment_metric("dev-3", "rejected")

    calls = _calls_for(mock_upsert, "optimization_metrics")
    # 3 increment_metric calls, all targeting the same (user, date) row.
    # SyncWriter push fires once per call with the latest row state.
    assert len(calls) == 3
    for c in calls:
        assert c.args[1] == "UPDATE"
        assert c.args[3]["user_id"] == "dev-3"


def test_metrics_unknown_action_is_noop(conn):
    """Unknown action should not push anything."""
    _seed_user(conn, user_id="dev-3b")
    from infrastructure.persistence.metrics_repo_sqlite import SqliteMetricsRepository

    repo = SqliteMetricsRepository()
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase") as mock_upsert:
        repo.increment_metric("dev-3b", "invalid_action")

    calls = _calls_for(mock_upsert, "optimization_metrics")
    assert calls == []


def test_metrics_failure_enqueues_outbox(conn):
    _seed_user(conn, user_id="dev-3c")
    from infrastructure.persistence.metrics_repo_sqlite import SqliteMetricsRepository

    repo = SqliteMetricsRepository()
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase",
               side_effect=Exception("supabase down")):
        repo.increment_metric("dev-3c", "generated")

    rows = conn.execute(
        "SELECT * FROM sync_outbox WHERE table_name='optimization_metrics'"
    ).fetchall()
    assert len(rows) == 1
    assert json.loads(rows[0]["payload"])["user_id"] == "dev-3c"


# ---------- device_repo ----------

def test_device_create_pushes_to_sync_excluding_token(conn):
    """Device token is sensitive auth material — must NOT leave local DB."""
    from domain.entities.device import Device
    from infrastructure.persistence.device_repo_sqlite import SqliteDeviceRepository

    repo = SqliteDeviceRepository()
    device = Device(id="dev-007", token="secret-token-xyz", name="phone-1",
                    platform="android", app_version="1.0.0")

    with patch("infrastructure.sync.sync_writer.upsert_to_supabase") as mock_upsert:
        repo.create(device)

    calls = _calls_for(mock_upsert, "devices")
    assert len(calls) == 1
    payload = calls[0].args[3]
    assert payload["id"] == "dev-007"
    assert payload["name"] == "phone-1"
    assert payload["platform"] == "android"
    assert "token" not in payload, "device token leaked to sync payload"


def test_device_heartbeat_pushes_to_sync(conn):
    from infrastructure.persistence.device_repo_sqlite import SqliteDeviceRepository

    repo = SqliteDeviceRepository()
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase") as mock_upsert:
        repo.update_heartbeat("dev-007")

    calls = _calls_for(mock_upsert, "devices")
    assert len(calls) == 1
    op, payload = calls[0].args[1], calls[0].args[3]
    assert op == "UPDATE"
    assert payload["id"] == "dev-007"


def test_device_create_failure_enqueues_outbox(conn):
    from domain.entities.device import Device
    from infrastructure.persistence.device_repo_sqlite import SqliteDeviceRepository

    repo = SqliteDeviceRepository()
    device = Device(id="dev-008", token="tkn", name="d8", platform="ios", app_version="2.0")

    with patch("infrastructure.sync.sync_writer.upsert_to_supabase",
               side_effect=Exception("boom")):
        repo.create(device)

    rows = conn.execute("SELECT * FROM sync_outbox WHERE table_name='devices'").fetchall()
    assert len(rows) == 1
    stored = json.loads(rows[0]["payload"])
    assert stored["id"] == "dev-008"
    assert "token" not in stored, "device token leaked to outbox"
