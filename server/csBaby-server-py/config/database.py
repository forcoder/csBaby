"""多数据库配置 — 同时支持 PostgreSQL (psycopg2) 和 MySQL (pymysql)。

通过环境变量 DB_TYPE 快速切换:
  DB_TYPE=postgresql (默认) — 连接 PostgreSQL, %s 占位符, ILIKE, ON CONFLICT ...
  DB_TYPE=mysql       — 连接 MySQL,   %s 占位符, LIKE,  ON DUPLICATE KEY UPDATE ...

驱动、连接参数、SQL 语法差异统一在此文件处理。
"""
import os
import logging
import queue
import threading

logger = logging.getLogger(__name__)

# ========== 数据库类型检测 ==========
DB_TYPE = os.getenv('DB_TYPE', 'postgresql').lower()
IS_MYSQL = DB_TYPE == 'mysql'
IS_POSTGRES = DB_TYPE == 'postgresql'

logger.info(f"Database type: {DB_TYPE}")

# ========== 惰性加载驱动 ==========
if IS_MYSQL:
    import pymysql as _driver
    _driver.connect  # verify import
    DB_PORT_DEFAULT = 3306
    _Connection = _driver.Connection
else:
    import psycopg2 as _driver
    from psycopg2 import pool as _pool
    DB_PORT_DEFAULT = 5432
    _Connection = None  # psycopg2 connections managed by pool

# ========== 连接配置 ==========
DB_CONFIG = {
    'host': os.getenv('DB_HOST', 'localhost'),
    'port': int(os.getenv('DB_PORT', str(DB_PORT_DEFAULT))),
    'user': os.getenv('DB_USER', 'postgres' if IS_POSTGRES else 'root'),
    'password': os.getenv('DB_PASSWORD', 'postgres' if IS_POSTGRES else ''),
    'database': os.getenv('DB_NAME', 'csbaby'),
}

if IS_MYSQL:
    DB_CONFIG['charset'] = 'utf8mb4'
    DB_CONFIG['connect_timeout'] = 10
    # 阿里云 RDS MySQL 默认不需要 SSL
    DB_CONFIG['ssl_disabled'] = True
else:
    DB_CONFIG['connect_timeout'] = 10

# ========== PostgreSQL 连接池 ==========
_pg_pool = None


def _get_pg_pool():
    global _pg_pool
    if _pg_pool is None:
        _pg_pool = _pool.ThreadedConnectionPool(
            minconn=1, maxconn=10,
            database=DB_CONFIG['database'],
            user=DB_CONFIG['user'],
            password=DB_CONFIG['password'],
            host=DB_CONFIG['host'],
            port=DB_CONFIG['port'],
            connect_timeout=DB_CONFIG['connect_timeout'],
        )
    return _pg_pool


# ========== MySQL 连接池 ==========
_mysql_pool = queue.Queue(maxsize=20)
_pool_lock = threading.Lock()


def _create_mysql_conn():
    return _driver.connect(**DB_CONFIG)


def _get_mysql_conn():
    try:
        conn = _mysql_pool.get_nowait()
        conn.ping(reconnect=True)
        return conn
    except queue.Empty:
        return _create_mysql_conn()


def _return_mysql_conn(conn):
    try:
        _mysql_pool.put_nowait(conn)
    except queue.Full:
        conn.close()


# ========== 统一连接接口 ==========

def get_connection():
    """获取数据库连接"""
    if IS_MYSQL:
        return _get_mysql_conn()
    else:
        return _get_pg_pool().getconn()


def return_connection(conn):
    """归还连接"""
    if IS_MYSQL:
        _return_mysql_conn(conn)
    else:
        _get_pg_pool().putconn(conn)


def direct_connection():
    """创建一个独立连接（不经过池，用于写入密集型操作）"""
    if IS_MYSQL:
        return _driver.connect(**DB_CONFIG)
    else:
        return _driver.connect(**DB_CONFIG)


# ========== SQL 辅助函数 ==========

def upsert_clause(conflict_cols: str | list[str]) -> str:
    """返回 upsert 子句

    PostgreSQL: ON CONFLICT (col) DO UPDATE SET
    MySQL:      ON DUPLICATE KEY UPDATE
    """
    if IS_POSTGRES:
        if isinstance(conflict_cols, list):
            cols = ', '.join(conflict_cols)
        else:
            cols = conflict_cols
        return f"ON CONFLICT ({cols}) DO UPDATE SET"
    else:
        return "ON DUPLICATE KEY UPDATE"


