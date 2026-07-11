"""
MySQL RDS 数据库连接层（替代原有 SQLite 实现）

提供与 sqlite3.Connection 兼容的接口，使上层代码无需修改即可从 SQLite 迁移到 MySQL。
自动处理：
  - 表名 `api_` 前缀（SQL 中仍用原表名，例如 `users` → `api_users`）
  - `?` 占位符 → `%s`（pymysql 兼容）
  - `INSERT OR IGNORE` → `INSERT IGNORE`
  - `INSERT OR REPLACE` → `REPLACE`
  - `PRAGMA table_info(x)` → `SHOW COLUMNS FROM x`
  - `PRAGMA sqlite_master` → `information_schema`
  - `PRAGMA foreign_keys=ON` / `journal_mode=WAL` / `busy_timeout` → 无操作
  - `executescript` → 逐条执行
  - `lastrowid` → 支持
  - sqlite3.Row 行为 → DictCursor
"""
import os
import re
import logging
import pymysql
from pymysql.cursors import Cursor

logger = logging.getLogger(__name__)

# ========== 配置 ==========

# 从环境变量读取 MySQL 连接参数（与 csBaby-server-py 兼容）
DB_URL = os.environ.get("DB_URL", "")
DB_HOST = os.environ.get("DB_HOST", "r8371qiaozhou.mysql.aliyun.com")
DB_PORT = int(os.environ.get("DB_PORT", "3306"))
DB_USER = os.environ.get("DB_USER", "qiaozhou")
DB_PASSWORD = os.environ.get("DB_PASSWORD", "Rds@2026")
DB_NAME = os.environ.get("DB_NAME", "r2346qiaozhou")


# ========== 表名前缀映射 ==========

# 需要加 api_ 前缀的表名列表
API_TABLES = {
    "users", "user_devices", "devices", "keyword_rules", "model_configs",
    "reply_history", "feedback", "optimization_metrics", "blacklist",
    "agent_status", "agent_skills", "routing_config", "sessions",
    "tenant_style_config", "tenant_app_config", "admin_accounts",
    "admin_sessions", "audit_log", "sync_outbox", "sync_outbox_dead",
}

# 不需要前缀的表（sync server 已有表）
SYNC_TABLES = {
    "ai_model_configs", "app_configs", "backup_records",
    "message_blacklist", "scenarios", "sync_checkpoints", "user_style_profiles",
}


# ========== SQL 转换 ==========

def _prefix_table_name(match):
    """为匹配到的表名添加 api_ 前缀（如果是 API 表）。"""
    word = match.group(0)
    # 去除可能的引号/反引号
    stripped = word.strip("`\"'")
    if stripped.lower() in API_TABLES:
        return f"`api_{stripped}`"
    elif stripped.lower() not in SYNC_TABLES:
        # 不在白名单中的表也加前缀（安全兜底）
        return f"`api_{stripped}`"
    return word


# SQL 关键字后跟表名的模式
# 使用捕获组而非 look-behind（Python re 不支持可变宽度 look-behind）
_TABLE_AFTER_KEYWORD = re.compile(
    r'\b(?:FROM|INTO|TABLE|UPDATE|VIEW|ON|JOIN|INDEX\s+ON|RENAME\s+TO)\s+`?(\w+)`?(?:\s|$|\.|,)',
    re.IGNORECASE,
)

# PRAGMA table_info(x) 模式
_PRAGMA_TABLE_INFO = re.compile(r"PRAGMA\s+table_info\(`?(\w+)`?\)", re.IGNORECASE)

# PRAGMA sqlite_master 模式
_PRAGMA_SQLITE_MASTER = re.compile(r"sqlite_master", re.IGNORECASE)

# INSERT OR IGNORE / INSERT OR REPLACE
_INSERT_OR_IGNORE = re.compile(r"\bINSERT\s+OR\s+IGNORE\s+INTO\b", re.IGNORECASE)
_INSERT_OR_REPLACE = re.compile(r"\bINSERT\s+OR\s+REPLACE\s+INTO\b", re.IGNORECASE)

