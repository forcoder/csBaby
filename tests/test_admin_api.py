"""Tests for Admin API endpoints."""
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

    # Set env vars BEFORE importing app (app.py requires JWT_SECRET at import time)
    os.environ["DATABASE_PATH"] = db_path
    os.environ["JWT_SECRET"] = "test-secret-key"
    os.environ["ADMIN_PHONE"] = "13800138000"
    os.environ["ADMIN_PASSWORD"] = "testadmin123"

    import app as app_module

    # Reset global state without reload (to preserve coverage tracking)
    app_module._db_initialized = False
    app_module._admin_table_initialized = False
    app_module._admin_tokens = {}
    app_module._blacklist_initialized = False
    app_module._audit_log_initialized = False
    app_module._rate_limit_store = {}
    app_module.JWT_SECRET = "test-secret-key"
    app_module.auth_service._secret = "test-secret-key"
    app_module.auth_service._expire_days = 30

    # Initialize the test database
    app_module.init_db()
    # Clear SQLite-backed tables for test isolation
    try:
        db = app_module.get_connection()
        db.execute("DELETE FROM admin_accounts")
        db.execute("DELETE FROM agent_status")
        db.execute("DELETE FROM agent_skills")
        db.execute("DELETE FROM sessions")
        db.execute("DELETE FROM routing_config")
        db.execute("DELETE FROM blacklist")
        db.execute("DELETE FROM audit_log")
        db.commit()
        db.close()
    except Exception:
        pass

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
        pass  # Windows may lock the file briefly


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


