import pytest


class TestSubmitFeedback:
    def test_submit_feedback(self, client, auth_headers):
        resp = client.post("/api/feedback", json={
            "reply_history_id": 1,
            "action": "accepted",
            "rating": 5,
            "comment": "很好"
        }, headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["action"] == "accepted"
        assert data["rating"] == 5

    def test_submit_feedback_actions(self, client, auth_headers):
        for action in ["generated", "accepted", "modified", "rejected"]:
            resp = client.post("/api/feedback", json={
                "action": action
            }, headers=auth_headers)
            assert resp.status_code == 200

    def test_submit_feedback_with_modified_text(self, client, auth_headers):
        resp = client.post("/api/feedback", json={
            "action": "modified",
            "modified_text": "修改后的回复"
        }, headers=auth_headers)
        assert resp.status_code == 200
        assert resp.get_json()["modified_text"] == "修改后的回复"

    def test_submit_feedback_invalid_action(self, client, auth_headers):
        resp = client.post("/api/feedback", json={
            "action": "invalid_action"
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_submit_feedback_unauthorized(self, client):
        resp = client.post("/api/feedback", json={"action": "accepted"})
        assert resp.status_code == 401


class TestGetFeedback:
    def test_get_feedback_empty(self, client, auth_headers):
        resp = client.get("/api/feedback", headers=auth_headers)
        assert resp.status_code == 200
        assert resp.get_json() == []

    def test_get_feedback_with_data(self, client, auth_headers):
        client.post("/api/feedback", json={
            "action": "accepted", "rating": 4
        }, headers=auth_headers)
        resp = client.get("/api/feedback", headers=auth_headers)
        assert len(resp.get_json()) == 1

    def test_get_feedback_pagination(self, client, auth_headers):
        for i in range(3):
            client.post("/api/feedback", json={
                "action": "accepted"
            }, headers=auth_headers)
        resp = client.get("/api/feedback?limit=2&offset=0", headers=auth_headers)
        data = resp.get_json()
        assert len(data) == 2

    def test_submit_feedback_comment_too_long(self, client, auth_headers):
        resp = client.post("/api/feedback", json={
            "action": "accepted",
            "comment": "x" * 2001
        }, headers=auth_headers)
        assert resp.status_code == 400
