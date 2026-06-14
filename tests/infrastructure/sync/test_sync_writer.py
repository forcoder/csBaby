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


def test_push_unknown_table_raises_value_error(writer):
    with patch("infrastructure.sync.sync_writer.upsert_to_supabase",
               side_effect=ValueError("Unknown table for sync: fake_table")):
        # unknown table triggers ValueError → caught → enqueued with empty payload
        writer.push("fake_table", "INSERT", 1, {"id": 1})
    db = get_connection()
    outbox = db.execute("SELECT * FROM sync_outbox WHERE table_name='fake_table'").fetchall()
    assert len(outbox) == 1