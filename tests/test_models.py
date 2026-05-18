import pytest


class TestCreateModel:
    def test_create_model(self, client, auth_headers):
        resp = client.post("/api/models", json={
            "name": "GPT-4",
            "model_type": "OPENAI",
            "model": "gpt-4o",
            "api_key": "sk-test-key",
            "temperature": 0.7,
            "max_tokens": 2000
        }, headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["name"] == "GPT-4"
        assert data["model"] == "gpt-4o"
        assert data["enabled"] == 1

    def test_create_model_as_default(self, client, auth_headers):
        client.post("/api/models", json={
            "name": "Model1", "model_type": "OPENAI",
            "model": "gpt-4", "api_key": "key1", "is_default": 1
        }, headers=auth_headers)
        resp = client.post("/api/models", json={
            "name": "Model2", "model_type": "OPENAI",
            "model": "gpt-4o", "api_key": "key2", "is_default": 1
        }, headers=auth_headers)
        assert resp.status_code == 200
        # Verify Model1 is no longer default
        models = client.get("/api/models", headers=auth_headers).get_json()
        defaults = [m for m in models if m["is_default"] == 1]
        assert len(defaults) == 1
        assert defaults[0]["name"] == "Model2"

    def test_create_model_unauthorized(self, client):
        resp = client.post("/api/models", json={"name": "test"})
        assert resp.status_code == 401


class TestGetModels:
    def test_get_models_empty(self, client, auth_headers):
        resp = client.get("/api/models", headers=auth_headers)
        assert resp.status_code == 200
        assert resp.get_json() == []

    def test_get_models_with_data(self, client, auth_headers):
        client.post("/api/models", json={
            "name": "M1", "model_type": "OPENAI", "model": "gpt-4", "api_key": "k"
        }, headers=auth_headers)
        resp = client.get("/api/models", headers=auth_headers)
        assert len(resp.get_json()) == 1


class TestGetSingleModel:
    def test_get_model(self, client, auth_headers):
        created = client.post("/api/models", json={
            "name": "Test", "model_type": "OPENAI", "model": "gpt-4", "api_key": "k"
        }, headers=auth_headers)
        model_id = created.get_json()["id"]
        resp = client.get(f"/api/models/{model_id}", headers=auth_headers)
        assert resp.status_code == 200
        assert resp.get_json()["name"] == "Test"

    def test_get_model_not_found(self, client, auth_headers):
        resp = client.get("/api/models/99999", headers=auth_headers)
        assert resp.status_code == 404


class TestUpdateModel:
    def test_update_model(self, client, auth_headers):
        created = client.post("/api/models", json={
            "name": "Old", "model_type": "OPENAI", "model": "gpt-4", "api_key": "k"
        }, headers=auth_headers)
        model_id = created.get_json()["id"]
        resp = client.put(f"/api/models/{model_id}", json={
            "name": "Updated", "model": "gpt-4o", "temperature": 0.9
        }, headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["name"] == "Updated"
        assert data["temperature"] == 0.9

    def test_update_nonexistent_model(self, client, auth_headers):
        resp = client.put("/api/models/99999", json={"name": "x"}, headers=auth_headers)
        assert resp.status_code == 404

    def test_update_model_empty_name(self, client, auth_headers):
        created = client.post("/api/models", json={
            "name": "TestModel", "model_type": "OPENAI"
        }, headers=auth_headers)
        model_id = created.get_json()["id"]
        resp = client.put(f"/api/models/{model_id}", json={
            "name": "", "model_type": "OPENAI"
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_update_model_invalid_temperature(self, client, auth_headers):
        created = client.post("/api/models", json={
            "name": "TestModel", "model_type": "OPENAI"
        }, headers=auth_headers)
        model_id = created.get_json()["id"]
        resp = client.put(f"/api/models/{model_id}", json={
            "name": "TestModel", "temperature": 5.0
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_update_model_invalid_max_tokens(self, client, auth_headers):
        created = client.post("/api/models", json={
            "name": "TestModel", "model_type": "OPENAI"
        }, headers=auth_headers)
        model_id = created.get_json()["id"]
        resp = client.put(f"/api/models/{model_id}", json={
            "name": "TestModel", "max_tokens": -1
        }, headers=auth_headers)
        assert resp.status_code == 400


class TestDeleteModel:
    def test_delete_model(self, client, auth_headers):
        created = client.post("/api/models", json={
            "name": "ToDelete", "model_type": "OPENAI", "model": "gpt-4", "api_key": "k"
        }, headers=auth_headers)
        model_id = created.get_json()["id"]
        resp = client.delete(f"/api/models/{model_id}", headers=auth_headers)
        assert resp.status_code == 200
        assert resp.get_json()["status"] == "deleted"

    def test_delete_model_not_found(self, client, auth_headers):
        resp = client.delete("/api/models/99999", headers=auth_headers)
        assert resp.status_code == 404


class TestModelInputValidation:
    def test_create_model_empty_name(self, client, auth_headers):
        resp = client.post("/api/models", json={
            "name": "", "model_type": "OPENAI"
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_create_model_name_whitespace_only(self, client, auth_headers):
        resp = client.post("/api/models", json={
            "name": "   ", "model_type": "OPENAI"
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_create_model_temperature_too_high(self, client, auth_headers):
        resp = client.post("/api/models", json={
            "name": "test", "temperature": 3.0
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_create_model_temperature_negative(self, client, auth_headers):
        resp = client.post("/api/models", json={
            "name": "test", "temperature": -0.5
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_create_model_max_tokens_zero(self, client, auth_headers):
        resp = client.post("/api/models", json={
            "name": "test", "max_tokens": 0
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_create_model_max_tokens_too_large(self, client, auth_headers):
        resp = client.post("/api/models", json={
            "name": "test", "max_tokens": 50000
        }, headers=auth_headers)
        assert resp.status_code == 400

    def test_create_model_max_tokens_negative(self, client, auth_headers):
        resp = client.post("/api/models", json={
            "name": "test", "max_tokens": -100
        }, headers=auth_headers)
        assert resp.status_code == 400


class TestModelTest:
    def test_test_model(self, client, auth_headers):
        created = client.post("/api/models", json={
            "name": "Test", "model_type": "OPENAI", "model": "gpt-4", "api_key": "k"
        }, headers=auth_headers)
        model_id = created.get_json()["id"]
        resp = client.post(f"/api/models/{model_id}/test", headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["success"] is True
        assert data["model"] == "gpt-4"

    def test_test_model_not_found(self, client, auth_headers):
        resp = client.post("/api/models/99999/test", headers=auth_headers)
        assert resp.status_code == 404


class TestModelTestEndpoint:
    def test_test_model_endpoint(self, client, auth_headers):
        created = client.post("/api/models", json={
            "name": "Test", "model_type": "OPENAI",
            "model": "gpt-4", "api_key": "k"
        }, headers=auth_headers)
        model_id = created.get_json()["id"]
        resp = client.post(f"/api/models/{model_id}/test", headers=auth_headers)
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["success"] is True
        assert data["model"] == "gpt-4"

    def test_test_model_not_found(self, client, auth_headers):
        resp = client.post("/api/models/99999/test", headers=auth_headers)
        assert resp.status_code == 404


class TestModelApiKeyMasking:
    def test_model_api_key_masked_in_list(self, client, auth_headers):
        client.post("/api/models", json={
            "name": "test", "model_type": "OPENAI",
            "api_key": "sk-1234567890abcdef"
        }, headers=auth_headers)
        resp = client.get("/api/models", headers=auth_headers)
        data = resp.get_json()
        assert len(data) == 1
        assert data[0]["api_key"].startswith("*")
        assert data[0]["api_key"].endswith("cdef")
        assert "1234567890" not in data[0]["api_key"]

    def test_model_api_key_short_fully_masked(self, client, auth_headers):
        resp = client.post("/api/models", json={
            "name": "test", "model_type": "OPENAI",
            "api_key": "short"
        }, headers=auth_headers)
        model_id = resp.get_json()["id"]
        resp = client.get(f"/api/models/{model_id}", headers=auth_headers)
        data = resp.get_json()
        assert data["api_key"] == "****"

    def test_model_no_api_key_not_masked(self, client, auth_headers):
        resp = client.post("/api/models", json={
            "name": "test", "model_type": "OPENAI"
        }, headers=auth_headers)
        data = resp.get_json()
        assert data["api_key"] == ""
