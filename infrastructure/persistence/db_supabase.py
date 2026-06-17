import os
from contextlib import contextmanager
from typing import Optional

import psycopg2
from psycopg2 import pool as pg_pool
from psycopg2.extras import RealDictCursor

_pool: Optional[pg_pool.ThreadedConnectionPool] = None


class _PoolProxy:
    """Lazy proxy over psycopg2 ThreadedConnectionPool.

    Defers actual pool construction until first use so callers/tests can
    instantiate the proxy without a reachable database host.
    """

    def __init__(self, dsn: str, minconn: int = 1, maxconn: int = 5) -> None:
        self._dsn = dsn
        self._minconn = minconn
        self._maxconn = maxconn
        self._real: Optional[pg_pool.ThreadedConnectionPool] = None

    def _ensure(self) -> pg_pool.ThreadedConnectionPool:
        if self._real is None:
            self._real = pg_pool.ThreadedConnectionPool(
                minconn=self._minconn, maxconn=self._maxconn, dsn=self._dsn
            )
        return self._real

    def getconn(self):
        return self._ensure().getconn()

    def putconn(self, conn) -> None:
        self._ensure().putconn(conn)

    def closeall(self) -> None:
        if self._real is not None:
            self._real.closeall()


class _ConnectionHandle:
    """Dual-mode connection accessor.

    Calling the handle returns the underlying connection directly (used by
    code that manages the lifecycle itself). Using it as a context manager
    yields the connection and returns it to the pool on exit.
    """

    def __init__(self, pool: _PoolProxy, conn) -> None:
        self._pool = pool
        self._conn = conn

    def __call__(self):
        return self._conn

    def __enter__(self):
        return self._conn

    def __exit__(self, exc_type, exc, tb) -> None:
        try:
            self._pool.putconn(self._conn)
        except Exception:
            pass


def _reset_pool() -> None:
    """Test helper: reset module-level pool."""
    global _pool
    if _pool is not None:
        try:
            _pool.closeall()
        except Exception:
            pass
    _pool = None


def get_pool():
    global _pool
    if _pool is not None:
        return _pool
    url = os.environ.get("SUPABASE_DB_URL")
    if not url:
        raise RuntimeError("SUPABASE_DB_URL environment variable not set")
    _pool = _PoolProxy(dsn=url, minconn=1, maxconn=5)
    return _pool


def get_connection():
    """Acquire a raw connection from the pool.

    Returns the connection directly. The returned psycopg2 connection supports
    the context-manager protocol, so callers may use either::

        conn = get_connection()
        try:
            ...
        finally:
            put_connection(conn)

    or::

        with get_connection() as conn:
            ...
    """
    pool = get_pool()
    return pool.getconn()


def put_connection(conn) -> None:
    """Return a connection to the pool."""
    pool = get_pool()
    pool.putconn(conn)


def health_check() -> bool:
    import logging
    try:
        with get_connection() as conn:
            cur = conn.cursor()
            cur.execute("SELECT 1")
            cur.fetchone()
        return True
    except Exception as e:
        logging.exception("supabase health_check failed: %s", e)
        return False