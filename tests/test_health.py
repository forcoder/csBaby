"""Tests for the /health endpoint."""

import json
import pytest


def test_health_returns_200(client):
    resp = client.get("/health")
    assert resp.status_code == 200


def test_health_returns_json(client):
    resp = client.get("/health")
    data = resp.get_json()
    assert data is not None


def test_health_status_ok(client):
    resp = client.get("/health")
    data = resp.get_json()
    assert data["status"] == "ok"


def test_health_service_name(client):
    resp = client.get("/health")
    data = resp.get_json()
    assert data["service"] == "csBaby-api"


def test_health_has_timestamp(client):
    resp = client.get("/health")
    data = resp.get_json()
    assert "timestamp" in data
    assert "T" in data["timestamp"]  # ISO 8601 format


def test_health_has_database_field(client):
    resp = client.get("/health")
    data = resp.get_json()
    assert "database" in data


def test_health_database_ok(client):
    resp = client.get("/health")
    data = resp.get_json()
    assert data["database"] == "ok"