# ALTER TABLE RENAME COLUMN
_ALTER_RENAME_COLUMN = re.compile(
    r"ALTER\s+TABLE\s+`?(\w+)`?\s+RENAME\s+COLUMN\s+`?(\w+)`?\s+TO\s+`?(\w+)`?",
    re.IGNORECASE
)

# CREATE INDEX
_CREATE_INDEX = re.compile(
    r"(CREATE(?:\s+UNIQUE)?\s+INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?\s+ON\s+)`?(\w+)`?",
    re.IGNORECASE
)

# ALTER TABLE ... RENAME TO
_ALTER_RENAME_TO = re.compile(
    r"ALTER\s+TABLE\s+`?(\w+)`?\s+RENAME\s+TO\s+`?(\w+)`?",
    re.IGNORECASE
)


def _transform_sql(sql: str) -> str:
    """将 SQLite 风格的 SQL 转换为 MySQL 兼容的 SQL。"""
    # 1. 处理 PRAGMA table_info
    sql = _PRAGMA_TABLE_INFO.sub(r"SHOW COLUMNS FROM `\1`", sql)

    # 2. 处理 PRAGMA sqlite_master
    if "sqlite_master" in sql.lower():
        sql = sql.replace(
            "sqlite_master",
            "information_schema.tables WHERE table_schema=DATABASE()"
        )

    # 3. INSERT OR IGNORE → INSERT IGNORE
    sql = _INSERT_OR_IGNORE.sub("INSERT IGNORE INTO", sql)

    # 4. INSERT OR REPLACE → REPLACE INTO
    sql = _INSERT_OR_REPLACE.sub("REPLACE INTO", sql)

    # 5. 表名添加 api_ 前缀
    def _add_prefix(m):
        word = m.group(1)
        if word.lower() in API_TABLES:
            prefix = f"`api_{word}`"
            return m.group(0).replace(word, prefix, 1)
        return m.group(0)

    # 对 FROM/INTO/TABLE/UPDATE/JOIN/ON 等关键字后的表名添加前缀
    sql = _TABLE_AFTER_KEYWORD.sub(_add_prefix, sql)

    # 处理 CREATE TABLE 语句
    sql = re.sub(
        r"(CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?)`?(\w+)`?",
        lambda m: f"{m.group(1)}`api_{m.group(2)}`" if m.group(2).lower() in API_TABLES else m.group(0),
        sql,
        flags=re.IGNORECASE
    )

    # 处理 CREATE INDEX
    sql = _CREATE_INDEX.sub(
        lambda m: f"{m.group(1)}`api_{m.group(3)}`" if m.group(3).lower() in API_TABLES else m.group(0),
        sql
    )

    # 处理 ALTER TABLE ... RENAME COLUMN
    sql = _ALTER_RENAME_COLUMN.sub(
        lambda m: f"ALTER TABLE `api_{m.group(1)}` CHANGE COLUMN `{m.group(2)}` `{m.group(3)}`"
        if m.group(1).lower() in API_TABLES else m.group(0),
        sql
    )

    # 处理 ALTER TABLE ... RENAME TO
    sql = _ALTER_RENAME_TO.sub(
        lambda m: f"ALTER TABLE `api_{m.group(1)}` RENAME TO `api_{m.group(2)}`"
        if m.group(1).lower() in API_TABLES else m.group(0),
        sql
    )

    # 处理 DROP INDEX
    sql = re.sub(
        r"DROP\s+INDEX\s+(?:IF\s+EXISTS\s+)?`?(\w+)`?",
        lambda m: f"DROP INDEX IF EXISTS `{m.group(1)}`",
        sql,
        flags=re.IGNORECASE
    )

    # 6. 转换 ? 占位符为 %s
    # 跳过已转换过的 %s
    if "?" in sql:
        sql = sql.replace("?", "%s")

    # 7. 处理 INTEGER 类型（MySQL 用 INT）
    sql = re.sub(r'\bINTEGER\b', 'INT', sql)

    # 8. 处理 TEXT DEFAULT 问题（MySQL 不允许 TEXT 有默认值）
    sql = re.sub(r'\bTEXT\s+DEFAULT\s+\'[^\']*\'', 'TEXT', sql)

    # 9. 处理 REAL 类型（MySQL 用 DOUBLE）
    sql = re.sub(r'\bREAL\b', 'DOUBLE', sql)

    # 10. 处理 AUTOINCREMENT（MySQL 用 AUTO_INCREMENT）
    sql = re.sub(r'\bAUTOINCREMENT\b', 'AUTO_INCREMENT', sql)

    return sql