def excluded_ref(col: str) -> str:
    """返回冲突引用语法

    PostgreSQL: EXCLUDED.col
    MySQL:      VALUES(col)
    """
    if IS_POSTGRES:
        return f"EXCLUDED.{col}"
    else:
        return f"VALUES({col})"


def like_op() -> str:
    """返回 LIKE 运算符

    PostgreSQL: ILIKE （大小写不敏感）
    MySQL:      LIKE （utf8mb4_unicode_ci 下默认大小写不敏感）
    """
    if IS_POSTGRES:
        return "ILIKE"
    else:
        return "LIKE"


def bool_true() -> str:
    """返回布尔真值"""
    return "TRUE" if IS_POSTGRES else "1"


def bool_false() -> str:
    """返回布尔假值"""
    return "FALSE" if IS_POSTGRES else "0"


def now_ms_expr() -> str:
    """返回毫秒时间戳表达式

    PostgreSQL: (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
    MySQL:      UNIX_TIMESTAMP() * 1000
    """
    if IS_POSTGRES:
        return "(EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT"
    else:
        return "UNIX_TIMESTAMP() * 1000"


def engine_suffix() -> str:
    """返回建表引擎后缀（仅 MySQL 需要）"""
    if IS_MYSQL:
        return " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
    return ""


def serial_type() -> str:
    """返回自增主键类型

    PostgreSQL: SERIAL
    MySQL:      INT AUTO_INCREMENT
    """
    return "INT AUTO_INCREMENT" if IS_MYSQL else "SERIAL"


def boolean_type() -> str:
    """返回布尔类型

    PostgreSQL: BOOLEAN
    MySQL:      TINYINT(1)
    """
    return "TINYINT(1)" if IS_MYSQL else "BOOLEAN"


# ========== 查询执行接口 ==========

# PostgreSQL 布尔值兼容：deleted = 0 → deleted = FALSE
def _normalize_sql(sql: str) -> str:
    """规范化 SQL 中的布尔值比较，兼容 PG BOOLEAN / MySQL TINYINT"""
    if IS_POSTGRES:
        # PG 的 BOOLEAN 不支持 = 0/1，需要 = FALSE/TRUE
        sql = sql.replace('= 0', "= FALSE")
        sql = sql.replace('= 1', "= TRUE")
    return sql


def execute_query(sql, params=None, fetch='all'):
    """执行查询，返回行"""
    sql = _normalize_sql(sql)
    conn = get_connection()
    try:
        cursor = conn.cursor()
        cursor.execute(sql, params or ())
        if fetch == 'one':
            result = cursor.fetchone()
        else:
            result = cursor.fetchall()
        conn.commit()
        return result
    except Exception as e:
        if not IS_MYSQL:
            conn.rollback()
        raise e
    finally:
        return_connection(conn)


def execute_update(sql, params=None):
    """执行单条更新/插入/删除"""
    sql = _normalize_sql(sql)
    conn = direct_connection()
    try:
        cursor = conn.cursor()
        cursor.execute(sql, params or ())
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        conn.rollback()
        raise e
    finally:
        conn.close()


def execute_batch(statements):
    """批量执行多条语句"""
    if not statements:
        return 0
    conn = get_connection()
    total = 0
    try:
        cursor = conn.cursor()
        for sql, params in statements:
            try:
                cursor.execute(_normalize_sql(sql), params or ())
                total += cursor.rowcount
            except Exception as inner_e:
                logger.error(f"batch execute error: {inner_e}")
                raise
        conn.commit()
        return total
    except Exception as e:
        conn.rollback()
        raise e
    finally:
        return_connection(conn)


# ========== 建表语句 ==========

