import sqlite3
import os


DATABASE_PATH = os.environ.get("DATABASE_PATH", "csBaby.db")


def get_connection() -> sqlite3.Connection:
    db = sqlite3.connect(DATABASE_PATH, timeout=10)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA journal_mode=WAL")
    db.execute("PRAGMA foreign_keys=ON")
    db.execute("PRAGMA busy_timeout=3000")
    return db


def _migrate_device_to_user(db: sqlite3.Connection):
    """Migrate existing device-based schema to user-based tenant isolation."""
    # Check if users table already exists
    row = db.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='users'"
    ).fetchone()
    if row:
        return  # Already migrated

    # Create users table
    db.execute("""
        CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            phone TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            salt TEXT NOT NULL,
            name TEXT DEFAULT '',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    """)

    # Create user_devices table (multi-device support per user)
    db.execute("""
        CREATE TABLE IF NOT EXISTS user_devices (
            user_id TEXT NOT NULL,
            device_id TEXT NOT NULL,
            platform TEXT DEFAULT 'android',
            device_name TEXT DEFAULT '',
            registered_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (user_id, device_id),
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )
    """)

    # Migrate existing data: for each device, create a user and link them
    devices = db.execute("SELECT id, name, platform FROM devices").fetchall()
    for dev in devices:
        device_id = dev["id"]
        # Skip system entries
        if device_id == "_global":
            continue
        # Create user from device (backward compatibility)
        phone = f"user_{device_id[:8]}"
        password_hash = "MIGRATED"
        salt = "MIGRATED"
        db.execute(
            "INSERT OR IGNORE INTO users (id, phone, password_hash, salt, name) VALUES (?, ?, ?, ?, ?)",
            (device_id, phone, password_hash, salt, dev["name"] or f"User_{device_id[:8]}")
        )
        db.execute(
            "INSERT OR IGNORE INTO user_devices (user_id, device_id, platform, device_name) VALUES (?, ?, ?, ?)",
            (device_id, device_id, dev["platform"] or "android", dev["name"] or "")
        )

    # Now migrate all business tables: rename device_id to user_id
    # We use ALTER TABLE RENAME COLUMN (SQLite 3.25+)
    tables_to_migrate = [
        "keyword_rules", "model_configs", "reply_history", "feedback",
        "optimization_metrics", "blacklist"
    ]
    for table in tables_to_migrate:
        # Check if table exists and has device_id column
        cols = db.execute(f"PRAGMA table_info({table})").fetchall()
        col_names = [c["name"] for c in cols]
        if "device_id" in col_names:
            db.execute(f"ALTER TABLE {table} RENAME COLUMN device_id TO user_id")

    # Migrate tenant_style_config and tenant_app_config
    for table in ["tenant_style_config", "tenant_app_config"]:
        cols = db.execute(f"PRAGMA table_info({table})").fetchall()
        col_names = [c["name"] for c in cols]
        if "device_id" in col_names:
            db.execute(f"ALTER TABLE {table} RENAME COLUMN device_id TO user_id")

    # Update foreign key references in business tables
    # SQLite doesn't support ALTER CONSTRAINT, so we rebuild tables if needed
    # For now, the renamed columns work since we already migrated device_ids to user_ids

    # Add user_id column to sessions table (currently uses tenant_id)
    cols = db.execute("PRAGMA table_info(sessions)").fetchall()
    col_names = [c["name"] for c in cols]
    if "tenant_id" in col_names and "user_id" not in col_names:
        db.execute("ALTER TABLE sessions RENAME COLUMN tenant_id TO user_id")

    # Add user_id column to agent_status
    cols = db.execute("PRAGMA table_info(agent_status)").fetchall()
    col_names = [c["name"] for c in cols]
    if "tenant_id" in col_names and "user_id" not in col_names:
        db.execute("ALTER TABLE agent_status RENAME COLUMN tenant_id TO user_id")

    # Update indexes
    db.execute("DROP INDEX IF EXISTS idx_rules_device")
    db.execute("CREATE INDEX IF NOT EXISTS idx_rules_user ON keyword_rules(user_id)")
    db.execute("DROP INDEX IF EXISTS idx_history_device_created")
    db.execute("CREATE INDEX IF NOT EXISTS idx_history_user_created ON reply_history(user_id, created_at DESC)")
    db.execute("DROP INDEX IF EXISTS idx_feedback_device")
    db.execute("CREATE INDEX IF NOT EXISTS idx_feedback_user ON feedback(user_id)")
    db.execute("DROP INDEX IF EXISTS idx_metrics_device_date")
    db.execute("CREATE INDEX IF NOT EXISTS idx_metrics_user_date ON optimization_metrics(user_id, date)")
    db.execute("DROP INDEX IF EXISTS idx_models_device_enabled")
    db.execute("CREATE INDEX IF NOT EXISTS idx_models_user_enabled ON model_configs(user_id, enabled)")
    db.execute("DROP INDEX IF EXISTS idx_sessions_tenant")
    db.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id)")
    db.execute("CREATE INDEX IF NOT EXISTS idx_user_devices_user ON user_devices(user_id)")