# ========== MySQL 连接包装器 ==========

class Row(dict):
    """兼容 sqlite3.Row 的字典对象，支持 row[0] 和 row["col"] 两种访问方式。"""
    def __getitem__(self, key):
        if isinstance(key, (int,)):
            values = list(super().values())
            if key < len(values):
                return values[key]
            raise KeyError(key)
        return super().__getitem__(key)

    def __getattr__(self, name):
        try:
            return self[name]
        except KeyError:
            raise AttributeError(name)


class RowCursor(pymysql.cursors.Cursor):
    """自定义游标，返回 Row 对象（支持 dict 和 index 访问，兼容 sqlite3.Row）。"""
    def _row(self, data):
        if data is None:
            return None
        cols = [d[0] for d in self.description] if self.description else []
        return Row(zip(cols, data))

    def fetchone(self):
        row = super().fetchone()
        return self._row(row)

    def fetchall(self):
        rows = super().fetchall()
        return [self._row(r) for r in rows]


class MySQLConnection:
    """包装 pymysql 连接，提供与 sqlite3.Connection 兼容的接口。"""

    def __init__(self, conn):
        self._conn = conn
        self._cursor = conn.cursor(RowCursor)
        self._last_cursor = None
        self.row_factory = None  # 兼容属性

    def execute(self, sql, parameters=None):
        """执行 SQL，返回兼容的游标对象。"""
        transformed = _transform_sql(sql)
        # 记录执行的SQL（调试用）
        logger.debug("SQL: %s | Params: %s", transformed[:150], parameters)
        try:
            if parameters is not None:
                if isinstance(parameters, (list, tuple)):
                    self._cursor.execute(transformed, parameters)
                else:
                    self._cursor.execute(transformed, (parameters,))
            else:
                self._cursor.execute(transformed)
            self._last_cursor = self._cursor
            return self._cursor
        except Exception as e:
            logger.error("MySQL execute error: %s\nSQL: %s\nParams: %s", e, transformed[:200], parameters)
            raise

    def executescript(self, sql_script):
        """执行多条 SQL 语句（以 ; 分隔）。"""
        statements = []
        for stmt in sql_script.split(";"):
            stmt = stmt.strip()
            if stmt and not stmt.startswith("--"):
                statements.append(stmt)

        # 移除空语句
        for stmt in statements:
            if stmt:
                transformed = _transform_sql(stmt)
                try:
                    self._cursor.execute(transformed)
                except Exception as e:
                    logger.warning("executescript stmt error: %s\nSQL: %s", e, transformed[:150])
                    # 继续执行后续语句（DDL 批量执行时允许部分失败）

    def commit(self):
        self._conn.commit()

    def close(self):
        try:
            self._cursor.close()
        except Exception:
            pass
        try:
            self._conn.close()
        except Exception:
            pass

    @property
    def lastrowid(self):
        if self._last_cursor:
            return self._last_cursor.lastrowid
        return None


# ========== 公共 API ==========

def get_connection() -> MySQLConnection:
    """获取 MySQL 数据库连接。

    通过环境变量配置连接参数，兼容原有 get_connection() 接口。
    """
    if DB_URL:
        # 使用 URL 格式连接
        kwargs = _parse_db_url(DB_URL)
    else:
        kwargs = dict(
            host=DB_HOST,
            port=DB_PORT,
            user=DB_USER,
            password=DB_PASSWORD,
            database=DB_NAME,
            charset="utf8mb4",
            connect_timeout=10,
            autocommit=False,
        )
        ssl_disabled = os.environ.get("DB_SSL_DISABLED", "true").lower() == "true"
        if ssl_disabled:
            kwargs["ssl_disabled"] = True

    conn = pymysql.connect(**kwargs)
    return MySQLConnection(conn)


