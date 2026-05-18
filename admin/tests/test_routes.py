"""Tests for admin routes with mocked backend API calls."""
import pytest
from unittest.mock import patch, MagicMock


def _login_admin(client):
    """Helper: perform admin login with mocked API."""
    import os
    phone = os.environ.get("TEST_ADMIN_PHONE", "13800138000")
    token = os.environ.get("TEST_ADMIN_TOKEN", "test-admin-token")
    with patch("app.api_post") as mock_post:
        mock_post.return_value.status_code = 200
        mock_post.return_value.json.return_value = {
            "phone": phone,
            "token": token,
            "is_admin": True,
        }
        client.post("/admin/login", data={
            "phone": phone,
            "password": os.environ.get("TEST_ADMIN_PASSWORD", "admin123"),
        })
    return token


class TestDashboard:
    def test_dashboard_renders(self, client):
        """Dashboard should render with stats and recent tenants."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            # The dashboard calls api_get twice: stats then recent-tenants
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.side_effect = [
                {"total_tenants": 10, "active_tenants": 8, "total_rules": 50},
                [{"tenant_id": "t1", "name": "租户A", "is_active": 1}],
            ]
            resp = client.get("/admin/dashboard")
            assert resp.status_code == 200

    def test_dashboard_api_failure_shows_empty(self, client):
        """Dashboard should still render when API is unreachable."""
        _login_admin(client)
        with patch("app.api_get", side_effect=Exception("Connection refused")):
            resp = client.get("/admin/dashboard")
            assert resp.status_code == 200


class TestTenants:
    def test_tenants_list(self, client):
        """Tenants page should render with tenant list."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {
                "items": [
                    {"tenant_id": "t1", "name": "租户A", "status": 1},
                    {"tenant_id": "t2", "name": "租户B", "status": 0},
                ],
                "total": 2,
                "page": 1,
                "page_size": 20,
            }
            resp = client.get("/admin/tenants")
            assert resp.status_code == 200

    def test_tenants_search(self, client):
        """Tenants search should pass query to API."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {
                "items": [], "total": 0, "page": 1, "page_size": 20,
            }
            resp = client.get("/admin/tenants?q=searchterm")
            assert resp.status_code == 200

    def test_tenants_pagination(self, client):
        """Tenants pagination should clamp page_size."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {
                "items": [], "total": 0, "page": 2, "page_size": 20,
            }
            resp = client.get("/admin/tenants?page=2&page_size=50")
            assert resp.status_code == 200

    def test_tenants_page_size_clamped_to_100(self, client):
        """Page size > 100 should be clamped."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {
                "items": [], "total": 0, "page": 1, "page_size": 100,
            }
            resp = client.get("/admin/tenants?page_size=500")
            assert resp.status_code == 200

    def test_tenants_page_size_minimum_1(self, client):
        """Page size < 1 should be clamped to 1."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {
                "items": [], "total": 0, "page": 1, "page_size": 1,
            }
            resp = client.get("/admin/tenants?page_size=0")
            assert resp.status_code == 200

    def test_tenants_negative_page_defaults_to_1(self, client):
        """Negative page number should be handled gracefully."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {
                "items": [], "total": 0, "page": 1, "page_size": 20,
            }
            resp = client.get("/admin/tenants?page=-1")
            assert resp.status_code == 200

    def test_tenants_very_large_page(self, client):
        """Very large page number should not cause errors."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {
                "items": [], "total": 0, "page": 9999, "page_size": 20,
            }
            resp = client.get("/admin/tenants?page=9999")
            assert resp.status_code == 200


class TestTenantDetail:
    def test_tenant_detail_renders(self, client):
        """Tenant detail page should render."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {
                "tenant_id": "test-tenant",
                "name": "测试租户",
                "status": 1,
            }
            resp = client.get("/admin/tenants/test-tenant")
            assert resp.status_code == 200

    def test_tenant_detail_not_found(self, client):
        """Non-existent tenant should still render page."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 404
            mock_get.return_value.json.return_value = {"error": "Not found"}
            resp = client.get("/admin/tenants/nonexistent")
            assert resp.status_code == 200