def init_db():
    db = get_connection()
    db.executescript("""
        CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            phone TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            salt TEXT NOT NULL,
            name TEXT DEFAULT '',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS user_devices (
            user_id TEXT NOT NULL,
            device_id TEXT NOT NULL,
            platform TEXT DEFAULT 'android',
            device_name TEXT DEFAULT '',
            registered_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (user_id, device_id),
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS devices (
            id TEXT PRIMARY KEY,
            token TEXT NOT NULL,
            name TEXT,
            platform TEXT DEFAULT 'android',
            app_version TEXT,
            last_heartbeat DATETIME,
            is_active INTEGER DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS keyword_rules (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            keyword TEXT NOT NULL,
            match_type TEXT DEFAULT 'CONTAINS',
            reply_template TEXT NOT NULL,
            category TEXT DEFAULT '',
            target_type TEXT DEFAULT 'ALL',
            target_names TEXT DEFAULT '[]',
            priority INTEGER DEFAULT 0,
            enabled INTEGER DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS model_configs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            name TEXT NOT NULL,
            model_type TEXT NOT NULL,
            model TEXT NOT NULL,
            api_key TEXT NOT NULL,
            api_endpoint TEXT,
            temperature REAL DEFAULT 0.7,
            max_tokens INTEGER DEFAULT 2000,
            is_default INTEGER DEFAULT 0,
            enabled INTEGER DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS reply_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            original_message TEXT,
            reply_content TEXT,
            source TEXT DEFAULT 'ai',
            model_used TEXT,
            confidence REAL,
            response_time_ms INTEGER,
            platform TEXT,
            customer_name TEXT,
            house_name TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS feedback (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            reply_history_id INTEGER,
            action TEXT NOT NULL,
            modified_text TEXT,
            rating INTEGER,
            comment TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
            FOREIGN KEY (reply_history_id) REFERENCES reply_history(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS optimization_metrics (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            date TEXT NOT NULL,
            total_generated INTEGER DEFAULT 0,
            total_accepted INTEGER DEFAULT 0,
            total_modified INTEGER DEFAULT 0,
            total_rejected INTEGER DEFAULT 0,
            avg_confidence REAL DEFAULT 0,
            avg_response_time_ms INTEGER DEFAULT 0,
            UNIQUE(user_id, date),
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS blacklist (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            type TEXT DEFAULT 'KEYWORD',
            value TEXT NOT NULL,
            description TEXT DEFAULT '',
            package_name TEXT,
            is_enabled INTEGER DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
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
            phone TEXT PRIMARY KEY,
            agent_name TEXT DEFAULT '',
            status TEXT DEFAULT 'online',
            current_load INTEGER DEFAULT 0,
            max_concurrent INTEGER DEFAULT 5,
            user_id TEXT DEFAULT '',
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS agent_skills (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            agent_phone TEXT NOT NULL,
            skill_tag TEXT NOT NULL,
            proficiency INTEGER DEFAULT 5,
            FOREIGN KEY (agent_phone) REFERENCES agent_status(phone) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS routing_config (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            customer_name TEXT DEFAULT '',
            customer_phone TEXT DEFAULT '',
            platform TEXT DEFAULT '',
            assigned_agent_phone TEXT DEFAULT '',
            status TEXT DEFAULT 'pending',
            priority INTEGER DEFAULT 0,
            skill_required TEXT DEFAULT '',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            closed_at DATETIME DEFAULT NULL
        );

        CREATE TABLE IF NOT EXISTS tenant_style_config (
            user_id TEXT PRIMARY KEY,
            theme TEXT DEFAULT 'light',
            primary_color TEXT DEFAULT '#1976D2',
            accent_color TEXT default '#FF4081',
            font_size TEXT DEFAULT 'medium',
            bubble_style TEXT DEFAULT 'rounded',
            avatar_enabled INTEGER DEFAULT 1,
            show_timestamp INTEGER DEFAULT 1,
            send_sound INTEGER DEFAULT 1,
            custom_css TEXT DEFAULT '',
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS tenant_app_config (
            user_id TEXT PRIMARY KEY,
            app_name TEXT DEFAULT '客服小秘',
            welcome_message TEXT DEFAULT '您好，请问有什么可以帮您？',
            offline_message TEXT DEFAULT '当前无客服在线，请稍后再试。',
            auto_reply_enabled INTEGER DEFAULT 1,
            notification_enabled INTEGER DEFAULT 1,
            voice_enabled INTEGER DEFAULT 0,
            language TEXT DEFAULT 'zh-CN',
            session_timeout INTEGER DEFAULT 300,
            max_queue_size INTEGER DEFAULT 50,
            file_upload_enabled INTEGER DEFAULT 1,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        );

        CREATE INDEX IF NOT EXISTS idx_agent_skills_phone ON agent_skills(agent_phone);
        CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
        CREATE INDEX IF NOT EXISTS idx_sessions_status ON sessions(status);

        CREATE TABLE IF NOT EXISTS admin_sessions (
            token TEXT PRIMARY KEY,
            phone TEXT NOT NULL,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            expires_at DATETIME NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_admin_sessions_phone ON admin_sessions(phone);
        CREATE INDEX IF NOT EXISTS idx_admin_sessions_expires ON admin_sessions(expires_at);
    """)

    # Run migration for existing databases
    _migrate_device_to_user(db)

    db.commit()
    db.close()
