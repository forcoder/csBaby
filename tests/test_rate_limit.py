import pytest


class TestRateLimit:
    def test_register_rate_limited(self, client):
        """Register endpoint should rate-limit after 10 requests."""
        for i in range(10):
            resp = client.post("/api/auth/register", json={
                "name": f"device-{i}",
                "platform": "android",
                "app_version": "1.0.0"
            })
            assert resp.status_code == 200
        # 11th request should be rate limited
        resp = client.post("/api/auth/register", json={
            "name": "device-overflow",
            "platform": "android",
            "app_version": "1.0.0"
        })
        assert resp.status_code == 429

    def test_ai_generate_rate_limited(self, client, auth_headers):
        """AI generate endpoint should rate-limit after 30 requests."""
        for i in range(30):
            resp = client.post("/api/ai/generate", json={
                "message": f"msg-{i}"
            }, headers=auth_headers)
            # May get 400 (no model) but should not be 429
            assert resp.status_code != 429
        # 31st request should be rate limited
        resp = client.post("/api/ai/generate", json={
            "message": "overflow"
        }, headers=auth_headers)
        assert resp.status_code == 429

    def test_batch_rules_rate_limited(self, client, auth_headers):
        """Batch rules endpoint should rate-limit after 10 requests."""
        for i in range(10):
            resp = client.post("/api/rules/batch", json={
                "rules": [{"keyword": f"k{i}", "reply_template": "r"}],
                "mode": "append"
            }, headers=auth_headers)
            assert resp.status_code != 429
        # 11th request should be rate limited
        resp = client.post("/api/rules/batch", json={
            "rules": [{"keyword": "overflow", "reply_template": "r"}],
            "mode": "append"
        }, headers=auth_headers)
        assert resp.status_code == 429
