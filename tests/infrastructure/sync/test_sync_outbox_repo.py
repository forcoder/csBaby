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
