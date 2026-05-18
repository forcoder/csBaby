"""Admin test fixtures."""
import os
import sys
import pytest

# Ensure the admin directory is importable
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Set required env vars before importing app
os.environ.setdefault("API_BASE_URL", "http://localhost:5000")
os.environ.setdefault("SESSION_SECRET", "test-secret-key-for-unit-tests-only")


@pytest.fixture
def app():
    """Create admin app in testing mode."""
    from app import app as admin_app
    admin_app.config["TESTING"] = True
    admin_app.config["WTF_CSRF_ENABLED"] = False
    admin_app.config["SERVER_NAME"] = "localhost"
    return admin_app


@pytest.fixture(autouse=True)
def _reset_rate_limiter():
    """Reset login rate limiter between tests to prevent state pollution."""
    from app import _login_attempts, _login_lock
    with _login_lock:
        _login_attempts.clear()
    yield
    with _login_lock:
        _login_attempts.clear()


@pytest.fixture
def client(app):
    """Flask test client."""
    return app.test_client()


def _mock_admin_login(client, mock_requests, token="test-admin-token", phone=None):
    """Helper: mock the admin login API call and perform login."""
    mock_requests.post.return_value.status_code = 200
    mock_requests.post.return_value.json.return_value = {
        "phone": phone,
        "token": token,
        "is_admin": True,
    }
    if phone is None:
        phone = os.environ.get("TEST_ADMIN_PHONE", "13800138000")
    return client.post("/admin/login", data={
        "phone": phone,
        "password": os.environ.get("TEST_ADMIN_PASSWORD", "testpass123"),
    }, follow_redirects=False)


def _get_auth_headers(client):
    """Not used for admin (session-based auth), but kept for consistency."""
    return {}
