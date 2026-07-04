-- csBaby Sync Server Database Schema
-- MySQL 8.0+ (阿里云 RDS MySQL)

-- Usuários
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    tenant_id VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL DEFAULT (UNIX_TIMESTAMP() * 1000),
    updated_at BIGINT,
    deleted TINYINT(1) DEFAULT 0,
    INDEX idx_users_email (email),
    INDEX idx_users_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Keyword Rules
CREATE TABLE IF NOT EXISTS keyword_rules (
    id VARCHAR(64) PRIMARY KEY,
    keyword VARCHAR(500),
    match_type VARCHAR(50),
    reply_template TEXT,
    category VARCHAR(100),
    target_type VARCHAR(50),
    target_names_json TEXT,
    priority INT DEFAULT 0,
    enabled TINYINT(1) DEFAULT 1,
    created_at BIGINT,
    updated_at BIGINT,
    tenant_id VARCHAR(64) NOT NULL,
    sync_version BIGINT DEFAULT 0,
    deleted TINYINT(1) DEFAULT 0,
    INDEX idx_keyword_tenant (tenant_id),
    INDEX idx_keyword_version (sync_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AI Model Configs
CREATE TABLE IF NOT EXISTS ai_model_configs (
    id VARCHAR(64) PRIMARY KEY,
    model_type VARCHAR(50),
    model_name VARCHAR(200),
    api_key TEXT,
    api_endpoint TEXT,
    temperature DOUBLE DEFAULT 0.7,
    max_tokens INT DEFAULT 1000,
    is_default TINYINT(1) DEFAULT 0,
    is_enabled TINYINT(1) DEFAULT 1,
    monthly_cost DOUBLE DEFAULT 0,
    last_used BIGINT,
    created_at BIGINT,
    tenant_id VARCHAR(64) NOT NULL,
    sync_version BIGINT DEFAULT 0,
    deleted TINYINT(1) DEFAULT 0,
    INDEX idx_ai_model_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- User Style Profiles
CREATE TABLE IF NOT EXISTS user_style_profiles (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    formality_level DOUBLE DEFAULT 0.5,
    enthusiasm_level DOUBLE DEFAULT 0.5,
    professionalism_level DOUBLE DEFAULT 0.5,
    word_count_preference INT DEFAULT 50,
    common_phrases TEXT,
    avoid_phrases TEXT,
    learning_samples TEXT,
    accuracy_score DOUBLE DEFAULT 0.5,
    last_trained BIGINT,
    created_at BIGINT,
    tenant_id VARCHAR(64) NOT NULL,
    sync_version BIGINT DEFAULT 0,
    deleted TINYINT(1) DEFAULT 0,
    INDEX idx_profile_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- App Configs
CREATE TABLE IF NOT EXISTS app_configs (
    package_name VARCHAR(255) PRIMARY KEY,
    app_name VARCHAR(200),
    icon_uri TEXT,
    is_monitored TINYINT(1) DEFAULT 1,
    created_at BIGINT,
    last_used BIGINT,
    tenant_id VARCHAR(64) NOT NULL,
    sync_version BIGINT DEFAULT 0,
    deleted TINYINT(1) DEFAULT 0,
    INDEX idx_app_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Scenarios
CREATE TABLE IF NOT EXISTS scenarios (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(200),
    type VARCHAR(50),
    target_id VARCHAR(64),
    description TEXT,
    created_at BIGINT,
    tenant_id VARCHAR(64) NOT NULL,
    sync_version BIGINT DEFAULT 0,
    deleted TINYINT(1) DEFAULT 0,
    INDEX idx_scenario_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Reply History
CREATE TABLE IF NOT EXISTS reply_history (
    id VARCHAR(64) PRIMARY KEY,
    source_app VARCHAR(255),
    original_message TEXT,
    generated_reply TEXT,
    final_reply TEXT,
    rule_matched_id VARCHAR(64),
    model_used_id VARCHAR(64),
    style_applied TINYINT(1) DEFAULT 0,
    send_time BIGINT,
    modified TINYINT(1) DEFAULT 0,
    tenant_id VARCHAR(64) NOT NULL,
    sync_version BIGINT DEFAULT 0,
    deleted TINYINT(1) DEFAULT 0,
    INDEX idx_reply_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Message Blacklist
CREATE TABLE IF NOT EXISTS message_blacklist (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(50),
    value TEXT,
    description TEXT,
    package_name VARCHAR(255),
    created_at BIGINT,
    is_enabled TINYINT(1) DEFAULT 1,
    tenant_id VARCHAR(64) NOT NULL,
    sync_version BIGINT DEFAULT 0,
    deleted TINYINT(1) DEFAULT 0,
    INDEX idx_blacklist_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Sync Checkpoints
CREATE TABLE IF NOT EXISTS sync_checkpoints (
    tenant_id VARCHAR(64) PRIMARY KEY,
    last_sync_version BIGINT DEFAULT 0,
    last_sync_time BIGINT,
    updated_at BIGINT,
    is_syncing TINYINT(1) DEFAULT 0,
    last_error TEXT,
    device_info TEXT,
    created_at BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Backup Records
CREATE TABLE IF NOT EXISTS backup_records (
    id INT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    device_name VARCHAR(255),
    app_version VARCHAR(50),
    data_json LONGTEXT,
    data_size BIGINT,
    checksum VARCHAR(64),
    version VARCHAR(20) DEFAULT '1.0',
    backup_type VARCHAR(20) DEFAULT 'manual',
    created_at BIGINT DEFAULT (UNIX_TIMESTAMP() * 1000),
    deleted TINYINT(1) DEFAULT 0,
    INDEX idx_backup_tenant (tenant_id),
    INDEX idx_backup_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
