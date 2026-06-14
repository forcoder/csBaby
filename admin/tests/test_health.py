"""Tests for the admin /health endpoint.

The /health endpoint is used by Docker HEALTHCHECK and load balancers to
verify the admin service is responsive. It must:
- return 200 OK
- return JSON with status and service name
- be unauthenticated (no login required, for liveness probes)
"""
import json


def test_admin_health_returns_200(client):
    resp = client.get("/admin/health")
    assert resp.status_code == 200


def test_admin_health_returns_json(client):
    resp = client.get("/admin/health")
    # Flask may return the dict as JSON; status_code confirms success
    data = resp.get_json()
    assert data is not None
    assert data["status"] == "ok"


def test_admin_health_service_name(client):
    resp = client.get("/admin/health")
    data = resp.get_json()
    assert data["service"] == "csbaby-admin"


def test_admin_health_does_not_require_login(client):
    """Liveness probes must work without authentication."""
    resp = client.get("/admin/health")
    assert resp.status_code == 200
