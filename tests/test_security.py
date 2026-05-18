"""Security tests: cross-device isolation, input validation, rate limiting."""
import pytest


def _register_device(client, name="device"):
    resp = client.post("/api/auth/register", json={
        "name": name, "platform": "android", "app_version": "1.0.0"
    })
    data = resp.get_json()
    return {"Authorization": f"Bearer {data['token']}", "Content-Type": "application/json"}


class TestCrossDeviceIsolation:
    def test_device_a_cannot_read_device_b_rules(self, client):
        headers_a = _register_device(client, "device-a")
        headers_b = _register_device(client, "device-b")

        # Device B creates a rule
        resp = client.post("/api/rules", json={
            "keyword": "secret", "reply_template": "B's rule"
        }, headers=headers_b)
        rule_id = resp.get_json()["id"]

        # Device A tries to read it
        resp = client.get(f"/api/rules/{rule_id}", headers=headers_a)
        assert resp.status_code == 404

    def test_device_cannot_update_others_rule(self, client):
        headers_a = _register_device(client, "device-a2")
        headers_b = _register_device(client, "device-b2")

        resp = client.post("/api/rules", json={
            "keyword": "original", "reply_template": "orig"
        }, headers=headers_a)
        rule_id = resp.get_json()["id"]

        resp = client.put(f"/api/rules/{rule_id}", json={
            "keyword": "hacked", "reply_template": "bad"
        }, headers=headers_b)
        assert resp.status_code == 404

    def test_device_cannot_delete_others_rule(self, client):
        headers_a = _register_device(client, "device-a3")
        headers_b = _register_device(client, "device-b3")

        resp = client.post("/api/rules", json={
            "keyword": "mine", "reply_template": "mine"
        }, headers=headers_a)
        rule_id = resp.get_json()["id"]

        resp = client.delete(f"/api/rules/{rule_id}", headers=headers_b)
        assert resp.status_code == 404

        # Verify it still exists for device A
        resp = client.get(f"/api/rules/{rule_id}", headers=headers_a)
        assert resp.status_code == 200

    def test_device_cannot_access_others_models(self, client):
        headers_a = _register_device(client, "device-a4")
        headers_b = _register_device(client, "device-b4")

        resp = client.post("/api/models", json={
            "name": "my-model", "model_type": "OPENAI", "model": "gpt-4o",
            "api_key": "sk-secret-key"
        }, headers=headers_a)
        model_id = resp.get_json()["id"]

        resp = client.get(f"/api/models/{model_id}", headers=headers_b)
        assert resp.status_code == 404

    def test_device_cannot_delete_others_model(self, client):
        headers_a = _register_device(client, "device-a5")
        headers_b = _register_device(client, "device-b5")

        resp = client.post("/api/models", json={
            "name": "my-model", "model_type": "OPENAI", "model": "gpt-4o"
        }, headers=headers_a)
        model_id = resp.get_json()["id"]

        resp = client.delete(f"/api/models/{model_id}", headers=headers_b)
        assert resp.status_code == 404


class TestInputValidation:
    def test_create_rule_empty_keyword(self, client, auth_headers):
        resp = client.post("/api/rules", json={
            "keyword": "", "reply_template": "test"
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_create_rule_invalid_match_type(self, client, auth_headers):
        resp = client.post("/api/rules", json={
            "keyword": "test", "match_type": "INVALID", "reply_template": "r"
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_create_rule_priority_out_of_range(self, client, auth_headers):
        resp = client.post("/api/rules", json={
            "keyword": "test", "priority": 200, "reply_template": "r"
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_create_rule_negative_priority(self, client, auth_headers):
        resp = client.post("/api/rules", json={
            "keyword": "test", "priority": -1, "reply_template": "r"
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_create_rule_target_names_not_list(self, client, auth_headers):
        resp = client.post("/api/rules", json={
            "keyword": "test", "target_names": "not-a-list", "reply_template": "r"
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_create_model_empty_name(self, client, auth_headers):
        resp = client.post("/api/models", json={
            "name": "", "model_type": "OPENAI"
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_create_model_invalid_temperature(self, client, auth_headers):
        resp = client.post("/api/models", json={
            "name": "test", "temperature": 5.0
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_create_model_invalid_max_tokens(self, client, auth_headers):
        resp = client.post("/api/models", json={
            "name": "test", "max_tokens": -1
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_batch_import_empty_rules(self, client, auth_headers):
        resp = client.post("/api/rules/batch", json={
            "rules": [], "mode": "append"
        }, headers=auth_headers)
        assert resp.status_code == 200

    def test_batch_import_too_many_rules(self, client, auth_headers):
        rules = [{"keyword": f"rule{i}"} for i in range(1001)]
        resp = client.post("/api/rules/batch", json={
            "rules": rules, "mode": "append"
        }, headers=auth_headers)
        assert resp.status_code == 400


class TestApiKeyMasking:
    def test_model_api_key_masked_in_list(self, client, auth_headers):
        client.post("/api/models", json={
            "name": "test", "model_type": "OPENAI",
            "api_key": "sk-1234567890abcdef"
        }, headers=auth_headers)

        resp = client.get("/api/models", headers=auth_headers)
        data = resp.get_json()
        assert len(data) == 1
        # Should be masked: ************cdef
        assert data[0]["api_key"].startswith("*")
        assert data[0]["api_key"].endswith("cdef")
        assert "1234567890" not in data[0]["api_key"]

    def test_model_api_key_masked_in_single(self, client, auth_headers):
        resp = client.post("/api/models", json={
            "name": "test", "model_type": "OPENAI",
            "api_key": "short"
        }, headers=auth_headers)
        model_id = resp.get_json()["id"]

        resp = client.get(f"/api/models/{model_id}", headers=auth_headers)
        data = resp.get_json()
        # Keys <= 8 chars get full mask
        assert data["api_key"] == "****"

    def test_backup_export_masks_api_keys(self, client, auth_headers):
        client.post("/api/models", json={
            "name": "test", "model_type": "OPENAI",
            "api_key": "sk-secret-key-12345"
        }, headers=auth_headers)

        resp = client.get("/api/backup", headers=auth_headers)
        data = resp.get_json()
        for model in data["models"]:
            assert model["api_key"].startswith("*")
            assert "secret" not in model["api_key"]
