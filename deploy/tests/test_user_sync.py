"""Tests for user auth → sync DB propagation (Phase 2: user sync fix).

根因: 主 API 注册/登录只写本地 SQLite, 从不写 sync DB (Supabase/MySQL),
      导致 app 端登录的用户在 sync 服务 users 表里找不到, 同步时 push
      数据无主。

修复: 注册/登录成功后, 同步调用 sync 服务 /auth/register 或 /auth/login
      把用户写入 RDS MySQL (RDS 是 sync 服务的主存储, 跨太平洋的 Supabase
      由 sync 服务内部 lazy 同步)。

失败回退: sync 不可达 → enqueue 到 sync_outbox, 由 csbaby-retry worker
         异步重试 (与已有 sync_writer 共用基础设施)。
"""
import json
import os
import sys
import pytest
from unittest.mock import patch, MagicMock

sys.path.insert(0, "/app")
sys.stdout.reconfigure(encoding="utf-8")


@pytest.fixture
def client(tmp_path, monkeypatch):
    """Flask test client with fresh per-test DB (隔离).

    注意: 必须清掉 app + database + 所有 db-dependent 模块缓存, 否则
    模块级别缓存的 DATABASE_PATH 会导致后续测试复用前一个的 DB.
    """
    db_path = str(tmp_path / "test_csbaby.db")
    monkeypatch.setenv("DATABASE_PATH", db_path)
    # 清掉所有可能缓存了 DATABASE_PATH 的模块
    modules_to_clear = [k for k in list(sys.modules.keys())
                       if k.startswith(("app", "infrastructure"))
                       or k == "domain.services.auth_service"]
    for m in modules_to_clear:
        del sys.modules[m]
    from app import app
    from infrastructure.persistence.database import get_connection
    with app.app_context():
        from app import ensure_db, _init_admin, _ensure_blacklist_table
        ensure_db()
        _init_admin()
        _ensure_blacklist_table()
        db = get_connection()
        db.executescript("""
            CREATE TABLE IF NOT EXISTS sync_outbox (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                table_name TEXT NOT NULL,
                op TEXT NOT NULL,
                row_id INTEGER,
                payload TEXT,
                attempts INTEGER DEFAULT 0,
                last_error TEXT,
                next_retry_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
            );
        """)
        db.commit()
        db.close()
    app.config["TESTING"] = True
    with app.test_client() as c:
        yield c



@pytest.fixture
def mock_sync_register():
    """Mock sync service /auth/register HTTP call (succeeds)."""
    with patch("requests.post") as mock:
        mock.return_value.status_code = 200
        mock.return_value.json.return_value = {
            "code": 0,
            "message": "注册成功",
            "data": {
                "userId": "sync-uuid-001",
                "tenantId": "sync-tenant-001",
                "accessToken": "x",
                "refreshToken": "y",
            },
        }
        yield mock


@pytest.fixture
def mock_sync_login():
    """Mock sync service /auth/login HTTP call (succeeds)."""
    with patch("requests.post") as mock:
        mock.return_value.status_code = 200
        mock.return_value.json.return_value = {
            "code": 0,
            "message": "登录成功",
            "data": {
                "userId": "sync-uuid-001",
                "tenantId": "sync-tenant-001",
                "accessToken": "x",
                "refreshToken": "y",
            },
        }
        yield mock


