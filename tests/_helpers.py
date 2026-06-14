"""Shared test helpers for fixture composition."""
from infrastructure.persistence.database import get_connection


def seed_user_for_device(device_id: str) -> None:
    """Mirror an anonymous device UUID into the users table.

    /api/auth/register (anonymous path) issues a JWT whose user_id claim is
    the device's UUID. Business tables (keyword_rules, model_configs,
    blacklist, feedback, optimization_metrics, tenant_*, ...) all carry
    FOREIGN KEY (user_id) REFERENCES users(id), so any write through them
    trips the constraint unless a users row exists for that id.

    Tests that need to use the anonymous register flow as a stand-in for a
    real authenticated user should call this helper right after
    /api/auth/register returns.
    """
    if not device_id:
        return
    db = get_connection()
    try:
        db.execute(
            "INSERT OR IGNORE INTO users (id, phone, password_hash, salt, name) "
            "VALUES (?, ?, ?, ?, ?)",
            (device_id, f"test-{device_id}", "h", "s", "test-user"),
        )
        db.commit()
    finally:
        db.close()
