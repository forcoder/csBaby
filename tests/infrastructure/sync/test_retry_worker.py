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


def test_tick_processes_multiple_rows_in_batch(db):
    for i in range(3):
        db.execute(
            "INSERT INTO sync_outbox (table_name, op, row_id, payload) VALUES (?, ?, ?, ?)",
            ("keyword_rules", "INSERT", i, f'{{"id":{i}}}'),
        )
    db.commit()
    worker = RetryWorker(db, batch_size=10)
    with patch("infrastructure.sync.retry_worker.upsert_to_supabase", return_value=None):
        processed = worker.tick()
    assert processed == 3
    assert db.execute("SELECT COUNT(*) AS c FROM sync_outbox").fetchone()["c"] == 0