"""Tests for new admin API endpoints: style config, app config, backup."""
import os
import sys
import json
import tempfile
import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


@pytest.fixture
def admin_client():
    """Create a test client with admin authentication."""
    db_fd, db_path = tempfile.mkstemp(suffix=".db")
    os.close(db_fd)
    os.environ["DATABASE_PATH"] = db_path
    os.environ["JWT_SECRET"] = "test-secret-key"
    os.environ["ADMIN_PHONE"] = "13800138000"
    os.environ["ADMIN_PASSWORD"] = "testadmin123"

    import importlib
    import app as app_module
    importlib.reload(app_module)

    # Reset global state
    app_module._admin_accounts = {}
    app_module._admin_tokens = {}
    app_module._blacklist_initialized = False
    app_module._audit_log_initialized = False
    app_module._agent_status = {}
    app_module._agent_skills = {}
    app_module._sessions = {}

    app_module.app.config["TESTING"] = True
    client = app_module.app.test_client()

    # Login as admin
    resp = client.post("/api/admin/login", json={
        "phone": "13800138000",
        "password": "testadmin123"
    })
    assert resp.status_code == 200
    data = resp.get_json()
    token = data["token"]
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

    yield client, headers, app_module

    # Cleanup
    import gc
    gc.collect()
    try:
        if os.path.exists(db_path):
            os.unlink(db_path)
    except Exception:
        pass


@pytest.fixture
def registered_device(admin_client):
    """Register a test device and return its info."""
    client, headers, app_module = admin_client
    resp = client.post("/api/auth/register", json={
        "name": "test-device",
        "platform": "android",
        "app_version": "1.0.0"
    })
    assert resp.status_code == 200
    data = resp.get_json()
    return data


class TestAdminTenantStyle:
    """Tests for tenant style config endpoints."""

    def test_get_style_default(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.get(f"/api/admin/tenants/{device_id}/style", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["theme"] == "light"
        assert data["primary_color"] == "#1976D2"
        assert data["font_size"] == "medium"
        assert data["bubble_style"] == "rounded"

    def test_update_style(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/style", headers=headers, json={
            "theme": "dark",
            "primary_color": "#000000",
            "accent_color": "#FF0000",
            "font_size": "large",
            "bubble_style": "square",
            "avatar_enabled": False,
            "show_timestamp": False,
            "send_sound": False,
            "custom_css": ".test { color: red; }",
        })
        assert resp.status_code == 200
        resp = client.get(f"/api/admin/tenants/{device_id}/style", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["theme"] == "dark"
        assert data["primary_color"] == "#000000"
        assert data["accent_color"] == "#FF0000"
        assert data["font_size"] == "large"
        assert data["bubble_style"] == "square"
        assert data["avatar_enabled"] == 0
        assert data["show_timestamp"] == 0
        assert data["send_sound"] == 0
        assert data["custom_css"] == ".test { color: red; }"

    def test_update_style_invalid_theme(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/style", headers=headers, json={
            "theme": "invalid_theme",
        })
        assert resp.status_code == 200
        resp = client.get(f"/api/admin/tenants/{device_id}/style", headers=headers)
        data = resp.get_json()
        assert data["theme"] == "light"

    def test_update_style_invalid_font_size(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/style", headers=headers, json={
            "font_size": "huge",
        })
        assert resp.status_code == 200
        resp = client.get(f"/api/admin/tenants/{device_id}/style", headers=headers)
        data = resp.get_json()
        assert data["font_size"] == "medium"

    def test_update_style_invalid_bubble_style(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/style", headers=headers, json={
            "bubble_style": "hexagonal",
        })
        assert resp.status_code == 200
        resp = client.get(f"/api/admin/tenants/{device_id}/style", headers=headers)
        data = resp.get_json()
        assert data["bubble_style"] == "rounded"


