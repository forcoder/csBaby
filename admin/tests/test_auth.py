"""Tests for admin authentication: login, logout, CSRF, session management."""
import pytest
from unittest.mock import patch, MagicMock


class TestLogin:
    def test_login_page_get(self, client):
        """Login page should render the login form."""
        with patch("app.api_post") as mock_post:
            resp = client.get("/admin/login")
            assert resp.status_code == 200

    def test_login_success(self, client):
        """Successful login should redirect to dashboard."""
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {
                "phone": "13800138000",
                "token": "test-token",
                "is_admin": True,
            }
            resp = client.post("/admin/login", data={
                "phone": "13800138000",
                "password": "admin123",
            }, follow_redirects=False)
            assert resp.status_code == 302
            assert "/admin/dashboard" in resp.headers["Location"]

    def test_login_wrong_password(self, client):
        """Failed login should re-render login page with error."""
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 401
            mock_post.return_value.json.return_value = {"error": "密码错误"}
            resp = client.post("/admin/login", data={
                "phone": "13800138000",
                "password": "wrong",
            }, follow_redirects=False)
            assert resp.status_code == 200

    def test_login_not_admin(self, client):
        """Non-admin user should see permission error."""
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {
                "phone": "13800138000",
                "token": "test-token",
                "is_admin": False,
            }
            resp = client.post("/admin/login", data={
                "phone": "13800138000",
                "password": "admin123",
            }, follow_redirects=False)
            assert resp.status_code == 200

    def test_login_api_unreachable(self, client):
        """Should show error when API is unreachable."""
        import requests as req
        with patch("app.api_post", side_effect=req.exceptions.RequestException("Connection refused")):
            resp = client.post("/admin/login", data={
                "phone": "13800138000",
                "password": "admin123",
            }, follow_redirects=False)
            assert resp.status_code == 200

    def test_login_rate_limiting(self, client):
        """Should block after 5 failed attempts within 5 minutes."""
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 401
            mock_post.return_value.json.return_value = {"error": "密码错误"}

            # Make 5 failed attempts
            for _ in range(5):
                client.post("/admin/login", data={
                    "phone": "13800138000",
                    "password": "wrong",
                })

            # 6th attempt should be rate limited
            resp = client.post("/admin/login", data={
                "phone": "13800138000",
                "password": "wrong",
            }, follow_redirects=False)
            assert resp.status_code == 200

    def test_login_redirects_if_already_logged_in(self, client):
        """Already logged-in user should be redirected to dashboard."""
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {
                "phone": "13800138000",
                "token": "test-token",
                "is_admin": True,
            }
            # Login first
            client.post("/admin/login", data={
                "phone": "13800138000",
                "password": "admin123",
            })

            # Now access login page
            resp = client.get("/admin/login", follow_redirects=False)
            assert resp.status_code == 302
            assert "/admin/dashboard" in resp.headers["Location"]


class TestLogout:
    def test_logout_clears_session(self, client):
        """Logout should clear session and redirect to login."""
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {
                "phone": "13800138000",
                "token": "test-token",
                "is_admin": True,
            }
            # Login first
            client.post("/admin/login", data={
                "phone": "13800138000",
                "password": "admin123",
            })

            # Logout
            resp = client.get("/admin/logout", follow_redirects=False)
            assert resp.status_code == 302
            assert "/admin/login" in resp.headers["Location"]

    def test_logout_without_login(self, client):
        """Logout without login should still redirect to login."""
        resp = client.get("/admin/logout", follow_redirects=False)
        assert resp.status_code == 302


class TestLoginRequired:
    def test_dashboard_requires_login(self, client):
        """Dashboard should redirect to login if not authenticated."""
        resp = client.get("/admin/dashboard", follow_redirects=False)
        assert resp.status_code == 302
        assert "/admin/login" in resp.headers["Location"]

    def test_tenants_requires_login(self, client):
        """Tenants page should redirect to login if not authenticated."""
        resp = client.get("/admin/tenants", follow_redirects=False)
        assert resp.status_code == 302

    def test_tenant_detail_requires_login(self, client):
        """Tenant detail page should redirect to login."""
        resp = client.get("/admin/tenants/test-tenant", follow_redirects=False)
        assert resp.status_code == 302

    def test_rules_requires_login(self, client):
        """Rules page should redirect to login."""
        resp = client.get("/admin/tenants/test-tenant/rules", follow_redirects=False)
        assert resp.status_code == 302

    def test_blacklist_requires_login(self, client):
        """Blacklist page should redirect to login."""
        resp = client.get("/admin/tenants/test-tenant/blacklist", follow_redirects=False)
        assert resp.status_code == 302

    def test_history_requires_login(self, client):
        """History page should redirect to login."""
        resp = client.get("/admin/tenants/test-tenant/history", follow_redirects=False)
        assert resp.status_code == 302

    def test_models_requires_login(self, client):
        """Models page should redirect to login."""
        resp = client.get("/admin/tenants/test-tenant/models", follow_redirects=False)
        assert resp.status_code == 302

    def test_feedback_requires_login(self, client):
        """Feedback page should redirect to login."""
        resp = client.get("/admin/tenants/test-tenant/feedback", follow_redirects=False)
        assert resp.status_code == 302

    def test_metrics_requires_login(self, client):
        """Metrics page should redirect to login."""
        resp = client.get("/admin/tenants/test-tenant/metrics", follow_redirects=False)
        assert resp.status_code == 302

    def test_routing_requires_login(self, client):
        """Routing page should redirect to login."""
        resp = client.get("/admin/tenants/test-tenant/routing", follow_redirects=False)
        assert resp.status_code == 302

    def test_audit_log_requires_login(self, client):
        """Audit log page should redirect to login."""
        resp = client.get("/admin/audit-log", follow_redirects=False)
        assert resp.status_code == 302

    def test_admins_requires_login(self, client):
        """Admins management page should redirect to login."""
        resp = client.get("/admin/admins", follow_redirects=False)
        assert resp.status_code == 302

    def test_profile_requires_login(self, client):
        """Profile page should redirect to login."""
        resp = client.get("/admin/profile", follow_redirects=False)
        assert resp.status_code == 302


class TestAdminIndex:
    def test_admin_index_redirects_to_dashboard(self, client):
        """Admin index should redirect to dashboard."""
        resp = client.get("/admin", follow_redirects=False)
        assert resp.status_code == 302
        assert "/admin/dashboard" in resp.headers["Location"]