class TestAdminLogin:
    def test_login_success(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.post("/api/admin/login", json={
            "phone": "13800138000",
            "password": "testadmin123"
        })
        assert resp.status_code == 200
        data = resp.get_json()
        assert "token" in data
        assert data["is_admin"] is True

    def test_login_wrong_password(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.post("/api/admin/login", json={
            "phone": "13800138000",
            "password": "wrongpassword"
        })
        assert resp.status_code == 401

    def test_login_unknown_phone(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.post("/api/admin/login", json={
            "phone": "99999999999",
            "password": "testadmin123"
        })
        assert resp.status_code == 401

    def test_login_missing_fields(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.post("/api/admin/login", json={})
        assert resp.status_code == 401


class TestAdminAuth:
    def test_no_token_rejected(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.get("/api/admin/stats")
        assert resp.status_code == 401

    def test_invalid_token_rejected(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.get("/api/admin/stats", headers={
            "Authorization": "Bearer invalid-token"
        })
        assert resp.status_code == 401


class TestAdminStats:
    def test_stats_empty(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.get("/api/admin/stats", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert "device_count" in data
        assert "rule_count" in data
        assert "history_count" in data

    def test_stats_with_device(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        resp = client.get("/api/admin/stats", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["device_count"] >= 1

    def test_recent_tenants(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        resp = client.get("/api/admin/recent-tenants", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert isinstance(data, list)


class TestAdminTenants:
    def test_list_tenants_empty(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.get("/api/admin/tenants", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert "items" in data
        assert "total" in data

    def test_list_tenants_with_device(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        resp = client.get("/api/admin/tenants", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["total"] >= 1

    def test_get_tenant(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.get(f"/api/admin/tenants/{device_id}", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["id"] == device_id

    def test_get_tenant_not_found(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.get("/api/admin/tenants/nonexistent", headers=headers)
        assert resp.status_code == 404

    def test_update_tenant(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.put(f"/api/admin/tenants/{device_id}",
                          headers=headers, json={"is_active": 1})
        assert resp.status_code == 200

    def test_update_tenant_name(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.put(f"/api/admin/tenants/{device_id}",
                          headers=headers, json={"name": "Updated Name"})
        assert resp.status_code == 200
        # Verify the name was actually updated
        resp2 = client.get(f"/api/admin/tenants/{device_id}", headers=headers)
        assert resp2.get_json()["name"] == "Updated Name"

    def test_update_tenant_not_found(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        resp = client.put("/api/admin/tenants/nonexistent-id",
                          headers=headers, json={"is_active": 1})
        assert resp.status_code == 404

    def test_update_tenant_name_too_long(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.put(f"/api/admin/tenants/{device_id}",
                          headers=headers, json={"name": "x" * 201})
        assert resp.status_code == 400


class TestAdminTenantRules:
    def test_get_rules_empty(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.get(f"/api/admin/tenants/{device_id}/rules", headers=headers)
        assert resp.status_code == 200
        assert resp.get_json() == []

    def test_create_rule(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/rules", headers=headers, json={
            "keyword": "测试关键词",
            "match_type": "CONTAINS",
            "reply_template": "测试回复",
            "priority": 1,
        })
        assert resp.status_code == 201
        data = resp.get_json()
        assert data["keyword"] == "测试关键词"

    def test_update_rule(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        # Create first
        resp = client.post(f"/api/admin/tenants/{device_id}/rules", headers=headers, json={
            "keyword": "原关键词",
            "reply_template": "原回复",
        })
        rule_id = resp.get_json()["id"]
        # Update
        resp = client.put(f"/api/admin/tenants/{device_id}/rules/{rule_id}",
                          headers=headers, json={"keyword": "新关键词"})
        assert resp.status_code == 200
        assert resp.get_json()["keyword"] == "新关键词"

    def test_delete_rule(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/rules", headers=headers, json={
            "keyword": "待删除",
            "reply_template": "回复",
        })
        rule_id = resp.get_json()["id"]
        resp = client.delete(f"/api/admin/tenants/{device_id}/rules/{rule_id}", headers=headers)
        assert resp.status_code == 200

    def test_delete_rule_not_found(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.delete(f"/api/admin/tenants/{device_id}/rules/99999", headers=headers)
        assert resp.status_code == 404

    def test_batch_import_rules(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/rules/batch",
                           headers=headers, json={
                               "rules": [
                                   {"keyword": "批量1", "reply_template": "回复1"},
                                   {"keyword": "批量2", "reply_template": "回复2"},
                               ],
                               "mode": "override",
                           })
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["imported"] == 2


class TestAdminBlacklist:
    def test_get_blacklist_empty(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.get(f"/api/admin/tenants/{device_id}/blacklist", headers=headers)
        assert resp.status_code == 200
        assert resp.get_json() == []

    def test_add_blacklist(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/blacklist", headers=headers, json={
            "type": "KEYWORD",
            "value": "垃圾词汇",
            "description": "测试黑名单",
        })
        assert resp.status_code == 201
        data = resp.get_json()
        assert data["status"] == "created"

    def test_add_blacklist_missing_value(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/blacklist", headers=headers, json={
            "type": "KEYWORD",
        })
        assert resp.status_code == 400

    def test_update_blacklist(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/blacklist", headers=headers, json={
            "type": "KEYWORD",
            "value": "原词汇",
        })
        bid = resp.get_json()["id"]
        resp = client.put(f"/api/admin/tenants/{device_id}/blacklist/{bid}",
                          headers=headers, json={"value": "新词汇"})
        assert resp.status_code == 200

    def test_delete_blacklist(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/blacklist", headers=headers, json={
            "type": "KEYWORD",
            "value": "待删除",
        })
        bid = resp.get_json()["id"]
        resp = client.delete(f"/api/admin/tenants/{device_id}/blacklist/{bid}", headers=headers)
        assert resp.status_code == 200

    def test_clear_blacklist(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        # Add some entries
        client.post(f"/api/admin/tenants/{device_id}/blacklist", headers=headers, json={
            "type": "KEYWORD", "value": "词1",
        })
        client.post(f"/api/admin/tenants/{device_id}/blacklist", headers=headers, json={
            "type": "KEYWORD", "value": "词2",
        })
        # Clear
        resp = client.post(f"/api/admin/tenants/{device_id}/blacklist/clear", headers=headers)
        assert resp.status_code == 200
        # Verify empty
        resp = client.get(f"/api/admin/tenants/{device_id}/blacklist", headers=headers)
        assert resp.get_json() == []


class TestAdminModels:
    def test_get_models_empty(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.get(f"/api/admin/tenants/{device_id}/models", headers=headers)
        assert resp.status_code == 200
        assert resp.get_json() == []

    def test_create_model(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/models", headers=headers, json={
            "name": "test-model",
            "model_type": "OPENAI",
            "model": "gpt-4o",
            "api_key": "sk-test-key",
        })
        assert resp.status_code == 201
        data = resp.get_json()
        assert data["name"] == "test-model"

    def test_update_model(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/models", headers=headers, json={
            "name": "original",
            "model_type": "OPENAI",
            "model": "gpt-4o",
        })
        model_id = resp.get_json()["id"]
        resp = client.put(f"/api/admin/tenants/{device_id}/models/{model_id}",
                          headers=headers, json={"name": "updated"})
        assert resp.status_code == 200
        assert resp.get_json()["name"] == "updated"

    def test_delete_model(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/models", headers=headers, json={
            "name": "to-delete",
            "model_type": "OPENAI",
            "model": "gpt-4o",
        })
        model_id = resp.get_json()["id"]
        resp = client.delete(f"/api/admin/tenants/{device_id}/models/{model_id}", headers=headers)
        assert resp.status_code == 200


class TestAdminDefaultModel:
    def test_get_global_default_empty(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.get("/api/admin/tenants/_global/default-model", headers=headers)
        assert resp.status_code == 200

    def test_save_global_default(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.post("/api/admin/tenants/_global/default-model", headers=headers, json={
            "name": "global-default",
            "model_type": "OPENAI",
            "model": "gpt-4o",
            "api_key": "sk-global-key",
        })
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["name"] == "global-default"

    def test_get_tenant_default_empty(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.get(f"/api/admin/tenants/{device_id}/default-model", headers=headers)
        assert resp.status_code == 200

    def test_save_tenant_default(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/default-model", headers=headers, json={
            "name": "tenant-default",
            "model_type": "OPENAI",
            "model": "gpt-4o",
        })
        assert resp.status_code == 200

    def test_delete_tenant_default(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        # Create first
        client.post(f"/api/admin/tenants/{device_id}/default-model", headers=headers, json={
            "name": "to-delete",
            "model_type": "OPENAI",
            "model": "gpt-4o",
        })
        # Delete
        resp = client.delete(f"/api/admin/tenants/{device_id}/default-model", headers=headers)
        assert resp.status_code == 200


class TestAdminHistory:
    def test_get_history_empty(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.get(f"/api/admin/tenants/{device_id}/history", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert "items" in data
        assert "total" in data

    def test_get_history_with_data(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        device_headers = {
            "Authorization": f"Bearer {registered_device['token']}",
            "Content-Type": "application/json"
        }
        # Add history via device API
        client.post("/api/history", headers=device_headers, json={
            "original_message": "你好",
            "reply_content": "您好！",
            "source": "ai",
        })
        # Check via admin API
        resp = client.get(f"/api/admin/tenants/{device_id}/history", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["total"] >= 1


class TestAdminFeedback:
    def test_get_feedback_empty(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.get(f"/api/admin/tenants/{device_id}/feedback", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert "items" in data


class TestAdminMetrics:
    def test_get_metrics(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.get(f"/api/admin/tenants/{device_id}/metrics?days=7", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert "items" in data

    def test_get_metrics_summary(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.get(f"/api/admin/tenants/{device_id}/metrics/summary?days=7", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert "total_generated" in data


class TestAdminAuditLog:
    def test_get_audit_log(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.get("/api/admin/audit-log", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert "items" in data

    def test_audit_log_after_action(self, admin_client):
        client, headers, app_module = admin_client
        # Create an admin (generates audit log)
        client.post("/api/admin/admins", headers=headers, json={
            "phone": "13900139000",
            "password": "newadmin123",
        })
        resp = client.get("/api/admin/audit-log", headers=headers)
        data = resp.get_json()
        assert data["total"] >= 1


class TestAdminManagement:
    def test_list_admins(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.get("/api/admin/admins", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert isinstance(data, list)
        assert len(data) >= 1

    def test_create_admin(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.post("/api/admin/admins", headers=headers, json={
            "phone": "13900139000",
            "password": "newadmin123",
        })
        assert resp.status_code == 201

    def test_create_admin_duplicate(self, admin_client):
        client, headers, app_module = admin_client
        client.post("/api/admin/admins", headers=headers, json={
            "phone": "13900139000",
            "password": "newadmin123",
        })
        resp = client.post("/api/admin/admins", headers=headers, json={
            "phone": "13900139000",
            "password": "another123",
        })
        assert resp.status_code == 409

    def test_create_admin_missing_fields(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.post("/api/admin/admins", headers=headers, json={
            "phone": "13900139000",
        })
        assert resp.status_code == 400

    def test_delete_admin(self, admin_client):
        client, headers, app_module = admin_client
        client.post("/api/admin/admins", headers=headers, json={
            "phone": "13900139000",
            "password": "newadmin123",
        })
        resp = client.delete("/api/admin/admins/13900139000", headers=headers)
        assert resp.status_code == 200

    def test_delete_self_forbidden(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.delete("/api/admin/admins/13800138000", headers=headers)
        assert resp.status_code == 400


class TestAdminAgentRouting:
    def test_set_agent_status(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.post("/api/agent/status", headers=headers, json={
            "agent_phone": "13700137000",
            "agent_name": "测试客服",
            "status": "online",
            "max_concurrent": 5,
        })
        assert resp.status_code == 200

    def test_set_agent_status_invalid(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.post("/api/agent/status", headers=headers, json={
            "agent_phone": "13700137000",
            "status": "invalid_status",
        })
        assert resp.status_code == 400

    def test_set_agent_skills(self, admin_client):
        client, headers, app_module = admin_client
        # Must create agent_status first due to FK constraint
        client.post("/api/agent/status", headers=headers, json={
            "agent_phone": "13700137000",
            "status": "online",
        })
        resp = client.post("/api/agent/skills", headers=headers, json={
            "agent_phone": "13700137000",
            "skills": [{"skill_tag": "售前", "proficiency": 8}],
        })
        assert resp.status_code == 200

    def test_get_agents(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        # Set an agent status first
        client.post("/api/agent/status", headers=headers, json={
            "agent_phone": "13700137000",
            "status": "online",
        })
        resp = client.get(f"/api/admin/tenants/{device_id}/agents", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert "agents" in data

    def test_get_sessions(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.get(f"/api/admin/tenants/{device_id}/sessions", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert "sessions" in data

    def test_get_routing_config(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.get(f"/api/admin/tenants/{device_id}/routing/config", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert "strategy" in data

    def test_update_routing_config(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.post("/api/routing/config", headers=headers, json={
            "strategy": "round_robin",
            "fallback_to_ai": True,
            "max_queue_size": 100,
        })
        assert resp.status_code == 200

    def test_close_conversation(self, admin_client):
        client, headers, app_module = admin_client
        # Add a session to close via SQLite
        db = app_module.get_connection()
        db.execute(
            "INSERT INTO sessions (id, tenant_id, assigned_agent_phone, status, created_at) VALUES (?, ?, ?, ?, ?)",
            (1, "test-tenant", "13700137000", "active", "2026-01-01 00:00:00")
        )
        db.commit()
        db.close()
        resp = client.post("/api/conversation/1/close", headers=headers, json={
            "agent_phone": "13700137000",
        })
        assert resp.status_code == 200
        # Verify in DB
        db = app_module.get_connection()
        row = db.execute("SELECT status FROM sessions WHERE id=1").fetchone()
        db.close()
        assert row is not None
        assert row["status"] == "closed"


class TestAdminChangePassword:
    def test_change_password_success(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.post("/api/auth/change_password", headers=headers, json={
            "old_password": "testadmin123",
            "new_password": "newpassword456",
        })
        assert resp.status_code == 200

    def test_change_password_wrong_old(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.post("/api/auth/change_password", headers=headers, json={
            "old_password": "wrongpassword",
            "new_password": "newpassword456",
        })
        assert resp.status_code == 401

    def test_change_password_too_short(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.post("/api/auth/change_password", headers=headers, json={
            "old_password": "testadmin123",
            "new_password": "short",
        })
        assert resp.status_code == 400

    def test_change_password_missing_fields(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.post("/api/auth/change_password", headers=headers, json={})
        assert resp.status_code == 400


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
        resp = client.post(f"/api/admin/tenants/{device_id}/style", headers=headers, json={"theme": "invalid_theme"})
        assert resp.status_code == 200
        resp = client.get(f"/api/admin/tenants/{device_id}/style", headers=headers)
        data = resp.get_json()
        assert data["theme"] == "light"

    def test_update_style_invalid_font_size(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/style", headers=headers, json={"font_size": "huge"})
        assert resp.status_code == 200
        resp = client.get(f"/api/admin/tenants/{device_id}/style", headers=headers)
        data = resp.get_json()
        assert data["font_size"] == "medium"

    def test_update_style_invalid_bubble_style(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/style", headers=headers, json={"bubble_style": "hexagonal"})
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
        resp = client.post(f"/api/admin/tenants/{device_id}/app-config", headers=headers, json={"session_timeout": 5})
        assert resp.status_code == 200
        resp = client.get(f"/api/admin/tenants/{device_id}/app-config", headers=headers)
        data = resp.get_json()
        assert data["session_timeout"] == 30
        resp = client.post(f"/api/admin/tenants/{device_id}/app-config", headers=headers, json={"session_timeout": 99999})
        assert resp.status_code == 200
        resp = client.get(f"/api/admin/tenants/{device_id}/app-config", headers=headers)
        data = resp.get_json()
        assert data["session_timeout"] == 3600

    def test_update_app_config_max_queue_size_clamped(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/app-config", headers=headers, json={"max_queue_size": 0})
        assert resp.status_code == 200
        resp = client.get(f"/api/admin/tenants/{device_id}/app-config", headers=headers)
        data = resp.get_json()
        assert data["max_queue_size"] == 1
        resp = client.post(f"/api/admin/tenants/{device_id}/app-config", headers=headers, json={"max_queue_size": 1000})
        assert resp.status_code == 200
        resp = client.get(f"/api/admin/tenants/{device_id}/app-config", headers=headers)
        data = resp.get_json()
        assert data["max_queue_size"] == 500

    def test_update_app_config_invalid_timeout_string(self, admin_client, registered_device):
        client, headers, app_module = admin_client
        device_id = registered_device["device_id"]
        resp = client.post(f"/api/admin/tenants/{device_id}/app-config", headers=headers, json={"session_timeout": "abc"})
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
        resp = client.post(f"/api/admin/tenants/{device_id}/backup/restore", headers=headers, json={"backup": backup_data})
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["status"] == "ok"
        assert data["restored"]["rules"] >= 1

    def test_restore_backup_not_found(self, admin_client):
        client, headers, app_module = admin_client
        resp = client.post("/api/admin/tenants/nonexistent/backup/restore", headers=headers, json={"backup": {"version": 2, "rules": []}})
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
                {"keyword": "restore_test_1", "match_type": "CONTAINS", "reply_template": "回复1",
                 "category": "test", "target_type": "ALL", "target_names": "[]", "priority": 1, "enabled": 1},
                {"keyword": "restore_test_2", "match_type": "EXACT", "reply_template": "回复2",
                 "category": "test", "target_type": "ALL", "target_names": "[]", "priority": 2, "enabled": 1},
            ],
        }
        resp = client.post(f"/api/admin/tenants/{device_id}/backup/restore", headers=headers, json={"backup": backup})
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["restored"]["rules"] == 2
        resp = client.get(f"/api/admin/tenants/{device_id}/rules", headers=headers)
        assert resp.status_code == 200
        data = resp.get_json()
        keywords = [r["keyword"] for r in data]
        assert "restore_test_1" in keywords
        assert "restore_test_2" in keywords
