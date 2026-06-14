"""Tests for /api/auth/user/{login,register} supporting phone OR email.

Both endpoints accept either field. The server auto-detects the identifier
type by presence of '@' (for identifier-based lookup) or stores whichever
is provided. A user can register and log in using either phone or email.
"""
import os
import pytest
from unittest.mock import patch

JWT_SECRET = "test-secret-key-for-unified-auth"
ADMIN_PHONE = "13800138000"
ADMIN_PASSWORD = "testadmin123"


@pytest.fixture
def auth_client(db_file):
    os.environ["DATABASE_PATH"] = db_file
    os.environ["JWT_SECRET"] = JWT_SECRET
    os.environ["ADMIN_PHONE"] = ADMIN_PHONE
    os.environ["ADMIN_PASSWORD"] = ADMIN_PASSWORD

    import app as app_module
    app_module._db_initialized = False
    app_module._admin_table_initialized = False
    app_module._admin_tokens = {}
    app_module._blacklist_initialized = False
    app_module._audit_log_initialized = False
    app_module._rate_limit_store = {}
    app_module.init_db()
    app_module.app.config["TESTING"] = True
    with app_module.app.test_client() as c:
        yield c


# ========== register 端点 ==========

def test_register_with_email_succeeds(auth_client):
    resp = auth_client.post("/api/auth/user/register", json={
        "email": "alice@example.com",
        "password": "secret123",
        "name": "Alice",
    })
    assert resp.status_code == 201
    data = resp.get_json()
    assert data["email"] == "alice@example.com"
    assert data["user_id"]
    assert data["token"]
    assert data["expires_in"] == 30 * 86400


def test_register_with_phone_succeeds(auth_client):
    resp = auth_client.post("/api/auth/user/register", json={
        "phone": "13800138000",
        "password": "secret123",
        "name": "Phone User",
    })
    assert resp.status_code == 201
    data = resp.get_json()
    assert data["phone"] == "13800138000"
    assert data["user_id"]
    assert data["token"]


def test_register_with_both_email_and_phone_succeeds(auth_client):
    resp = auth_client.post("/api/auth/user/register", json={
        "email": "both@example.com",
        "phone": "13900139000",
        "password": "secret123",
    })
    assert resp.status_code == 201
    data = resp.get_json()
    assert data["email"] == "both@example.com"
    assert data["phone"] == "13900139000"


def test_register_without_password_rejected(auth_client):
    resp = auth_client.post("/api/auth/user/register", json={
        "email": "x@example.com",
    })
    assert resp.status_code == 400


def test_register_without_phone_and_email_rejected(auth_client):
    resp = auth_client.post("/api/auth/user/register", json={
        "password": "secret123",
    })
    assert resp.status_code == 400


def test_register_with_invalid_email_rejected(auth_client):
    resp = auth_client.post("/api/auth/user/register", json={
        "email": "not-an-email",
        "password": "secret123",
    })
    assert resp.status_code == 400


def test_register_duplicate_email_rejected(auth_client):
    auth_client.post("/api/auth/user/register", json={
        "email": "dup@example.com", "password": "secret123",
    })
    resp = auth_client.post("/api/auth/user/register", json={
        "email": "dup@example.com", "password": "secret456",
    })
    assert resp.status_code == 409


def test_register_short_password_rejected(auth_client):
    resp = auth_client.post("/api/auth/user/register", json={
        "email": "x@example.com", "password": "123",
    })
    assert resp.status_code == 400


# ========== login 端点 ==========

def test_login_with_email_succeeds(auth_client):
    auth_client.post("/api/auth/user/register", json={
        "email": "login@example.com", "password": "secret123", "name": "L",
    })
    resp = auth_client.post("/api/auth/user/login", json={
        "email": "login@example.com",
        "password": "secret123",
    })
    assert resp.status_code == 200
    data = resp.get_json()
    assert data["email"] == "login@example.com"
    assert data["token"]


def test_login_with_phone_succeeds(auth_client):
    auth_client.post("/api/auth/user/register", json={
        "phone": "13800138001", "password": "secret123",
    })
    resp = auth_client.post("/api/auth/user/login", json={
        "phone": "13800138001",
        "password": "secret123",
    })
    assert resp.status_code == 200
    data = resp.get_json()
    assert data["phone"] == "13800138001"


def test_login_with_identifier_field_succeeds(auth_client):
    auth_client.post("/api/auth/user/register", json={
        "email": "ident@example.com", "password": "secret123",
    })
    resp = auth_client.post("/api/auth/user/login", json={
        "identifier": "ident@example.com",
        "password": "secret123",
    })
    assert resp.status_code == 200


def test_login_email_is_case_insensitive(auth_client):
    auth_client.post("/api/auth/user/register", json={
        "email": "case@example.com", "password": "secret123",
    })
    resp = auth_client.post("/api/auth/user/login", json={
        "email": "CASE@Example.COM",
        "password": "secret123",
    })
    assert resp.status_code == 200


def test_login_wrong_password_rejected(auth_client):
    auth_client.post("/api/auth/user/register", json={
        "email": "wrong@example.com", "password": "secret123",
    })
    resp = auth_client.post("/api/auth/user/login", json={
        "email": "wrong@example.com",
        "password": "wrongpass",
    })
    assert resp.status_code == 401


def test_login_nonexistent_email_rejected(auth_client):
    resp = auth_client.post("/api/auth/user/login", json={
        "email": "nonexistent@example.com",
        "password": "secret123",
    })
    assert resp.status_code == 401


def test_login_missing_password_rejected(auth_client):
    resp = auth_client.post("/api/auth/user/login", json={
        "email": "x@example.com",
    })
    assert resp.status_code == 400


def test_login_missing_identifier_rejected(auth_client):
    resp = auth_client.post("/api/auth/user/login", json={
        "password": "secret123",
    })
    assert resp.status_code == 400


def test_login_can_use_email_after_phone_registration(auth_client):
    """Cross-identifier login: a user with only phone should not be reachable by email."""
    auth_client.post("/api/auth/user/register", json={
        "phone": "13800138002", "password": "secret123",
    })
    resp = auth_client.post("/api/auth/user/login", json={
        "email": "13800138002@example.com",
        "password": "secret123",
    })
    assert resp.status_code == 401
