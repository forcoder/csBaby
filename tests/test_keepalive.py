"""Tests for the keepalive script."""

import json
import pytest
from unittest.mock import patch, MagicMock
import urllib.error

import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "scripts"))
import keepalive


class TestPingHealth:
    @patch("keepalive.urllib.request.urlopen")
    def test_success_returns_true(self, mock_urlopen):
        mock_resp = MagicMock()
        mock_resp.status = 200
        mock_resp.read.return_value = b'{"status":"ok"}'
        mock_resp.__enter__ = lambda s: s
        mock_resp.__exit__ = MagicMock(return_value=False)
        mock_urlopen.return_value = mock_resp
        assert keepalive.ping_health() is True

    @patch("keepalive.urllib.request.urlopen")
    def test_503_returns_false(self, mock_urlopen):
        mock_resp = MagicMock()
        mock_resp.status = 503
        mock_resp.read.return_value = b'{"status":"degraded"}'
        mock_resp.__enter__ = lambda s: s
        mock_resp.__exit__ = MagicMock(return_value=False)
        mock_urlopen.return_value = mock_resp
        assert keepalive.ping_health() is False

    @patch("keepalive.urllib.request.urlopen")
    def test_url_error_returns_false(self, mock_urlopen):
        mock_urlopen.side_effect = urllib.error.URLError("Connection refused")
        assert keepalive.ping_health() is False

    @patch("keepalive.urllib.request.urlopen")
    def test_timeout_returns_false(self, mock_urlopen):
        mock_urlopen.side_effect = TimeoutError("timed out")
        assert keepalive.ping_health() is False


class TestMain:
    @patch("keepalive.ping_health", return_value=True)
    def test_main_returns_0_on_success(self, mock_ping):
        assert keepalive.main() == 0

    @patch("keepalive.ping_health", return_value=False)
    def test_main_returns_1_on_failure(self, mock_ping):
        assert keepalive.main() == 1
