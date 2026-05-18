import pytest


class TestGetHistory:
    def test_get_history_empty(self, client, auth_headers):
        resp = client.get("/api/history", headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["items"] == []
        assert data["total"] == 0

    def test_get_history_pagination(self, client, auth_headers):
        for i in range(3):
            client.post("/api/history", json={
                "original_message": f"msg{i}",
                "reply_content": f"reply{i}"
            }, headers=auth_headers)
        resp = client.get("/api/history?limit=2&offset=0", headers=auth_headers)
        data = resp.get_json()
        assert len(data["items"]) == 2
        assert data["total"] == 3

    def test_get_history_limit_capped(self, client, auth_headers):
        for i in range(5):
            client.post("/api/history", json={
                "original_message": f"msg{i}",
                "reply_content": f"reply{i}"
            }, headers=auth_headers)
        resp = client.get("/api/history?limit=9999", headers=auth_headers)
        data = resp.get_json()
        assert data["limit"] <= 200

    def test_get_history_unauthorized(self, client):
        resp = client.get("/api/history")
        assert resp.status_code == 401


class TestRecordHistory:
    def test_record_history(self, client, auth_headers):
        resp = client.post("/api/history", json={
            "original_message": "用户消息",
            "reply_content": "回复内容",
            "source": "ai",
            "model_used": "gpt-4o",
            "confidence": 0.9,
            "response_time_ms": 120,
            "platform": "wechat",
            "customer_name": "张三",
            "house_name": "房源A"
        }, headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["original_message"] == "用户消息"
        assert data["reply_content"] == "回复内容"
        assert data["source"] == "ai"

    def test_record_history_minimal(self, client, auth_headers):
        resp = client.post("/api/history", json={}, headers=auth_headers)
        assert resp.status_code == 200

    def test_record_history_unauthorized(self, client):
        resp = client.post("/api/history", json={"original_message": "test"})
        assert resp.status_code == 401

    def test_record_history_message_too_long(self, client, auth_headers):
        resp = client.post("/api/history", json={
            "original_message": "x" * 50001,
            "reply_content": "ok"
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_record_history_reply_too_long(self, client, auth_headers):
        resp = client.post("/api/history", json={
            "original_message": "ok",
            "reply_content": "x" * 50001
        }, headers=auth_headers)
        assert resp.status_code == 400