class TestTenantRules:
    def test_rules_page_renders(self, client):
        """Rules page should render with rule list."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = []
            resp = client.get("/admin/tenants/test-tenant/rules")
            assert resp.status_code == 200

    def test_add_rule(self, client):
        """Adding a rule should call API and redirect."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"id": 1}
            resp = client.post("/admin/tenants/test-tenant/rules/add", data={
                "keyword": "你好",
                "match_type": "CONTAINS",
                "reply_template": "您好！请问有什么可以帮您？",
                "category": "greeting",
                "target_type": "ALL",
                "target_names": "[]",
                "priority": "10",
                "enabled": "on",
            }, follow_redirects=False)
            assert resp.status_code == 302

    def test_add_rule_failure(self, client):
        """Failed rule add should flash error."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 400
            mock_post.return_value.json.return_value = {"error": "关键词不能为空"}
            resp = client.post("/admin/tenants/test-tenant/rules/add", data={
                "keyword": "",
                "reply_template": "test",
            }, follow_redirects=False)
            assert resp.status_code == 302

    def test_edit_rule(self, client):
        """Editing a rule should call API and redirect."""
        _login_admin(client)
        with patch("app.api_put") as mock_put:
            mock_put.return_value.status_code = 200
            mock_put.return_value.json.return_value = {"id": 1}
            resp = client.post("/admin/tenants/test-tenant/rules/1/edit", data={
                "keyword": "updated",
                "match_type": "CONTAINS",
                "reply_template": "updated reply",
                "category": "",
                "target_type": "ALL",
                "target_names": "[]",
                "priority": "5",
                "enabled": "on",
            }, follow_redirects=False)
            assert resp.status_code == 302

    def test_edit_rule_not_found(self, client):
        """Editing non-existent rule should flash error."""
        _login_admin(client)
        with patch("app.api_put") as mock_put:
            mock_put.return_value.status_code = 404
            mock_put.return_value.json.return_value = {"error": "规则不存在"}
            resp = client.post("/admin/tenants/test-tenant/rules/99999/edit", data={
                "keyword": "test",
                "reply_template": "test",
            }, follow_redirects=False)
            assert resp.status_code == 302

    def test_delete_rule(self, client):
        """Deleting a rule should call API and redirect."""
        _login_admin(client)
        with patch("app.api_delete") as mock_delete:
            mock_delete.return_value.status_code = 200
            mock_delete.return_value.json.return_value = {"status": "deleted"}
            resp = client.post("/admin/tenants/test-tenant/rules/1/delete", follow_redirects=False)
            assert resp.status_code == 302

    def test_delete_rule_not_found(self, client):
        """Deleting non-existent rule should flash error."""
        _login_admin(client)
        with patch("app.api_delete") as mock_delete:
            mock_delete.return_value.status_code = 404
            mock_delete.return_value.json.return_value = {"error": "规则不存在"}
            resp = client.post("/admin/tenants/test-tenant/rules/99999/delete", follow_redirects=False)
            assert resp.status_code == 302


class TestBatchImport:
    def test_batch_import_json_file(self, client):
        """Batch import via JSON file upload."""
        import io
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"imported": 2, "total": 2}
            data = {
                "import_file": (io.BytesIO(b'[{"keyword":"k1","reply_template":"r1"},{"keyword":"k2","reply_template":"r2"}]'), "rules.json"),
                "import_mode": "override",
            }
            resp = client.post(
                "/admin/tenants/test-tenant/rules/batch",
                data=data,
                content_type="multipart/form-data",
                follow_redirects=False,
            )
            assert resp.status_code == 302

    def test_batch_import_csv_file(self, client):
        """Batch import via CSV file upload."""
        import io
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"imported": 1, "total": 1}
            csv_content = "keyword,reply_template\n你好,您好"
            data = {
                "import_file": (io.BytesIO(csv_content.encode("utf-8")), "rules.csv"),
                "import_mode": "append",
            }
            resp = client.post(
                "/admin/tenants/test-tenant/rules/batch",
                data=data,
                content_type="multipart/form-data",
                follow_redirects=False,
            )
            assert resp.status_code == 302

    def test_batch_import_excel_file(self, client):
        """Batch import via Excel file upload."""
        import io
        import openpyxl
        from io import BytesIO

        wb = openpyxl.Workbook()
        ws = wb.active
        ws.append(["keyword", "reply_template"])
        ws.append(["你好", "您好"])
        buf = BytesIO()
        wb.save(buf)
        buf.seek(0)
        wb.close()

        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"imported": 1, "total": 1}
            data = {
                "import_file": (buf, "rules.xlsx"),
                "import_mode": "override",
            }
            resp = client.post(
                "/admin/tenants/test-tenant/rules/batch",
                data=data,
                content_type="multipart/form-data",
                follow_redirects=False,
            )
            assert resp.status_code == 302

    def test_batch_import_json_text(self, client):
        """Batch import via JSON text paste."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"imported": 1, "total": 1}
            resp = client.post("/admin/tenants/test-tenant/rules/batch", data={
                "import_data": '[{"keyword":"test","reply_template":"reply"}]',
                "import_mode": "append",
            }, follow_redirects=False)
            assert resp.status_code == 302

    def test_batch_import_no_file_no_data(self, client):
        """Should flash error when no file and no JSON text."""
        _login_admin(client)
        resp = client.post("/admin/tenants/test-tenant/rules/batch", data={
            "import_mode": "override",
        }, follow_redirects=False)
        assert resp.status_code == 302

    def test_batch_import_invalid_file_extension(self, client):
        """Should flash error for unsupported file types."""
        import io
        _login_admin(client)
        data = {
            "import_file": (io.BytesIO(b"test"), "rules.txt"),
            "import_mode": "override",
        }
        resp = client.post(
            "/admin/tenants/test-tenant/rules/batch",
            data=data,
            content_type="multipart/form-data",
            follow_redirects=False,
        )
        assert resp.status_code == 302

    def test_batch_import_empty_rules(self, client):
        """Should flash error when parsed rules are empty."""
        _login_admin(client)
        resp = client.post("/admin/tenants/test-tenant/rules/batch", data={
            "import_data": '[]',
            "import_mode": "override",
        }, follow_redirects=False)
        assert resp.status_code == 302

    def test_batch_import_invalid_json_text(self, client):
        """Should flash error for invalid JSON text."""
        _login_admin(client)
        resp = client.post("/admin/tenants/test-tenant/rules/batch", data={
            "import_data": "not valid json {{{",
            "import_mode": "override",
        }, follow_redirects=False)
        assert resp.status_code == 302

    def test_batch_import_append_mode(self, client):
        """Append mode should not delete existing rules."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"imported": 1, "total": 5}
            resp = client.post("/admin/tenants/test-tenant/rules/batch", data={
                "import_data": '[{"keyword":"new","reply_template":"new_reply"}]',
                "import_mode": "append",
            }, follow_redirects=False)
            assert resp.status_code == 302
            # Verify mode=append was sent to API
            call_args = mock_post.call_args
            assert call_args[0][1]["mode"] == "append"

    def test_batch_import_override_mode(self, client):
        """Override mode should be the default."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"imported": 1, "total": 1}
            resp = client.post("/admin/tenants/test-tenant/rules/batch", data={
                "import_data": '[{"keyword":"new","reply_template":"new_reply"}]',
            }, follow_redirects=False)
            assert resp.status_code == 302
            call_args = mock_post.call_args
            assert call_args[0][1]["mode"] == "override"

    def test_batch_import_api_failure(self, client):
        """API failure during batch import should flash error."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 500
            mock_post.return_value.json.return_value = {"error": "Internal server error"}
            resp = client.post("/admin/tenants/test-tenant/rules/batch", data={
                "import_data": '[{"keyword":"test","reply_template":"reply"}]',
                "import_mode": "override",
            }, follow_redirects=False)
            assert resp.status_code == 302

    def test_batch_import_network_error(self, client):
        """Network error during batch import should flash error."""
        import requests as req
        _login_admin(client)
        with patch("app.api_post", side_effect=req.exceptions.RequestException("Connection refused")):
            resp = client.post("/admin/tenants/test-tenant/rules/batch", data={
                "import_data": '[{"keyword":"test","reply_template":"reply"}]',
                "import_mode": "override",
            }, follow_redirects=False)
            assert resp.status_code == 302

    def test_batch_import_file_parse_error(self, client):
        """File parsing error should flash error."""
        import io
        _login_admin(client)
        data = {
            "import_file": (io.BytesIO(b"\x00\x01\x02\x03"), "rules.xlsx"),
            "import_mode": "override",
        }
        resp = client.post(
            "/admin/tenants/test-tenant/rules/batch",
            data=data,
            content_type="multipart/form-data",
            follow_redirects=False,
        )
        assert resp.status_code == 302


class TestBlacklist:
    def test_blacklist_page_renders(self, client):
        """Blacklist page should render."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = []
            resp = client.get("/admin/tenants/test-tenant/blacklist")
            assert resp.status_code == 200

    def test_add_blacklist(self, client):
        """Adding blacklist entry should call API and redirect."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"id": 1}
            resp = client.post("/admin/tenants/test-tenant/blacklist/add", data={
                "name": "黑名单用户",
                "phone": "13800138001",
                "reason": "恶意骚扰",
            }, follow_redirects=False)
            assert resp.status_code == 302

    def test_delete_blacklist(self, client):
        """Deleting blacklist entry should call API and redirect."""
        _login_admin(client)
        with patch("app.api_delete") as mock_delete:
            mock_delete.return_value.status_code = 200
            mock_delete.return_value.json.return_value = {"status": "deleted"}
            resp = client.post("/admin/tenants/test-tenant/blacklist/1/delete", follow_redirects=False)
            assert resp.status_code == 302

    def test_clear_blacklist(self, client):
        """Clearing all blacklist entries should call API and redirect."""
        _login_admin(client)
        with patch("app.api_delete") as mock_delete:
            mock_delete.return_value.status_code = 200
            mock_delete.return_value.json.return_value = {"status": "cleared"}
            resp = client.post("/admin/tenants/test-tenant/blacklist/clear", follow_redirects=False)
            assert resp.status_code == 302


class TestHistory:
    def test_history_page_renders(self, client):
        """History page should render."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {
                "items": [], "total": 0, "page": 1, "page_size": 20,
            }
            resp = client.get("/admin/tenants/test-tenant/history")
            assert resp.status_code == 200

    def test_history_with_date_filter(self, client):
        """History with date filter should pass params to API."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {
                "items": [], "total": 0, "page": 1, "page_size": 20,
            }
            resp = client.get("/admin/tenants/test-tenant/history?date_from=2026-01-01&date_to=2026-05-12")
            assert resp.status_code == 200


class TestModels:
    def test_models_page_renders(self, client):
        """Models page should render."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = []
            resp = client.get("/admin/tenants/test-tenant/models")
            assert resp.status_code == 200

    def test_add_model(self, client):
        """Adding a model config should call API and redirect."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"id": 1}
            resp = client.post("/admin/tenants/test-tenant/models/add", data={
                "name": "GPT-4",
                "model_type": "OPENAI",
                "model": "gpt-4o",
                "api_key": "sk-test-key",
                "temperature": "0.7",
                "max_tokens": "2000",
            }, follow_redirects=False)
            assert resp.status_code == 302

    def test_edit_model(self, client):
        """Editing a model config should call API and redirect."""
        _login_admin(client)
        with patch("app.api_put") as mock_put:
            mock_put.return_value.status_code = 200
            mock_put.return_value.json.return_value = {"id": 1}
            resp = client.post("/admin/tenants/test-tenant/models/1/edit", data={
                "name": "GPT-4 Updated",
                "model_type": "OPENAI",
                "model": "gpt-4o",
                "temperature": "0.5",
                "max_tokens": "4000",
            }, follow_redirects=False)
            assert resp.status_code == 302

    def test_delete_model(self, client):
        """Deleting a model config should call API and redirect."""
        _login_admin(client)
        with patch("app.api_delete") as mock_delete:
            mock_delete.return_value.status_code = 200
            mock_delete.return_value.json.return_value = {"status": "deleted"}
            resp = client.post("/admin/tenants/test-tenant/models/1/delete", follow_redirects=False)
            assert resp.status_code == 302


class TestFeedback:
    def test_feedback_page_renders(self, client):
        """Feedback page should render."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {
                "items": [], "total": 0, "page": 1, "page_size": 20,
            }
            resp = client.get("/admin/tenants/test-tenant/feedback")
            assert resp.status_code == 200


