import os
import pytest
from unittest.mock import patch, MagicMock


def test_get_connection_returns_pool_instance():
    from infrastructure.persistence.db_supabase import get_pool, _reset_pool
    _reset_pool()
    with patch.dict(os.environ, {"SUPABASE_DB_URL": "postgresql://u:p@h:5432/db"}):
        pool = get_pool()
        assert pool is not None


def test_get_connection_acquires_from_pool():
    from infrastructure.persistence.db_supabase import get_connection, _reset_pool
    _reset_pool()
    with patch.dict(os.environ, {"SUPABASE_DB_URL": "postgresql://u:p@h:5432/db"}):
        mock_pool = MagicMock()
        mock_conn = MagicMock()
        mock_pool.getconn.return_value = mock_conn
        with patch("infrastructure.persistence.db_supabase.get_pool", return_value=mock_pool):
            conn = get_connection()
            assert conn is mock_conn


def test_health_check_returns_true_when_ping_ok():
    from infrastructure.persistence.db_supabase import health_check
    with patch("infrastructure.persistence.db_supabase.get_connection") as mock_gc:
        mock_conn = MagicMock()
        mock_gc.return_value.__enter__.return_value = mock_conn
        result = health_check()
        assert result is True
        mock_conn.execute.assert_called()


def test_health_check_returns_false_on_error():
    from infrastructure.persistence.db_supabase import health_check
    with patch("infrastructure.persistence.db_supabase.get_connection", side_effect=Exception("conn refused")):
        result = health_check()
        assert result is False