class TestAdminTenantAppConfig:
    """Tests for tenant app config endpoints."""

    def test_get_app_config_default(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.get(f"/api/admin/tenants/{device_id}/app-config", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["app_name"] == "客服小秘"
        assert data["language"] == "zh-CN"
        assert data["session_timeout"] == 300
        assert data["max_queue_size"] == 50

    def test_update_app_config(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/app-config", headers=headers, json={
            "app_name": "我的客服",
            "welcome_message": "欢迎！",
            "offline_message": "暂时离线",
            "auto_reply_enabled": False,
            "notification_enabled": False,
            "voice_enabled": True,
            "language": "en-US",
            "session_timeout": 600,
            "max_queue_size": 100,
            "file_upload_enabled": False,
        })
        assert resp.status_code == 200
        resp = client.get(f"/api/admin/tenants/{device_id}/app-config", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["app_name"] == "我的客服"
        assert data["welcome_message"] == "欢迎！"
        assert data["offline_message"] == "暂时离线"
        assert data["auto_reply_enabled"] == 0
        assert data["notification_enabled"] == 0
        assert data["voice_enabled"] == 1
        assert data["language"] == "en-US"
        assert data["session_timeout"] == 600
        assert data["max_queue_size"] == 100
        assert data["file_upload_enabled"] == 0

    def test_update_app_config_session_timeout_clamped(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/app-config", headers=headers, json={
            "session_timeout": 5,
        })
        assert resp.status_code == 200
        resp = client.get(f"/api/admin/tenants/{device_id}/app-config", headers=headers)
        data = resp.get_json()
        assert data["session_timeout"] == 30
        resp = client.post(f"/api/admin/tenants/{device_id}/app-config", headers=headers, json={
            "session_timeout": 99999,
        })
        assert resp.status_code == 200
        resp = client.get(f"/api/admin/tenants/{device_id}/app-config", headers=headers)
        data = resp.get_json()
        assert data["session_timeout"] == 3600

    def test_update_app_config_max_queue_size_clamped(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/app-config", headers=headers, json={
            "max_queue_size": 0,
        })
        assert resp.status_code == 200
        resp = client.get(f"/api/admin/tenants/{device_id}/app-config", headers=headers)
        data = resp.get_json()
        assert data["max_queue_size"] == 1
        resp = client.post(f"/api/admin/tenants/{device_id}/app-config", headers=headers, json={
            "max_queue_size": 1000,
        })
        assert resp.status_code == 200
        resp = client.get(f"/api/admin/tenants/{device_id}/app-config", headers=headers)
        data = resp.get_json()
        assert data["max_queue_size"] == 500

    def test_update_app_config_invalid_timeout_string(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/app-config", headers=headers, json={
            "session_timeout": "abc",
        })
        assert resp.status_code == 200
        resp = client.get(f"/api/admin/tenants/{device_id}/app-config", headers=headers)
        data = resp.get_json()
        assert data["session_timeout"] == 300


class TestAdminTenantBackup:
    """Tests for tenant backup API endpoints."""

    def test_export_backup(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.get(f"/api/admin/tenants/{device_id}/backup", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["version"] == 2
        assert data["device_id"] == device_id
        assert "rules" in data
        assert "models" in data
        assert "history" in data
        assert "feedback" in data
        assert "metrics" in data
        assert "blacklist" in data

    def test_export_backup_not_found(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.get("/api/admin/tenants/nonexistent/backup", headers=headers)
        assert resp.status_code == 404

    def test_restore_backup(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        client.post(f"/api/admin/tenants/{device_id}/rules", headers=headers, json={
            "keyword": "测试关键词",
            "match_type": "CONTAINS",
            "reply_template": "测试回复",
        })
        resp = client.get(f"/api/admin/tenants/{device_id}/backup", headers=headers)
        assert resp.status_code == 200
        backup_data = resp.get_json()
        assert len(backup_data["rules"]) >= 1
        resp = client.post(f"/api/admin/tenants/{device_id}/backup/restore", headers=headers, json={
            "backup": backup_data,
        })
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["status"] == "ok"
        assert data["restored"]["rules"] >= 1

    def test_restore_backup_not_found(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.post("/api/admin/tenants/nonexistent/backup/restore", headers=headers, json={
            "backup": {"version": 2, "rules": []},
        })
        assert resp.status_code == 404

    def test_restore_backup_invalid_payload(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/backup/restore", headers=headers, json={})
        assert resp.status_code == 400

    def test_restore_backup_rules(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        backup = {
            "version": 2,
            "rules": [
                {
                    "keyword": "restore_test_1",
                    "match_type": "CONTAINS",
                    "reply_template": "回复1",
                    "category": "test",
                    "target_type": "ALL",
                    "target_names": "[]",
                    "priority": 1,
                    "enabled": 1,
                },
                {
                    "keyword": "restore_test_2",
                    "match_type": "EXACT",
                    "reply_template": "回复2",
                    "category": "test",
                    "target_type": "ALL",
                    "target_names": "[]",
                    "priority": 2,
                    "enabled": 1,
                },
            ],
        }
        resp = client.post(f"/api/admin/tenants/{device_id}/backup/restore", headers=headers, json={
            "backup": backup,
        })
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["restored"]["rules"] == 2
        resp = client.get(f"/api/admin/tenants/{device_id}/rules", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        keywords = [r["keyword"] for r in data]
        assert "restore_test_1" in keywords
        assert "restore_test_2" in keywords