class TestMetrics:
    def test_metrics_page_renders(self, client):
        """Metrics page should render."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {}
            resp = client.get("/admin/tenants/test-tenant/metrics")
            assert resp.status_code == 200

    def test_metrics_days_clamped(self, client):
        """Days parameter should be clamped to 1-365."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {}
            resp = client.get("/admin/tenants/test-tenant/metrics?days=999")
            assert resp.status_code == 200


class TestAuditLog:
    def test_audit_log_renders(self, client):
        """Audit log page should render."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = {
                "items": [], "total": 0, "page": 1, "page_size": 20,
            }
            resp = client.get("/admin/audit-log")
            assert resp.status_code == 200


class TestAdmins:
    def test_admins_page_renders(self, client):
        """Admins management page should render."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = []
            resp = client.get("/admin/admins")
            assert resp.status_code == 200

    def test_add_admin(self, client):
        """Adding an admin should call API and redirect."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"id": 1}
            resp = client.post("/admin/admins/add", data={
                "phone": "13800138001",
                "password": "newpass123",
                "name": "新管理员",
            }, follow_redirects=False)
            assert resp.status_code == 302

    def test_edit_admin(self, client):
        """Editing an admin should call API and redirect."""
        _login_admin(client)
        with patch("app.api_put") as mock_put:
            mock_put.return_value.status_code = 200
            mock_put.return_value.json.return_value = {"id": 1}
            resp = client.post("/admin/admins/1/edit", data={
                "phone": "13800138001",
                "password": "",
                "name": "更新名称",
            }, follow_redirects=False)
            assert resp.status_code == 302

    def test_delete_admin(self, client):
        """Deleting an admin should call API and redirect."""
        _login_admin(client)
        with patch("app.api_delete") as mock_delete:
            mock_delete.return_value.status_code = 200
            mock_delete.return_value.json.return_value = {"status": "deleted"}
            resp = client.post("/admin/admins/1/delete", follow_redirects=False)
            assert resp.status_code == 302

    def test_delete_admin_redirects_on_error(self, client):
        """When backend returns error, should redirect with error flash."""
        _login_admin(client)
        with patch("app.api_delete") as mock_delete:
            mock_delete.return_value.status_code = 400
            mock_delete.return_value.json.return_value = {"error": "Cannot delete yourself"}
            resp = client.post("/admin/admins/1/delete", follow_redirects=False)
            assert resp.status_code == 302


class TestProfile:
    def test_profile_page_renders(self, client):
        """Profile page should render."""
        _login_admin(client)
        resp = client.get("/admin/profile")
        assert resp.status_code == 200

    def test_change_password(self, client):
        """Changing password should call API and re-render with success message."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"status": "ok"}
            resp = client.post("/admin/profile", data={
                "old_password": "oldpass",
                "new_password": "newpass123",
                "confirm_password": "newpass123",
            }, follow_redirects=False)
            # Profile renders template with success message (200), not redirect
            assert resp.status_code == 200

    def test_change_password_mismatch(self, client):
        """Password mismatch should re-render profile page."""
        _login_admin(client)
        resp = client.post("/admin/profile", data={
            "old_password": "oldpass",
            "new_password": "newpass123",
            "confirm_password": "different",
        }, follow_redirects=False)
        assert resp.status_code == 200