# MySQL 专用建表 SQL
_MYSQL_TABLES = [
    # 1. users
    """CREATE TABLE IF NOT EXISTS users (
        id VARCHAR(64) PRIMARY KEY, email VARCHAR(255) UNIQUE NOT NULL,
        password_hash VARCHAR(255) NOT NULL, display_name VARCHAR(255),
        tenant_id VARCHAR(64) NOT NULL,
        created_at BIGINT NOT NULL DEFAULT (UNIX_TIMESTAMP() * 1000),
        updated_at BIGINT, deleted TINYINT(1) DEFAULT 0,
        INDEX idx_users_email (email), INDEX idx_users_tenant (tenant_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    # 2. keyword_rules
    """CREATE TABLE IF NOT EXISTS keyword_rules (
        id VARCHAR(64) PRIMARY KEY, keyword VARCHAR(500), match_type VARCHAR(50),
        reply_template TEXT, category VARCHAR(100), target_type VARCHAR(50),
        target_names_json TEXT, priority INT DEFAULT 0, enabled TINYINT(1) DEFAULT 1,
        created_at BIGINT, updated_at BIGINT, tenant_id VARCHAR(64) NOT NULL,
        sync_version BIGINT DEFAULT 0, deleted TINYINT(1) DEFAULT 0,
        keyword_hash VARCHAR(64),
        UNIQUE KEY uk_tenant_keyword (tenant_id, keyword_hash),
        INDEX idx_keyword_tenant (tenant_id), INDEX idx_keyword_version (sync_version)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    # 3-10 其他表...
    """CREATE TABLE IF NOT EXISTS ai_model_configs (
        id VARCHAR(64) PRIMARY KEY, model_type VARCHAR(50), model_name VARCHAR(200),
        api_key TEXT, api_endpoint TEXT, temperature DOUBLE DEFAULT 0.7,
        max_tokens INT DEFAULT 1000, is_default TINYINT(1) DEFAULT 0,
        is_enabled TINYINT(1) DEFAULT 1, monthly_cost DOUBLE DEFAULT 0,
        last_used BIGINT, created_at BIGINT, tenant_id VARCHAR(64) NOT NULL,
        sync_version BIGINT DEFAULT 0, deleted TINYINT(1) DEFAULT 0,
        INDEX idx_ai_model_tenant (tenant_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS user_style_profiles (
        id VARCHAR(64) PRIMARY KEY, user_id VARCHAR(64) NOT NULL,
        formality_level DOUBLE DEFAULT 0.5, enthusiasm_level DOUBLE DEFAULT 0.5,
        professionalism_level DOUBLE DEFAULT 0.5, word_count_preference INT DEFAULT 50,
        common_phrases TEXT, avoid_phrases TEXT, learning_samples TEXT,
        accuracy_score DOUBLE DEFAULT 0.5, last_trained BIGINT, created_at BIGINT,
        tenant_id VARCHAR(64) NOT NULL, sync_version BIGINT DEFAULT 0,
        deleted TINYINT(1) DEFAULT 0, INDEX idx_profile_tenant (tenant_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS app_configs (
        package_name VARCHAR(255) PRIMARY KEY, app_name VARCHAR(200), icon_uri TEXT,
        is_monitored TINYINT(1) DEFAULT 1, created_at BIGINT, last_used BIGINT,
        tenant_id VARCHAR(64) NOT NULL, sync_version BIGINT DEFAULT 0,
        deleted TINYINT(1) DEFAULT 0, INDEX idx_app_tenant (tenant_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS scenarios (
        id VARCHAR(64) PRIMARY KEY, name VARCHAR(200), type VARCHAR(50),
        target_id VARCHAR(64), description TEXT, created_at BIGINT,
        tenant_id VARCHAR(64) NOT NULL, sync_version BIGINT DEFAULT 0,
        deleted TINYINT(1) DEFAULT 0, INDEX idx_scenario_tenant (tenant_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS reply_history (
        id VARCHAR(64) PRIMARY KEY, source_app VARCHAR(255), original_message TEXT,
        generated_reply TEXT, final_reply TEXT, rule_matched_id VARCHAR(64),
        model_used_id VARCHAR(64), style_applied TINYINT(1) DEFAULT 0,
        send_time BIGINT, modified TINYINT(1) DEFAULT 0,
        tenant_id VARCHAR(64) NOT NULL, sync_version BIGINT DEFAULT 0,
        deleted TINYINT(1) DEFAULT 0, INDEX idx_reply_tenant (tenant_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS message_blacklist (
        id VARCHAR(64) PRIMARY KEY, type VARCHAR(50), value TEXT, description TEXT,
        package_name VARCHAR(255), created_at BIGINT, is_enabled TINYINT(1) DEFAULT 1,
        tenant_id VARCHAR(64) NOT NULL, sync_version BIGINT DEFAULT 0,
        deleted TINYINT(1) DEFAULT 0, INDEX idx_blacklist_tenant (tenant_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS sync_checkpoints (
        tenant_id VARCHAR(64) PRIMARY KEY, last_sync_version BIGINT DEFAULT 0,
        last_sync_time BIGINT, updated_at BIGINT, is_syncing TINYINT(1) DEFAULT 0,
        last_error TEXT, device_info TEXT, created_at BIGINT
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS backup_records (
        id INT AUTO_INCREMENT PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL,
        device_name VARCHAR(255), app_version VARCHAR(50), data_json LONGTEXT,
        data_size BIGINT, checksum VARCHAR(64), version VARCHAR(20) DEFAULT '1.0',
        backup_type VARCHAR(20) DEFAULT 'manual',
        created_at BIGINT DEFAULT (UNIX_TIMESTAMP() * 1000),
        deleted TINYINT(1) DEFAULT 0, INDEX idx_backup_tenant (tenant_id),
        INDEX idx_backup_created (created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
]

# PostgreSQL 专用建表 SQL
_PG_TABLES = [
    # 1. users
    """CREATE TABLE IF NOT EXISTS users (
        id TEXT PRIMARY KEY, email TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL,
        display_name TEXT, tenant_id TEXT NOT NULL,
        created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
        updated_at BIGINT, deleted BOOLEAN DEFAULT FALSE
    )""",

    # 2. keyword_rules
    """CREATE TABLE IF NOT EXISTS keyword_rules (
        id TEXT PRIMARY KEY, keyword TEXT, match_type TEXT, reply_template TEXT,
        category TEXT, target_type TEXT, target_names_json TEXT, priority INT DEFAULT 0,
        enabled BOOLEAN DEFAULT TRUE, created_at BIGINT, updated_at BIGINT,
        tenant_id TEXT NOT NULL, sync_version BIGINT DEFAULT 0, deleted BOOLEAN DEFAULT FALSE,
        keyword_hash TEXT
    )""",

    # 3-10
    """CREATE TABLE IF NOT EXISTS ai_model_configs (
        id TEXT PRIMARY KEY, model_type TEXT, model_name TEXT, api_key TEXT,
        api_endpoint TEXT, temperature REAL DEFAULT 0.7, max_tokens INT DEFAULT 1000,
        is_default BOOLEAN DEFAULT FALSE, is_enabled BOOLEAN DEFAULT TRUE,
        monthly_cost REAL DEFAULT 0, last_used BIGINT, created_at BIGINT,
        tenant_id TEXT NOT NULL, sync_version BIGINT DEFAULT 0, deleted BOOLEAN DEFAULT FALSE
    )""",

    """CREATE TABLE IF NOT EXISTS user_style_profiles (
        id TEXT PRIMARY KEY, user_id TEXT NOT NULL,
        formality_level REAL DEFAULT 0.5, enthusiasm_level REAL DEFAULT 0.5,
        professionalism_level REAL DEFAULT 0.5, word_count_preference INT DEFAULT 50,
        common_phrases TEXT DEFAULT '[]', avoid_phrases TEXT DEFAULT '[]',
        learning_samples TEXT DEFAULT '[]', accuracy_score REAL DEFAULT 0.5,
        last_trained BIGINT, created_at BIGINT, tenant_id TEXT NOT NULL,
        sync_version BIGINT DEFAULT 0, deleted BOOLEAN DEFAULT FALSE
    )""",

    """CREATE TABLE IF NOT EXISTS app_configs (
        package_name TEXT PRIMARY KEY, app_name TEXT, icon_uri TEXT,
        is_monitored BOOLEAN DEFAULT TRUE, created_at BIGINT, last_used BIGINT,
        tenant_id TEXT NOT NULL, sync_version BIGINT DEFAULT 0, deleted BOOLEAN DEFAULT FALSE
    )""",

    """CREATE TABLE IF NOT EXISTS scenarios (
        id TEXT PRIMARY KEY, name TEXT, type TEXT, target_id TEXT, description TEXT,
        created_at BIGINT, tenant_id TEXT NOT NULL, sync_version BIGINT DEFAULT 0,
        deleted BOOLEAN DEFAULT FALSE
    )""",

    """CREATE TABLE IF NOT EXISTS reply_history (
        id TEXT PRIMARY KEY, source_app TEXT, original_message TEXT,
        generated_reply TEXT, final_reply TEXT, rule_matched_id TEXT,
        model_used_id TEXT, style_applied BOOLEAN DEFAULT FALSE, send_time BIGINT,
        modified BOOLEAN DEFAULT FALSE, tenant_id TEXT NOT NULL,
        sync_version BIGINT DEFAULT 0, deleted BOOLEAN DEFAULT FALSE
    )""",

    """CREATE TABLE IF NOT EXISTS message_blacklist (
        id TEXT PRIMARY KEY, type TEXT, value TEXT, description TEXT,
        package_name TEXT, created_at BIGINT, is_enabled BOOLEAN DEFAULT TRUE,
        tenant_id TEXT NOT NULL, sync_version BIGINT DEFAULT 0, deleted BOOLEAN DEFAULT FALSE
    )""",

    """CREATE TABLE IF NOT EXISTS sync_checkpoints (
        tenant_id TEXT PRIMARY KEY, last_sync_version BIGINT DEFAULT 0,
        last_sync_time BIGINT, updated_at BIGINT, is_syncing BOOLEAN DEFAULT FALSE,
        last_error TEXT, device_info TEXT, created_at BIGINT
    )""",

    """CREATE TABLE IF NOT EXISTS backup_records (
        id SERIAL PRIMARY KEY, tenant_id TEXT NOT NULL, device_name TEXT,
        app_version TEXT, data_json TEXT, data_size BIGINT, checksum TEXT,
        version TEXT DEFAULT '1.0', backup_type TEXT DEFAULT 'manual',
        created_at BIGINT DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
        deleted BOOLEAN DEFAULT FALSE
    )""",
]


def init_schema():
    """初始化数据库表结构（根据 DB_TYPE 自动选择对应 DDL）"""
    conn = get_connection()
    try:
        cursor = conn.cursor()
        tables = _MYSQL_TABLES if IS_MYSQL else _PG_TABLES
        for sql in tables:
            try:
                cursor.execute(sql)
                logger.info(f"Table created/verified: {sql.split()[2]}")
            except Exception as e:
                logger.warning(f"Table creation warning: {e}")

        # PostgreSQL 额外索引
        if IS_POSTGRES:
            pg_indexes = [
                "CREATE INDEX IF NOT EXISTS idx_users_email ON users(email)",
                "CREATE INDEX IF NOT EXISTS idx_users_tenant ON users(tenant_id)",
                "CREATE INDEX IF NOT EXISTS idx_keyword_tenant ON keyword_rules(tenant_id)",
                "CREATE INDEX IF NOT EXISTS idx_keyword_version ON keyword_rules(sync_version)",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_keyword_hash ON keyword_rules(tenant_id, keyword_hash)",
                "CREATE INDEX IF NOT EXISTS idx_ai_model_tenant ON ai_model_configs(tenant_id)",
                "CREATE INDEX IF NOT EXISTS idx_profile_tenant ON user_style_profiles(tenant_id)",
                "CREATE INDEX IF NOT EXISTS idx_app_tenant ON app_configs(tenant_id)",
                "CREATE INDEX IF NOT EXISTS idx_scenario_tenant ON scenarios(tenant_id)",
                "CREATE INDEX IF NOT EXISTS idx_reply_tenant ON reply_history(tenant_id)",
                "CREATE INDEX IF NOT EXISTS idx_blacklist_tenant ON message_blacklist(tenant_id)",
                "CREATE INDEX IF NOT EXISTS idx_backup_tenant ON backup_records(tenant_id)",
                "CREATE INDEX IF NOT EXISTS idx_backup_created ON backup_records(created_at)",
            ]
            for sql in pg_indexes:
                try:
                    cursor.execute(sql)
                except Exception:
                    pass

        conn.commit()
        logger.info(f"All {len(tables)} tables created successfully (DB_TYPE={DB_TYPE})")
    except Exception as e:
        logger.error(f"Error creating tables: {e}")
        conn.rollback()
        raise e
    finally:
        return_connection(conn)
