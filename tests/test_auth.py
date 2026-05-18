import pytest


class TestRegister:
    def test_register_returns_device_and_token(self, client):
        resp = client.post("/api/auth/register", json={
            "name": "test-device",
            "platform": "android",
            "app_version": "1.0.0"
        })
        assert resp.status_code == 200
        data = resp.get_json()
        assert "device_id" in data
        assert "token" in data
        assert "expires_in" in data
        assert data["expires_in"] == 30 * 86400

    def test_register_defaults(self, client):
        resp = client.post("/api/auth/register", json={})
        assert resp.status_code == 200
        data = resp.get_json()
        assert "device_id" in data
        assert "token" in data

    def test_register_custom_platform(self, client):
        resp = client.post("/api/auth/register", json={"platform": "ios"})
        assert resp.status_code == 200

    def test_register_invalid_platform(self, client):
        resp = client.post("/api/auth/register", json={"platform": "windows"})
        assert resp.status_code == 400
        assert "platform" in resp.get_json()["error"].lower() or "invalid" in resp.get_json()["error"].lower()

    def test_register_invalid_platform_type(self, client):
        resp = client.post("/api/auth/register", json={"platform": 123})
        assert resp.status_code == 400

    def test_register_app_version_too_long(self, client):
        resp = client.post("/api/auth/register", json={"app_version": "1" * 51})
        assert resp.status_code == 400
        assert "app_version" in resp.get_json()["error"].lower()

    def test_register_app_version_max_length(self, client):
        resp = client.post("/api/auth/register", json={"app_version": "1" * 50})
        assert resp.status_code == 200

    def test_register_invalid_app_version_type(self, client):
        resp = client.post("/api/auth/register", json={"app_version": 123})
        assert resp.status_code == 400


class TestHeartbeat:
    def test_heartbeat_ok(self, client, auth_headers):
        resp = client.post("/api/auth/heartbeat", headers=auth_headers)
        assert resp.status_code == 200
        assert resp.get_json()["status"] == "ok"

    def test_heartbeat_unauthorized(self, client):
        resp = client.post("/api/auth/heartbeat")
        assert resp.status_code == 401

    def test_heartbeat_bad_token(self, client):
        resp = client.post("/api/auth/heartbeat",
                           headers={"Authorization": "Bearer invalid.token.here"})
        assert resp.status_code == 401


class TestHealthCheck:
    def test_health(self, client):
        resp = client.get("/health")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["status"] == "ok"
        assert data["service"] == "csBaby-api"


class TestCORS:
    def test_cors_headers(self, client):
        resp = client.get("/health", headers={"Origin": "http://localhost:3000"})
        assert "Access-Control-Allow-Origin" in resp.headers
        assert resp.headers["Access-Control-Allow-Origin"] == "http://localhost:3000"

    def test_cors_rejects_unknown_origin(self, client):
        resp = client.get("/health", headers={"Origin": "http://evil.com"})
        assert "Access-Control-Allow-Origin" not in resp.headers

    def test_cors_missing_origin_no_header(self, client):
        resp = client.get("/health")
        # Without Origin header, CORS header is not set
        assert "Access-Control-Allow-Origin" not in resp.headers

    def test_options_preflight(self, client):
        resp = client.options("/api/rules")
        # OPTIONS handler returns empty string; status may be 200 or 204
        assert resp.status_code in (200, 204)


class TestCORSWithSpaces:
    def test_cors_origins_with_spaces(self, app_module):
        """CORS origins should be stripped of whitespace."""
        origins = app_module.CORS_ORIGINS
        for origin in origins:
            assert " " not in origin, f"Origin '{origin}' contains spaces"


class TestJWTExpiration:
    def test_expired_token_rejected(self, client, app_module):
        """An expired JWT token should be rejected."""
        import time
        from domain.services.auth_service import AuthService
        # Create a token with negative expiry (already expired)
        expired_service = AuthService("test-secret-key", jwt_expire_days=-1)
        # Generate and immediately the token is expired
        token = expired_service.generate_token("test-device")
        # Small delay to ensure expiry
        time.sleep(0.1)
        resp = client.get("/api/rules", headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json"
        })
        assert resp.status_code == 401


class TestJWTPadding:
    """Tests for JWT base64 padding fix."""

    def test_token_with_various_payload_lengths(self, app_module):
        """JWT tokens should verify correctly regardless of payload length."""
        from domain.services.auth_service import AuthService
        service = AuthService("test-secret-key")
        # Test with various device IDs that produce different payload lengths
        for device_id in ["a", "ab", "abc", "abcd", "abcde", "abcdef", "x" * 50, "y" * 99]:
            token = service.generate_token(device_id)
            verified_id = service.verify_token(token)
            assert verified_id == device_id, f"Token verification failed for device_id='{device_id}'"

    def test_token_verification_roundtrip(self, app_module):
        """Generated tokens should always verify back to the same device_id."""
        from domain.services.auth_service import AuthService
        service = AuthService("my-jwt-secret")
        device_id = "test-device-12345"
        token = service.generate_token(device_id)
        assert service.verify_token(token) == device_id

    def test_tampered_token_rejected(self, app_module):
        """Tampered tokens should fail verification."""
        from domain.services.auth_service import AuthService
        service = AuthService("test-secret")
        token = service.generate_token("device1")
        parts = token.split(".")
        # Tamper with the payload
        tampered = parts[0] + ".AAAA." + parts[2]
        assert service.verify_token(tampered) is None