class TestRegisterSyncPropagation:
    """主 API /api/auth/user/register 注册成功后, 必须调用 sync 服务"""

    def test_register_calls_sync_service(self, client, mock_sync_register):
        """注册成功时必须调用 sync 服务 /auth/register 一次."""
        resp = client.post("/api/auth/user/register", json={
            "email": "test-sync-1@test.com",
            "password": "test123",
            "name": "Test Sync 1",
        })
        assert resp.status_code == 201
        # 验证 sync 服务被调用
        assert mock_sync_register.called, \
            "Sync service /auth/register was not called"
        call_args = mock_sync_register.call_args
        # URL 在 positional args[0]
        url = (call_args.args[0] if call_args.args else
               call_args.kwargs.get("url", ""))
        assert "/auth/register" in url

    def test_register_sends_email_and_password(self, client, mock_sync_register):
        """调用 sync 服务时, 必须把 email 和 password 传过去."""
        resp = client.post("/api/auth/user/register", json={
            "email": "test-sync-2@test.com",
            "password": "secret456",
            "name": "Test Sync 2",
        })
        assert resp.status_code == 201
        body = mock_sync_register.call_args.kwargs.get("json", {})
        assert body.get("email") == "test-sync-2@test.com"
        assert body.get("password") == "secret456"
        assert body.get("displayName") == "Test Sync 2"

    def test_register_response_includes_tenant_id(self, client, mock_sync_register):
        """响应里应该返回 sync 服务给的 tenantId, 而不是本地 user_id."""
        resp = client.post("/api/auth/user/register", json={
            "email": "test-sync-3@test.com",
            "password": "test123",
        })
        assert resp.status_code == 201
        data = resp.get_json()
        # 关键: 响应里要有 tenantId, 来自 sync 服务
        assert "tenantId" in data, \
            "Response should include tenantId from sync service"
        assert data["tenantId"] == "sync-tenant-001"

    def test_register_succeeds_even_if_sync_unavailable(self, client):
        """sync 服务不可达时, 主 API 注册仍应成功 (outbox 兜底)."""
        with patch("requests.post") as mock:
            # sync 服务超时/失败
            mock.side_effect = Exception("sync service down")
            resp = client.post("/api/auth/user/register", json={
                "email": "test-sync-4@test.com",
                "password": "test123",
            })
            # 主 API 注册仍应成功 (degrade gracefully)
            assert resp.status_code == 201
            # 验证 outbox 表里应该有一条记录待重试
            import sqlite3
            db = sqlite3.connect(os.environ["DATABASE_PATH"])
            db.row_factory = sqlite3.Row
            rows = db.execute(
                "SELECT * FROM sync_outbox WHERE table_name='users' ORDER BY id DESC"
            ).fetchall()
            db.close()
            # 找 email == test-sync-4@test.com 的那条 (忽略其他测试残留)
            matched = [r for r in rows if json.loads(r["payload"]).get("email") == "test-sync-4@test.com"]
            assert len(matched) > 0, \
                f"Should enqueue test-sync-4 to sync_outbox. Found {len(rows)} rows."
            payload = json.loads(matched[0]["payload"])
            assert payload.get("_sync_kind") == "register"


class TestLoginSyncPropagation:
    """主 API /api/auth/user/login 登录成功后, 同步调用 sync 服务 (lazy 创建)."""

    def test_login_calls_sync_service(self, client, mock_sync_register, mock_sync_login):
        """登录成功时必须调用 sync 服务 /auth/login."""
        # 先注册一个用户
        client.post("/api/auth/user/register", json={
            "email": "test-login-1@test.com",
            "password": "test123",
        })
        # 重置 mock (register 调用过)
        mock_sync_login.reset_mock()
        # 登录
        resp = client.post("/api/auth/user/login", json={
            "identifier": "test-login-1@test.com",
            "password": "test123",
        })
        assert resp.status_code == 200
        # 验证 sync 服务被调用
        assert mock_sync_login.called, \
            "Sync service /auth/login was not called"

    def test_login_response_includes_tenant_id(self, client, mock_sync_register, mock_sync_login):
        """登录响应里应有 sync 服务的 tenantId."""
        client.post("/api/auth/user/register", json={
            "email": "test-login-2@test.com",
            "password": "test123",
        })
        mock_sync_login.reset_mock()
        resp = client.post("/api/auth/user/login", json={
            "identifier": "test-login-2@test.com",
            "password": "test123",
        })
        data = resp.get_json()
        assert "tenantId" in data
        assert data["tenantId"] == "sync-tenant-001"
