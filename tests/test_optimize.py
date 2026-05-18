import pytest


class TestGetMetrics:
    def test_get_metrics_empty(self, client, auth_headers):
        resp = client.get("/api/optimize/metrics", headers=auth_headers)
        assert resp.status_code == 200
        assert resp.get_json() == []

    def test_get_metrics_with_data(self, client, auth_headers):
        # Submit feedback which triggers metric recording
        client.post("/api/feedback", json={
            "action": "generated"
        }, headers=auth_headers)
        resp = client.get("/api/optimize/metrics?days=1", headers=auth_headers)
        data = resp.get_json()
        assert len(data) >= 1
        assert data[0]["total_generated"] >= 1


class TestAnalyze:
    def test_analyze_no_data(self, client, auth_headers):
        resp = client.post("/api/optimize/analyze", headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["status"] == "no_data"

    def test_analyze_with_data(self, client, auth_headers):
        for action in ["generated", "generated", "generated", "accepted", "modified", "rejected"]:
            client.post("/api/feedback", json={"action": action}, headers=auth_headers)
        resp = client.post("/api/optimize/analyze", headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["status"] == "ok"
        assert data["total_generated"] >= 3
        assert "accept_rate" in data
        assert "suggestions" in data

    def test_analyze_rates_sum_to_one(self, client, auth_headers):
        """Accept + modify + reject rates should sum to ~1.0."""
        for _ in range(5):
            client.post("/api/feedback", json={"action": "generated"}, headers=auth_headers)
        for _ in range(3):
            client.post("/api/feedback", json={"action": "accepted"}, headers=auth_headers)
        for _ in range(2):
            client.post("/api/feedback", json={"action": "modified"}, headers=auth_headers)
        resp = client.post("/api/optimize/analyze", headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["status"] == "ok"
        total_rate = data["accept_rate"] + data["modify_rate"] + data["reject_rate"]
        assert abs(total_rate - 1.0) < 0.01

    def test_analyze_low_acceptance_gives_suggestion(self, client, auth_headers):
        """Low acceptance rate should generate a suggestion."""
        for _ in range(10):
            client.post("/api/feedback", json={"action": "generated"}, headers=auth_headers)
        for _ in range(2):
            client.post("/api/feedback", json={"action": "rejected"}, headers=auth_headers)
        resp = client.post("/api/optimize/analyze", headers=auth_headers)
        data = resp.get_json()
        assert data["status"] == "ok"
        assert data["accept_rate"] < 0.5
        assert any("接受率" in s for s in data["suggestions"])

    def test_analyze_period_days_in_response(self, client, auth_headers):
        """Response should include period_days field."""
        client.post("/api/feedback", json={"action": "generated"}, headers=auth_headers)
        resp = client.post("/api/optimize/analyze", headers=auth_headers)
        data = resp.get_json()
        assert data["period_days"] == 30
