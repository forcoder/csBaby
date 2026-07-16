"""Supabase 数据库健康检查。

尝试连接 Supabase PostgreSQL，返回连接状态。
连接参数从环境变量读取。
"""

import os
import logging

logger = logging.getLogger(__name__)


def health_check() -> bool:
    """检查 Supabase 数据库连接是否正常。

    Returns:
        True 表示连接正常，False 表示异常。
    """
    try:
        import psycopg2
        conn = psycopg2.connect(
            host=os.getenv('SUPABASE_HOST', os.getenv('DB_HOST', 'localhost')),
            port=int(os.getenv('SUPABASE_PORT', os.getenv('DB_PORT', '5432'))),
            user=os.getenv('SUPABASE_USER', os.getenv('DB_USER', 'postgres')),
            password=os.getenv('SUPABASE_PASSWORD', os.getenv('DB_PASSWORD', '')),
            dbname=os.getenv('SUPABASE_DB', os.getenv('DB_NAME', 'postgres')),
            connect_timeout=3,
        )
        conn.close()
        return True
    except Exception as exc:
        logger.warning("Supabase health check failed: %s", exc)
        return False