def _parse_db_url(url: str) -> dict:
    """解析 mysql:// 格式的 URL 为连接参数字典。"""
    if url.startswith("mysql+pymysql://"):
        url = url[len("mysql+pymysql://"):]
    elif url.startswith("mysql://"):
        url = url[len("mysql://"):]
    else:
        raise ValueError(f"DB_URL must start with mysql://, got: {url[:30]}...")

    creds, rest = url.split("@", 1)
    user, password = creds.split(":", 1)
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

    kwargs = dict(
        host=host, port=port, user=user, password=password,
        database=db or DB_NAME, charset="utf8mb4", connect_timeout=10,
        autocommit=False,
    )
    ssl_disabled = os.environ.get("DB_SSL_DISABLED", "true").lower() == "true"
    if ssl_disabled:
        kwargs["ssl_disabled"] = True
    return kwargs


def init_db():
    """初始化数据库表结构（MySQL 版）。

    如果表已存在则跳过，幂等操作。
    """
    db = get_connection()
    try:
        db.executescript("""
            CREATE TABLE IF NOT EXISTS users (
                id VARCHAR(64) PRIMARY KEY,
                phone VARCHAR(20) NOT NULL,
                password_hash VARCHAR(128) NOT NULL,
                salt VARCHAR(64) NOT NULL,
                name VARCHAR(100) DEFAULT '',
                created_at DATETIME DEFAULT NULL,
                email VARCHAR(255) DEFAULT NULL
            );

            CREATE TABLE IF NOT EXISTS user_devices (
                user_id VARCHAR(64) NOT NULL,
                device_id VARCHAR(128) NOT NULL,
                platform VARCHAR(20) DEFAULT 'android',
                device_name VARCHAR(100) DEFAULT '',
                registered_at DATETIME DEFAULT NULL,
                PRIMARY KEY (user_id, device_id)
            );

            CREATE TABLE IF NOT EXISTS devices (
                id VARCHAR(128) PRIMARY KEY,
                token VARCHAR(256) NOT NULL,
                name VARCHAR(100) DEFAULT NULL,
                platform VARCHAR(20) DEFAULT 'android',
                app_version VARCHAR(50) DEFAULT NULL,
                last_heartbeat DATETIME DEFAULT NULL,
                is_active TINYINT DEFAULT 1,
                created_at DATETIME DEFAULT NULL
            );

            CREATE TABLE IF NOT EXISTS keyword_rules (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_id VARCHAR(64) NOT NULL,
                keyword VARCHAR(500) NOT NULL,
                match_type VARCHAR(50) DEFAULT 'CONTAINS',
                reply_template TEXT NOT NULL,
                category VARCHAR(100) DEFAULT '',
                target_type VARCHAR(50) DEFAULT 'ALL',
                target_names TEXT,
                priority INT DEFAULT 0,
                enabled TINYINT DEFAULT 1,
                created_at DATETIME DEFAULT NULL,
                updated_at DATETIME DEFAULT NULL
            );

            CREATE TABLE IF NOT EXISTS model_configs (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_id VARCHAR(64) NOT NULL,
                name VARCHAR(200) NOT NULL,
                model_type VARCHAR(50) NOT NULL,
                model VARCHAR(200) NOT NULL,
                api_key TEXT NOT NULL,
                api_endpoint TEXT DEFAULT NULL,
                temperature DOUBLE DEFAULT 0.7,
                max_tokens INT DEFAULT 2000,
                is_default TINYINT DEFAULT 0,
                enabled TINYINT DEFAULT 1,
                created_at DATETIME DEFAULT NULL,
                updated_at DATETIME DEFAULT NULL
            );

            CREATE TABLE IF NOT EXISTS reply_history (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_id VARCHAR(64) NOT NULL,
                original_message TEXT,
                reply_content TEXT,
                source VARCHAR(20) DEFAULT 'ai',
                model_used VARCHAR(100) DEFAULT NULL,
                confidence DOUBLE DEFAULT NULL,
                response_time_ms INT DEFAULT NULL,
                platform VARCHAR(50) DEFAULT NULL,
                customer_name VARCHAR(100) DEFAULT NULL,
                house_name VARCHAR(200) DEFAULT NULL,
                created_at DATETIME DEFAULT NULL
            );

            CREATE TABLE IF NOT EXISTS feedback (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_id VARCHAR(64) NOT NULL,
                reply_history_id INT DEFAULT NULL,
                action VARCHAR(50) NOT NULL,
                modified_text TEXT,
                rating INT DEFAULT NULL,
                comment TEXT,
                created_at DATETIME DEFAULT NULL
            );

            CREATE TABLE IF NOT EXISTS optimization_metrics (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_id VARCHAR(64) NOT NULL,
                date VARCHAR(20) NOT NULL,
                total_generated INT DEFAULT 0,
                total_accepted INT DEFAULT 0,
                total_modified INT DEFAULT 0,
                total_rejected INT DEFAULT 0,
                avg_confidence DOUBLE DEFAULT 0,
                avg_response_time_ms INT DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS blacklist (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_id VARCHAR(64) NOT NULL,
                type VARCHAR(50) DEFAULT 'KEYWORD',
                value VARCHAR(500) NOT NULL,
                description TEXT,
                package_name VARCHAR(255) DEFAULT NULL,
                is_enabled TINYINT DEFAULT 1,
                created_at DATETIME DEFAULT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_rules_user ON keyword_rules(user_id);
            CREATE INDEX IF NOT EXISTS idx_rules_keyword ON keyword_rules(keyword);
            CREATE INDEX IF NOT EXISTS idx_history_user_created ON reply_history(user_id, created_at DESC);
            CREATE INDEX IF NOT EXISTS idx_feedback_user ON feedback(user_id);
            CREATE INDEX IF NOT EXISTS idx_feedback_history ON feedback(reply_history_id);
            CREATE INDEX IF NOT EXISTS idx_metrics_user_date ON optimization_metrics(user_id, date);
            CREATE INDEX IF NOT EXISTS idx_models_user_enabled ON model_configs(user_id, enabled);
            CREATE INDEX IF NOT EXISTS idx_devices_heartbeat ON devices(last_heartbeat DESC);
            CREATE INDEX IF NOT EXISTS idx_history_source ON reply_history(source);
            CREATE INDEX IF NOT EXISTS idx_feedback_action ON feedback(action);
            CREATE INDEX IF NOT EXISTS idx_blacklist_user ON blacklist(user_id);
            CREATE INDEX IF NOT EXISTS idx_user_devices_user ON user_devices(user_id);

            CREATE TABLE IF NOT EXISTS agent_status (
                phone VARCHAR(20) PRIMARY KEY,
                agent_name VARCHAR(100) DEFAULT '',
                status VARCHAR(20) DEFAULT 'online',
                current_load INT DEFAULT 0,
                max_concurrent INT DEFAULT 5,
                user_id VARCHAR(64) DEFAULT '',
                updated_at DATETIME DEFAULT NULL
            );

            CREATE TABLE IF NOT EXISTS agent_skills (
                id INT AUTO_INCREMENT PRIMARY KEY,
                agent_phone VARCHAR(20) NOT NULL,
                skill_tag VARCHAR(100) NOT NULL,
                proficiency INT DEFAULT 5
            );

            CREATE TABLE IF NOT EXISTS routing_config (
                `key` VARCHAR(100) PRIMARY KEY,
                `value` TEXT NOT NULL,
                updated_at DATETIME DEFAULT NULL
            );

            CREATE TABLE IF NOT EXISTS sessions (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_id VARCHAR(64) NOT NULL,
                customer_name VARCHAR(100) DEFAULT '',
                customer_phone VARCHAR(20) DEFAULT '',
                platform VARCHAR(50) DEFAULT '',
                assigned_agent_phone VARCHAR(20) DEFAULT '',
                status VARCHAR(20) DEFAULT 'pending',
                priority INT DEFAULT 0,
                skill_required VARCHAR(100) DEFAULT '',
                created_at DATETIME DEFAULT NULL,
                updated_at DATETIME DEFAULT NULL,
                closed_at DATETIME DEFAULT NULL
            );

            CREATE TABLE IF NOT EXISTS tenant_style_config (
                user_id VARCHAR(64) PRIMARY KEY,
                theme VARCHAR(20) DEFAULT 'light',
                primary_color VARCHAR(20) DEFAULT '#1976D2',
                accent_color VARCHAR(20) DEFAULT '#FF4081',
                font_size VARCHAR(20) DEFAULT 'medium',
                bubble_style VARCHAR(20) DEFAULT 'rounded',
                avatar_enabled TINYINT DEFAULT 1,
                show_timestamp TINYINT DEFAULT 1,
                send_sound TINYINT DEFAULT 1,
                custom_css TEXT,
                updated_at DATETIME DEFAULT NULL
            );

            CREATE TABLE IF NOT EXISTS tenant_app_config (
                user_id VARCHAR(64) PRIMARY KEY,
                app_name VARCHAR(100) DEFAULT '客服小秘',
                welcome_message TEXT,
                offline_message TEXT,
                auto_reply_enabled TINYINT DEFAULT 1,
                notification_enabled TINYINT DEFAULT 1,
                voice_enabled TINYINT DEFAULT 0,
                language VARCHAR(10) DEFAULT 'zh-CN',
                session_timeout INT DEFAULT 300,
                max_queue_size INT DEFAULT 50,
                file_upload_enabled TINYINT DEFAULT 1,
                updated_at DATETIME DEFAULT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_agent_skills_phone ON agent_skills(agent_phone);
            CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
            CREATE INDEX IF NOT EXISTS idx_sessions_status ON sessions(status);

            CREATE TABLE IF NOT EXISTS admin_sessions (
                token VARCHAR(128) PRIMARY KEY,
                phone VARCHAR(20) NOT NULL,
                created_at DATETIME DEFAULT NULL,
                expires_at DATETIME NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_admin_sessions_phone ON admin_sessions(phone);
            CREATE INDEX IF NOT EXISTS idx_admin_sessions_expires ON admin_sessions(expires_at);

            CREATE TABLE IF NOT EXISTS admin_accounts (
                phone VARCHAR(20) PRIMARY KEY,
                password_hash VARCHAR(128) NOT NULL,
                is_active TINYINT DEFAULT 1,
                created_at DATETIME DEFAULT NULL
            );

            CREATE TABLE IF NOT EXISTS audit_log (
                id INT AUTO_INCREMENT PRIMARY KEY,
                admin_phone VARCHAR(20) NOT NULL,
                action VARCHAR(100) NOT NULL,
                target_type VARCHAR(50) DEFAULT '',
                target_id VARCHAR(100) DEFAULT '',
                detail TEXT,
                created_at DATETIME DEFAULT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log(created_at DESC);

            CREATE TABLE IF NOT EXISTS sync_outbox (
                id INT AUTO_INCREMENT PRIMARY KEY,
                table_name VARCHAR(100) NOT NULL,
                op VARCHAR(20) NOT NULL,
                row_id INT DEFAULT NULL,
                payload TEXT NOT NULL,
                attempts INT DEFAULT 0,
                last_error TEXT DEFAULT NULL,
                next_retry_at DATETIME NOT NULL,
                created_at DATETIME DEFAULT NULL,
                updated_at DATETIME DEFAULT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_outbox_next_retry ON sync_outbox(next_retry_at);

            CREATE TABLE IF NOT EXISTS sync_outbox_dead (
                id INT AUTO_INCREMENT PRIMARY KEY,
                table_name VARCHAR(100) NOT NULL,
                op VARCHAR(20) NOT NULL,
                row_id INT DEFAULT NULL,
                payload TEXT NOT NULL,
                attempts INT DEFAULT NULL,
                last_error TEXT DEFAULT NULL,
                created_at DATETIME DEFAULT NULL,
                moved_at DATETIME DEFAULT NULL
            );
        """)
        db.commit()
        logger.info("Database tables initialized successfully")
    except Exception as e:
        logger.error("Database init error: %s", e)
        raise
    finally:
        db.close()