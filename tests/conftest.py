import os
import sys
import pytest
import tempfile

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


@pytest.fixture
def db_file():
    fd, path = tempfile.mkstemp(suffix=".db")
    os.close(fd)
    yield path
    if os.path.exists(path):
        os.unlink(path)


@pytest.fixture
def app_module(db_file):
    """Import app module with test database path (no reload to preserve coverage)."""
    os.environ["DATABASE_PATH"] = db_file
    os.environ["JWT_SECRET"] = "test-secret-key"
    os.environ["ADMIN_PHONE"] = "13800138000"
    os.environ["ADMIN_PASSWORD"] = "testadmin123"

    import app as app_module
    # Reset DB state without reload
    app_module._db_initialized = False
    app_module._admin_table_initialized = False
    app_module._admin_tokens = {}
    app_module._blacklist_initialized = False
    app_module._audit_log_initialized = False
    app_module._rate_limit_store = {}
    app_module.init_db()
    return app_module


@pytest.fixture
def client(app_module):
    app_module.app.config["TESTING"] = True
    with app_module.app.test_client() as c:
        yield c


@pytest.fixture
def auth_headers(client):
    resp = client.post("/api/auth/register", json={
        "name": "test-device",
        "platform": "android",
        "app_version": "1.0.0"
    })
    data = resp.get_json()
    token = data["token"]
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
