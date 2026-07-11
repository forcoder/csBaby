"""在MySQL RDS中创建API表结构（api_前缀）"""
import pymysql

conn = pymysql.connect(
    host="r8371qiaozhou.mysql.aliyun.com",
    port=3306,
    user="qiaozhou",
    password="Rds@2026",
    database="r2346qiaozhou",
    charset="utf8mb4",
    connect_timeout=10,
    ssl_disabled=True
)
cursor = conn.cursor()

tables_sql = [
    """CREATE TABLE IF NOT EXISTS api_users (
        id VARCHAR(64) PRIMARY KEY,
        phone VARCHAR(20) NOT NULL,
        password_hash VARCHAR(128) NOT NULL,
        salt VARCHAR(64) NOT NULL,
        name VARCHAR(100) DEFAULT '',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        email VARCHAR(255) DEFAULT NULL,
        UNIQUE KEY uk_phone (phone)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_user_devices (
        user_id VARCHAR(64) NOT NULL,
        device_id VARCHAR(128) NOT NULL,
        platform VARCHAR(20) DEFAULT 'android',
        device_name VARCHAR(100) DEFAULT '',
        registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (user_id, device_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_devices (
        id VARCHAR(128) PRIMARY KEY,
        token VARCHAR(256) NOT NULL,
        name VARCHAR(100) DEFAULT NULL,
        platform VARCHAR(20) DEFAULT 'android',
        app_version VARCHAR(50) DEFAULT NULL,
        last_heartbeat DATETIME DEFAULT NULL,
        is_active TINYINT DEFAULT 1,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_keyword_rules (
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
        updated_at DATETIME DEFAULT NULL,
        INDEX idx_rules_user (user_id),
        INDEX idx_rules_keyword (keyword)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_model_configs (
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
        updated_at DATETIME DEFAULT NULL,
        INDEX idx_models_user_enabled (user_id, enabled)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_reply_history (
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
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_history_user_created (user_id, created_at DESC),
        INDEX idx_history_source (source)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_feedback (
        id INT AUTO_INCREMENT PRIMARY KEY,
        user_id VARCHAR(64) NOT NULL,
        reply_history_id INT DEFAULT NULL,
        action VARCHAR(50) NOT NULL,
        modified_text TEXT,
        rating INT DEFAULT NULL,
        comment TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_feedback_user (user_id),
        INDEX idx_feedback_history (reply_history_id),
        INDEX idx_feedback_action (action)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_optimization_metrics (
        id INT AUTO_INCREMENT PRIMARY KEY,
        user_id VARCHAR(64) NOT NULL,
        date VARCHAR(20) NOT NULL,
        total_generated INT DEFAULT 0,
        total_accepted INT DEFAULT 0,
        total_modified INT DEFAULT 0,
        total_rejected INT DEFAULT 0,
        avg_confidence DOUBLE DEFAULT 0,
        avg_response_time_ms INT DEFAULT 0,
        UNIQUE KEY uk_user_date (user_id, date),
        INDEX idx_metrics_user_date (user_id, date)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_blacklist (
        id INT AUTO_INCREMENT PRIMARY KEY,
        user_id VARCHAR(64) NOT NULL,
        type VARCHAR(50) DEFAULT 'KEYWORD',
        value VARCHAR(500) NOT NULL,
        description TEXT,
        package_name VARCHAR(255) DEFAULT NULL,
        is_enabled TINYINT DEFAULT 1,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_blacklist_user (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_agent_status (
        phone VARCHAR(20) PRIMARY KEY,
        agent_name VARCHAR(100) DEFAULT '',
        status VARCHAR(20) DEFAULT 'online',
        current_load INT DEFAULT 0,
        max_concurrent INT DEFAULT 5,
        user_id VARCHAR(64) DEFAULT '',
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_agent_skills (
        id INT AUTO_INCREMENT PRIMARY KEY,
        agent_phone VARCHAR(20) NOT NULL,
        skill_tag VARCHAR(100) NOT NULL,
        proficiency INT DEFAULT 5,
        INDEX idx_agent_skills_phone (agent_phone)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_routing_config (
        `key` VARCHAR(100) PRIMARY KEY,
        `value` TEXT NOT NULL,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_sessions (
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
        closed_at DATETIME DEFAULT NULL,
        INDEX idx_sessions_user (user_id),
        INDEX idx_sessions_status (status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_tenant_style_config (
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
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_tenant_app_config (
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
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_admin_accounts (
        phone VARCHAR(20) PRIMARY KEY,
        password_hash VARCHAR(128) NOT NULL,
        is_active TINYINT DEFAULT 1,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_admin_sessions (
        token VARCHAR(128) PRIMARY KEY,
        phone VARCHAR(20) NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        expires_at DATETIME NOT NULL,
        INDEX idx_admin_sessions_phone (phone),
        INDEX idx_admin_sessions_expires (expires_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_audit_log (
        id INT AUTO_INCREMENT PRIMARY KEY,
        admin_phone VARCHAR(20) NOT NULL,
        action VARCHAR(100) NOT NULL,
        target_type VARCHAR(50) DEFAULT '',
        target_id VARCHAR(100) DEFAULT '',
        detail TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_audit_created (created_at DESC)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_sync_outbox (
        id INT AUTO_INCREMENT PRIMARY KEY,
        table_name VARCHAR(100) NOT NULL,
        op VARCHAR(20) NOT NULL,
        row_id INT DEFAULT NULL,
        payload TEXT NOT NULL,
        attempts INT DEFAULT 0,
        last_error TEXT DEFAULT NULL,
        next_retry_at DATETIME NOT NULL,
        created_at DATETIME DEFAULT NULL,
        updated_at DATETIME DEFAULT NULL,
        INDEX idx_outbox_next_retry (next_retry_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",

    """CREATE TABLE IF NOT EXISTS api_sync_outbox_dead (
        id INT AUTO_INCREMENT PRIMARY KEY,
        table_name VARCHAR(100) NOT NULL,
        op VARCHAR(20) NOT NULL,
        row_id INT DEFAULT NULL,
        payload TEXT NOT NULL,
        attempts INT DEFAULT NULL,
        last_error TEXT DEFAULT NULL,
        created_at DATETIME DEFAULT NULL,
        moved_at DATETIME DEFAULT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
]

for sql in tables_sql:
    try:
        cursor.execute(sql)
        print(f"OK: {sql.split()[2]}")
    except Exception as e:
        print(f"FAIL: {sql.split()[2]} -> {e}")

conn.commit()
print("\n=== 验证表 ===")
cursor.execute("SELECT TABLE_NAME FROM information_schema.tables WHERE TABLE_SCHEMA='r2346qiaozhou' AND TABLE_NAME LIKE 'api_%' ORDER BY TABLE_NAME")
for t in cursor.fetchall():
    print(f"  {t[0]}")
conn.close()