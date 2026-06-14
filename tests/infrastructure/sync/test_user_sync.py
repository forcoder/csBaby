"""Security test: user sync payload must NOT contain password_hash or salt.

Background: user_repo_sqlite.SqliteUserRepository.create() previously pushed
the full SQLite row (including password_hash and salt) to Supabase. This is
a critical security regression — password material should never leave the
local database. This test pins the safe behavior.
"""
import json
import pytest
from unittest.mock import patch

from infrastructure.persistence.database import get_connection, init_db
from infrastructure.sync.sync_writer import _TABLE_NAME_MAP


@pytest.fixture
def db():
    init_db()
    db = get_connection()
    db.execute("DELETE FROM sync_outbox")
    db.commit()
    yield db
    db.close()


def _captured_payload(mock_upsert, table_name):
    """Return the dict passed to upsert_to_supabase for the given table."""
    for call in mock_upsert.call_args_list:
        args, kwargs = call
        if args and args[0] == table_name:
            return args[3]
    return None


def test_users_table_is_registered_in_sync_map():
    assert "users" in _TABLE_NAME_MAP
    assert _TABLE_NAME_MAP["users"] == "users"


def test_user_create_payload_excludes_password_hash_and_salt(db):
    """After SqliteUserRepository.create(), the captured sync payload must not
    contain password_hash or salt. These fields are sensitive credentials and
    must stay in local SQLite only."""
    from infrastructure.persistence.user_repo_sqlite import SqliteUserRepository

    with patch("infrastructure.sync.sync_writer.upsert_to_supabase") as mock_upsert:
        SqliteUserRepository().create(
            user_id="user-001",
            phone="13800138000",
            password_hash="$2b$12$abcdef0123456789",
            salt="salt-12345",
            name="alice",
        )

    payload = _captured_payload(mock_upsert, "users")
    assert payload is not None, "SyncWriter.push was not called for users"
    assert "password_hash" not in payload, "password_hash leaked to Supabase sync"
    assert "salt" not in payload, "salt leaked to Supabase sync"
    # Identity fields must still be present for analytics
    assert payload["id"] == "user-001"
    assert payload["phone"] == "13800138000"
    assert payload["name"] == "alice"


def test_user_update_payload_excludes_password_hash_and_salt(db):
    """SqliteUserRepository.update() must not leak password fields either."""
    from infrastructure.persistence.user_repo_sqlite import SqliteUserRepository

    repo = SqliteUserRepository()
    repo.create(
        user_id="user-002",
        phone="13800138001",
        password_hash="$2b$12$xxx",
        salt="salt-xxx",
        name="bob",
    )

    with patch("infrastructure.sync.sync_writer.upsert_to_supabase") as mock_upsert:
        repo.update("user-002", "bob-updated")

    update_calls = [c for c in mock_upsert.call_args_list if c.args[0] == "users"]
    assert len(update_calls) >= 1
    update_payload = update_calls[-1].args[3]
    assert "password_hash" not in update_payload
    assert "salt" not in update_payload
    assert update_payload["name"] == "bob-updated"


def test_user_create_failure_enqueues_outbox_without_secrets(db):
    """Even when Supabase is down, the outbox must not retain password material."""
    from infrastructure.persistence.user_repo_sqlite import SqliteUserRepository

    with patch("infrastructure.sync.sync_writer.upsert_to_supabase",
               side_effect=Exception("supabase unreachable")):
        SqliteUserRepository().create(
            user_id="user-003",
            phone="13800138002",
            password_hash="$2b$12$zzz",
            salt="salt-zzz",
            name="carol",
        )

    outbox_rows = db.execute(
        "SELECT * FROM sync_outbox WHERE table_name='users'"
    ).fetchall()
    assert len(outbox_rows) == 1
    stored_payload = json.loads(outbox_rows[0]["payload"])
    assert "password_hash" not in stored_payload
    assert "salt" not in stored_payload