class TestDefaultModel:
    def test_default_model_page_renders(self, client):
        """Default model page should render."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.return_value = None
            resp = client.get("/admin/default-model")
            assert resp.status_code == 200

    def test_save_default_model(self, client):
        """Saving default model should call API and re-render with success."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"id": 1}
            resp = client.post("/admin/default-model", data={
                "action": "",
                "name": "Default GPT",
                "model_type": "OPENAI",
                "model": "gpt-4o",
                "api_key": "sk-key",
                "temperature": "0.7",
                "max_tokens": "2000",
            }, follow_redirects=False)
            # Default model page renders template (200), not redirect
            assert resp.status_code == 200


class TestHelperFunctions:
    def test_safe_float_valid(self):
        from app import _safe_float
        assert _safe_float("3.14") == 3.14
        assert _safe_float(42) == 42.0

    def test_safe_float_invalid(self):
        from app import _safe_float
        assert _safe_float("abc", 1.0) == 1.0
        assert _safe_float(None, 2.0) == 2.0

    def test_safe_int_valid(self):
        from app import _safe_int
        assert _safe_int("42") == 42
        assert _safe_int(3.14) == 3

    def test_safe_int_invalid(self):
        from app import _safe_int
        assert _safe_int("abc", 10) == 10
        assert _safe_int(None, 5) == 5

    def test_clamp_page_size_normal(self):
        from app import _clamp_page_size
        assert _clamp_page_size(20) == 20
        assert _clamp_page_size(50) == 50

    def test_clamp_page_size_too_large(self):
        from app import _clamp_page_size
        assert _clamp_page_size(200) == 100

    def test_clamp_page_size_too_small(self):
        from app import _clamp_page_size
        assert _clamp_page_size(0) == 1
        assert _clamp_page_size(-5) == 1


