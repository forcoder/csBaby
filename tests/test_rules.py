import pytest


class TestCreateRule:
    def test_create_rule(self, client, auth_headers):
        resp = client.post("/api/rules", json={
            "keyword": "你好",
            "match_type": "CONTAINS",
            "reply_template": "您好！请问有什么可以帮您？",
            "category": "greeting",
            "priority": 10
        }, headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["keyword"] == "你好"
        assert data["reply_template"] == "您好！请问有什么可以帮您？"
        assert data["enabled"] == 1

    def test_create_rule_minimal(self, client, auth_headers):
        resp = client.post("/api/rules", json={
            "keyword": "test",
            "reply_template": "reply"
        }, headers=auth_headers)
        assert resp.status_code == 200

    def test_create_rule_unauthorized(self, client):
        resp = client.post("/api/rules", json={"keyword": "test", "reply_template": "r"})
        assert resp.status_code == 401


class TestGetRules:
    def test_get_rules_empty(self, client, auth_headers):
        resp = client.get("/api/rules", headers=auth_headers)
        assert resp.status_code == 200
        assert resp.get_json() == []

    def test_get_rules_with_data(self, client, auth_headers):
        client.post("/api/rules", json={
            "keyword": "你好", "reply_template": "您好"
        }, headers=auth_headers)
        resp = client.get("/api/rules", headers=auth_headers)
        data = resp.get_json()
        assert len(data) == 1
        assert data[0]["keyword"] == "你好"

    def test_get_rules_ordered_by_priority(self, client, auth_headers):
        client.post("/api/rules", json={
            "keyword": "low", "reply_template": "r", "priority": 1
        }, headers=auth_headers)
        client.post("/api/rules", json={
            "keyword": "high", "reply_template": "r", "priority": 100
        }, headers=auth_headers)
        resp = client.get("/api/rules", headers=auth_headers)
        data = resp.get_json()
        assert data[0]["priority"] >= data[1]["priority"]


class TestGetSingleRule:
    def test_get_rule(self, client, auth_headers):
        created = client.post("/api/rules", json={
            "keyword": "test", "reply_template": "reply"
        }, headers=auth_headers)
        rule_id = created.get_json()["id"]
        resp = client.get(f"/api/rules/{rule_id}", headers=auth_headers)
        assert resp.status_code == 200
        assert resp.get_json()["keyword"] == "test"

    def test_get_rule_not_found(self, client, auth_headers):
        resp = client.get("/api/rules/99999", headers=auth_headers)
        assert resp.status_code == 404


class TestUpdateRule:
    def test_update_rule(self, client, auth_headers):
        created = client.post("/api/rules", json={
            "keyword": "old", "reply_template": "old_reply"
        }, headers=auth_headers)
        rule_id = created.get_json()["id"]
        resp = client.put(f"/api/rules/{rule_id}", json={
            "keyword": "new",
            "reply_template": "new_reply",
            "match_type": "EXACT",
            "priority": 50,
            "enabled": 0
        }, headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["keyword"] == "new"
        assert data["match_type"] == "EXACT"

    def test_update_nonexistent_rule(self, client, auth_headers):
        resp = client.put("/api/rules/99999", json={"keyword": "x"}, headers=auth_headers)
        assert resp.status_code == 404

    def test_update_rule_empty_keyword(self, client, auth_headers):
        created = client.post("/api/rules", json={
            "keyword": "valid", "reply_template": "r"
        }, headers=auth_headers)
        rule_id = created.get_json()["id"]
        resp = client.put(f"/api/rules/{rule_id}", json={
            "keyword": "", "reply_template": "r"
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_update_rule_invalid_match_type(self, client, auth_headers):
        created = client.post("/api/rules", json={
            "keyword": "valid", "reply_template": "r"
        }, headers=auth_headers)
        rule_id = created.get_json()["id"]
        resp = client.put(f"/api/rules/{rule_id}", json={
            "keyword": "valid", "match_type": "INVALID", "reply_template": "r"
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_update_rule_priority_out_of_range(self, client, auth_headers):
        created = client.post("/api/rules", json={
            "keyword": "valid", "reply_template": "r"
        }, headers=auth_headers)
        rule_id = created.get_json()["id"]
        resp = client.put(f"/api/rules/{rule_id}", json={
            "keyword": "valid", "priority": 200, "reply_template": "r"
        }, headers=auth_headers)
        assert resp.status_code == 400


class TestDeleteRule:
    def test_delete_rule(self, client, auth_headers):
        created = client.post("/api/rules", json={
            "keyword": "to_delete", "reply_template": "r"
        }, headers=auth_headers)
        rule_id = created.get_json()["id"]
        resp = client.delete(f"/api/rules/{rule_id}", headers=auth_headers)
        assert resp.status_code == 200
        assert resp.get_json()["status"] == "deleted"

    def test_delete_rule_not_found(self, client, auth_headers):
        resp = client.delete("/api/rules/99999", headers=auth_headers)
        assert resp.status_code == 404

    def test_delete_rule_unauthorized(self, client):
        resp = client.delete("/api/rules/1")
        assert resp.status_code == 401


class TestBatchImport:
    def test_batch_import_append(self, client, auth_headers):
        resp = client.post("/api/rules/batch", json={
            "rules": [
                {"keyword": "k1", "reply_template": "r1"},
                {"keyword": "k2", "reply_template": "r2"},
            ],
            "mode": "append"
        }, headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["imported"] == 2

    def test_batch_import_override(self, client, auth_headers):
        client.post("/api/rules", json={
            "keyword": "existing", "reply_template": "r"
        }, headers=auth_headers)
        resp = client.post("/api/rules/batch", json={
            "rules": [{"keyword": "new_rule", "reply_template": "nr"}],
            "mode": "override"
        }, headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["total"] == 1

    def test_batch_import_empty(self, client, auth_headers):
        resp = client.post("/api/rules/batch", json={
            "rules": [], "mode": "append"
        }, headers=auth_headers)
        assert resp.status_code == 200
        assert resp.get_json()["imported"] == 0
