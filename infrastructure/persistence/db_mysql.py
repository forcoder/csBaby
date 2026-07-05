"""pymysql 连接池 - 镜像 db_supabase.py 接口,供 sync_writer 双写路径使用。

约束:
  - 接口签名与 db_supabase 完全一致 (get_connection / put_connection / health_check)
  - 通过环境变量 RDS_DB_URL 注入连接串,示例:
      mysql+pymysql://user:pass@host:3306/db?charset=utf8mb4
    或 raw URL:
      mysql://user:pass@host:3306/db
  - 未配置 RDS_DB_URL 时,健康检查返回 False,但调用 get_connection 会抛 RuntimeError
    (与 db_supabase 行为一致)

Why:
  Phase 1 双写过渡期,RDS 是镜像库,Supabase 仍是权威。失败优雅降级,不阻塞 API。
How:
  用 dbutils PooledDB 提供连接池(minconn=1, maxconn=5),与 db_supabase 一致。
"""
import os
import logging
import threading
from typing import Optional

try:
    import pymysql
    from dbutils.pooled_db import PooledDB
    _HAS_PYMYSQL = True
except ImportError:
    _HAS_PYMYSQL = False


logger = logging.getLogger(__name__)
_pool: Optional["PooledDB"] = None
_pool_lock = threading.Lock()


def _parse_mysql_url(url: str) -> dict:
    """mysql://user:pass@host:port/db → pymysql.connect kwargs.

    支持两种前缀: mysql:// 和 mysql+pymysql://
    """
    prefix = "mysql://"
    if url.startswith("mysql+pymysql://"):
        prefix = "mysql+pymysql://"
    if not url.startswith(prefix):
        raise ValueError(f"RDS_DB_URL must start with {prefix}, got: {url[:20]}...")
    body = url[len(prefix):]
    # user:pass@host:port/db?charset=utf8mb4
    if "@" not in body:
        raise ValueError(f"RDS_DB_URL missing credentials: {url}")
    creds, rest = body.split("@", 1)
    user, password = creds.split(":", 1)
    # host:port/db
    if "/" in rest:
        host_port, db_part = rest.split("/", 1)
    else:
        host_port, db_part = rest, ""
    if ":" in host_port:
        host, port = host_port.split(":", 1)
        port = int(port)
    else:
        host, port = host_port, 3306
    db = db_part.split("?")[0] if "?" in db_part else db_part
    return dict(host=host, port=port, user=user, password=password, database=db or None,
                charset="utf8mb4", connect_timeout=10, autocommit=False)


def _get_pool() -> "PooledDB":
    global _pool
    if _pool is not None:
        return _pool
    with _pool_lock:
        if _pool is not None:
            return _pool
        if not _HAS_PYMYSQL:
            raise RuntimeError(
                "pymysql/dbutils not installed. Run: pip install pymysql dbutils"
            )
        url = os.environ.get("RDS_DB_URL")
        if not url:
            raise RuntimeError("RDS_DB_URL environment variable not set")
        kwargs = _parse_mysql_url(url)
        _pool = PooledDB(
            creator=pymysql,
            mincached=1,
            maxcached=5,
            maxconnections=10,
            blocking=True,
            **kwargs,
        )
        logger.info("RDS MySQL pool initialized host=%s db=%s", kwargs["host"], kwargs["database"])
        return _pool


def _reset_pool() -> None:
    """测试辅助: 重置连接池。"""
    global _pool
    if _pool is not None:
        try:
            _pool.close()
        except Exception:
            pass
    _pool = None


def get_connection():
    """获取一个连接。

    返回 pymysql 连接对象,支持 with 上下文管理器 (自动 commit/rollback)。
    """
    pool = _get_pool()
    return pool.connection()


def put_connection(conn) -> None:
    """归还连接到池(pymysql dbutils 模式下 conn.close() 自动归还)。"""
    try:
        conn.close()
    except Exception:
        pass


def health_check() -> bool:
    """健康检查: SELECT 1,成功返回 True,失败返回 False。"""
    if not os.environ.get("RDS_DB_URL"):
        return False
    try:
        conn = get_connection()
        try:
            cur = conn.cursor()
            cur.execute("SELECT 1")
            cur.fetchone()
            return True
        finally:
            put_connection(conn)
    except Exception as e:
        logger.warning("rds health_check failed: %s", e)
        return False