class TestRouting:
    def test_routing_page_renders(self, client):
        """Routing page should render with agents, sessions, and config."""
        _login_admin(client)
        with patch("app.api_get") as mock_get:
            mock_get.return_value.status_code = 200
            mock_get.return_value.json.side_effect = [
                {"agents": [{"agent_phone": "13800138000", "status": "online"}]},
                {"sessions": [], "total": 0},
                {"strategy": "skill_first", "fallback_to_ai": 1},
            ]
            resp = client.get("/admin/tenants/test-tenant/routing")
            assert resp.status_code == 200

    def test_routing_page_api_failure_shows_empty(self, client):
        """Routing page should render even when API is unreachable."""
        _login_admin(client)
        with patch("app.api_get", side_effect=Exception("Connection refused")):
            resp = client.get("/admin/tenants/test-tenant/routing")
            assert resp.status_code == 200

    def test_add_agent(self, client):
        """Adding an agent should call the API and redirect."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 201
            mock_post.return_value.json.return_value = {"status": "ok"}
            resp = client.post("/admin/tenants/test-tenant/routing/agent/add", data={
                "agent_phone": "13800138000",
                "agent_name": "客服小王",
                "status": "online",
                "max_concurrent": "5",
            }, follow_redirects=False)
            assert resp.status_code == 302

    def test_add_agent_empty_phone(self, client):
        """Adding an agent with empty phone should show error."""
        _login_admin(client)
        resp = client.post("/admin/tenants/test-tenant/routing/agent/add", data={
            "agent_phone": "",
            "agent_name": "客服小王",
            "status": "online",
        }, follow_redirects=False)
        assert resp.status_code == 302

    def test_update_agent_status(self, client):
        """Updating agent status should call the API and redirect."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"status": "ok"}
            resp = client.post(
                "/admin/tenants/test-tenant/routing/agent/13800138000/status",
                data={"status": "busy"},
                follow_redirects=False,
            )
            assert resp.status_code == 302

    def test_update_agent_status_invalid(self, client):
        """Updating agent status with invalid value should show error."""
        _login_admin(client)
        resp = client.post(
            "/admin/tenants/test-tenant/routing/agent/13800138000/status",
            data={"status": "invalid_status"},
            follow_redirects=False,
        )
        assert resp.status_code == 302

    def test_update_agent_skills(self, client):
        """Updating agent skills should call the API and redirect."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"status": "ok"}
            resp = client.post(
                "/admin/tenants/test-tenant/routing/agent/13800138000/skills",
                data={"skills": "入住咨询,5\n退房办理,3"},
                follow_redirects=False,
            )
            assert resp.status_code == 302

    def test_update_agent_skills_empty(self, client):
        """Updating agent skills with empty value should work."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"status": "ok"}
            resp = client.post(
                "/admin/tenants/test-tenant/routing/agent/13800138000/skills",
                data={"skills": ""},
                follow_redirects=False,
            )
            assert resp.status_code == 302

    def test_update_routing_config(self, client):
        """Updating routing config should call the API and redirect."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"status": "ok"}
            resp = client.post("/admin/tenants/test-tenant/routing/config", data={
                "strategy": "skill_first",
                "fallback_to_ai": "on",
                "max_queue_size": "50",
                "timeout_seconds": "300",
            }, follow_redirects=False)
            assert resp.status_code == 302

    def test_update_routing_config_invalid_numbers(self, client):
        """Routing config with invalid numbers should use defaults."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"status": "ok"}
            resp = client.post("/admin/tenants/test-tenant/routing/config", data={
                "strategy": "round_robin",
                "max_queue_size": "abc",
                "timeout_seconds": "xyz",
            }, follow_redirects=False)
            assert resp.status_code == 302

    def test_close_session(self, client):
        """Closing a session should call the API and redirect."""
        _login_admin(client)
        with patch("app.api_post") as mock_post:
            mock_post.return_value.status_code = 200
            mock_post.return_value.json.return_value = {"status": "ok"}
            resp = client.post(
                "/admin/tenants/test-tenant/routing/session/42/close",
                data={"agent_phone": "13800138000"},
                follow_redirects=False,
            )
            assert resp.status_code == 302

    def test_close_session_missing_agent_phone(self, client):
        """Closing a session without agent_phone should show error."""
        _login_admin(client)
        resp = client.post(
            "/admin/tenants/test-tenant/routing/session/42/close",
            data={"agent_phone": ""},
            follow_redirects=False,
        )
        assert resp.status_code == 302

    def test_routing_requires_login(self, client):
        """Routing page should redirect to login if not authenticated."""
        resp = client.get("/admin/tenants/test-tenant/routing", follow_redirects=False)
        assert resp.status_code == 302
        assert "/admin/login" in resp.headers["Location"]