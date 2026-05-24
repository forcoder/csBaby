const { Pool } = require('pg');

// PostgreSQL 连接配置
// 优先使用 DATABASE_URL 环境变量（Render 免费 PostgreSQL 会自动设置）
// 本地开发时可使用 .env 文件或默认值
const pool = new Pool({
  connectionString: process.env.DATABASE_URL || 'postgresql://postgres:postgres@localhost:5432/csbaby',
  ssl: process.env.DATABASE_URL ? { rejectUnauthorized: false } : false,
  max: 3,
  min: 1,
  idleTimeoutMillis: 20000,
  connectionTimeoutMillis: 10000,
  idle_in_transaction_session_timeout: 5000,
});

let dbReady = null;

// 捕获连接池级别的错误
pool.on('error', (e) => {
  console.error('pg pool error:', e.message);
});

async function getDb() {
  if (dbReady) return dbReady;
  dbReady = (async () => {
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      await initSchema(client);
      await client.query('COMMIT');
    } catch (e) {
      try { await client.query('ROLLBACK'); } catch (_) {}
      throw e;
    } finally {
      client.release();
    }
    return pool;
  })();
  return dbReady;
}

async function initSchema(client) {
  // 启用 uuid-ossp 扩展
  await client.query(`CREATE EXTENSION IF NOT EXISTS "uuid-ossp"`);

  // 用户表
  await client.query(`
    CREATE TABLE IF NOT EXISTS users (
      id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
      email TEXT UNIQUE NOT NULL,
      password_hash TEXT NOT NULL,
      display_name TEXT NOT NULL,
      tenant_id UUID NOT NULL,
      created_at BIGINT NOT NULL
    )
  `);

  // 刷新令牌表
  await client.query(`
    CREATE TABLE IF NOT EXISTS refresh_tokens (
      token TEXT PRIMARY KEY,
      user_id UUID NOT NULL,
      tenant_id UUID NOT NULL,
      expires_at BIGINT NOT NULL,
      created_at BIGINT NOT NULL
    )
  `);

  // 知识库规则表
  await client.query(`
    CREATE TABLE IF NOT EXISTS keyword_rules (
      id SERIAL PRIMARY KEY,
      keyword TEXT NOT NULL,
      match_type TEXT NOT NULL DEFAULT 'CONTAINS',
      reply_template TEXT NOT NULL,
      category TEXT NOT NULL DEFAULT '',
      target_type TEXT NOT NULL DEFAULT 'ALL',
      target_names_json TEXT NOT NULL DEFAULT '[]',
      priority INTEGER NOT NULL DEFAULT 0,
      enabled INTEGER NOT NULL DEFAULT 1,
      created_at BIGINT NOT NULL,
      updated_at BIGINT NOT NULL,
      tenant_id UUID NOT NULL,
      sync_version BIGINT NOT NULL DEFAULT 0,
      deleted INTEGER NOT NULL DEFAULT 0
    )
  `);

  // AI 模型配置表
  await client.query(`
    CREATE TABLE IF NOT EXISTS ai_model_configs (
      id SERIAL PRIMARY KEY,
      model_type TEXT NOT NULL,
      model_name TEXT NOT NULL,
      api_key TEXT NOT NULL DEFAULT '',
      api_endpoint TEXT NOT NULL DEFAULT '',
      temperature REAL NOT NULL DEFAULT 0.7,
      max_tokens INTEGER NOT NULL DEFAULT 1000,
      is_default INTEGER NOT NULL DEFAULT 0,
      is_enabled INTEGER NOT NULL DEFAULT 1,
      monthly_cost REAL NOT NULL DEFAULT 0,
      last_used BIGINT NOT NULL DEFAULT 0,
      created_at BIGINT NOT NULL,
      tenant_id UUID NOT NULL,
      sync_version BIGINT NOT NULL DEFAULT 0,
      deleted INTEGER NOT NULL DEFAULT 0
    )
  `);

  // 用户风格画像表
  await client.query(`
    CREATE TABLE IF NOT EXISTS user_style_profiles (
      user_id UUID PRIMARY KEY,
      formality_level REAL NOT NULL DEFAULT 0.5,
      enthusiasm_level REAL NOT NULL DEFAULT 0.5,
      professionalism_level REAL NOT NULL DEFAULT 0.5,
      word_count_preference INTEGER NOT NULL DEFAULT 50,
      common_phrases TEXT NOT NULL DEFAULT '',
      avoid_phrases TEXT NOT NULL DEFAULT '',
      learning_samples INTEGER NOT NULL DEFAULT 0,
      accuracy_score REAL NOT NULL DEFAULT 0,
      last_trained BIGINT NOT NULL DEFAULT 0,
      created_at BIGINT NOT NULL,
      tenant_id UUID NOT NULL,
      sync_version BIGINT NOT NULL DEFAULT 0,
      deleted INTEGER NOT NULL DEFAULT 0
    )
  `);

  // 应用配置表
  await client.query(`
    CREATE TABLE IF NOT EXISTS app_configs (
      package_name TEXT PRIMARY KEY,
      app_name TEXT NOT NULL,
      icon_uri TEXT,
      is_monitored INTEGER NOT NULL DEFAULT 0,
      created_at BIGINT NOT NULL,
      last_used BIGINT NOT NULL DEFAULT 0,
      tenant_id UUID NOT NULL,
      sync_version BIGINT NOT NULL DEFAULT 0,
      deleted INTEGER NOT NULL DEFAULT 0
    )
  `);

  // 场景表
  await client.query(`
    CREATE TABLE IF NOT EXISTS scenarios (
      id SERIAL PRIMARY KEY,
      name TEXT NOT NULL,
      type TEXT NOT NULL DEFAULT 'ALL_PROPERTIES',
      target_id TEXT,
      description TEXT,
      created_at BIGINT NOT NULL,
      tenant_id UUID NOT NULL,
      sync_version BIGINT NOT NULL DEFAULT 0,
      deleted INTEGER NOT NULL DEFAULT 0
    )
  `);

  // 规则-场景关联表
  await client.query(`
    CREATE TABLE IF NOT EXISTS rule_scenario_relation (
      rule_id INTEGER NOT NULL,
      scenario_id INTEGER NOT NULL,
      tenant_id UUID NOT NULL,
      PRIMARY KEY (rule_id, scenario_id)
    )
  `);

  // 回复历史表
  await client.query(`
    CREATE TABLE IF NOT EXISTS reply_history (
      id SERIAL PRIMARY KEY,
      source_app TEXT NOT NULL,
      original_message TEXT NOT NULL,
      generated_reply TEXT NOT NULL,
      final_reply TEXT NOT NULL,
      rule_matched_id INTEGER,
      model_used_id INTEGER,
      style_applied INTEGER NOT NULL DEFAULT 0,
      send_time BIGINT NOT NULL,
      modified INTEGER NOT NULL DEFAULT 0,
      tenant_id UUID NOT NULL,
      sync_version BIGINT NOT NULL DEFAULT 0,
      deleted INTEGER NOT NULL DEFAULT 0
    )
  `);

  // 同步检查点表
  await client.query(`
    CREATE TABLE IF NOT EXISTS sync_checkpoints (
      tenant_id UUID PRIMARY KEY,
      last_sync_time BIGINT NOT NULL DEFAULT 0,
      sync_token TEXT,
      is_syncing INTEGER NOT NULL DEFAULT 0,
      last_error TEXT
    )
  `);

  // 消息黑名单表
  await client.query(`
    CREATE TABLE IF NOT EXISTS message_blacklist (
      id SERIAL PRIMARY KEY,
      type TEXT NOT NULL,
      value TEXT NOT NULL,
      description TEXT NOT NULL DEFAULT '',
      package_name TEXT,
      created_at BIGINT NOT NULL,
      is_enabled INTEGER NOT NULL DEFAULT 1,
      tenant_id UUID NOT NULL,
      sync_version BIGINT NOT NULL DEFAULT 0,
      deleted INTEGER NOT NULL DEFAULT 0
    )
  `);

  // OTA 版本管理表
  await client.query(`
    CREATE TABLE IF NOT EXISTS ota_versions (
      version_code INTEGER PRIMARY KEY,
      version_name TEXT NOT NULL,
      download_url TEXT NOT NULL,
      file_size INTEGER NOT NULL DEFAULT 0,
      md5 TEXT NOT NULL DEFAULT '',
      release_notes TEXT NOT NULL DEFAULT '',
      channel TEXT NOT NULL DEFAULT 'default',
      is_force_update INTEGER NOT NULL DEFAULT 0,
      min_required_version INTEGER NOT NULL DEFAULT 1,
      is_published INTEGER NOT NULL DEFAULT 1,
      release_date BIGINT,
      created_at BIGINT NOT NULL
    )
  `);

  // 数据备份记录表
  await client.query(`
    CREATE TABLE IF NOT EXISTS backup_records (
      id SERIAL PRIMARY KEY,
      tenant_id UUID NOT NULL,
      device_name TEXT NOT NULL DEFAULT '',
      app_version TEXT NOT NULL DEFAULT '',
      data_json TEXT NOT NULL,
      data_size INTEGER NOT NULL DEFAULT 0,
      checksum TEXT NOT NULL DEFAULT '',
      created_at BIGINT NOT NULL
    )
  `);

  // 创建索引
  await client.query(`CREATE INDEX IF NOT EXISTS idx_kr_tenant ON keyword_rules(tenant_id)`);
  await client.query(`CREATE INDEX IF NOT EXISTS idx_am_tenant ON ai_model_configs(tenant_id)`);
  await client.query(`CREATE INDEX IF NOT EXISTS idx_rh_tenant ON reply_history(tenant_id)`);
  await client.query(`CREATE INDEX IF NOT EXISTS idx_mb_tenant ON message_blacklist(tenant_id)`);
  await client.query(`CREATE INDEX IF NOT EXISTS idx_backup_tenant ON backup_records(tenant_id)`);
  await client.query(`CREATE INDEX IF NOT EXISTS idx_users_email ON users(email)`);
  await client.query(`CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id)`);
}

// 安全地转义 SQL 值（用于简单查询协议）
function escapeSql(val) {
  if (val === null || val === undefined) return 'NULL';
  if (typeof val === 'number') return String(val);
  if (typeof val === 'boolean') return val ? '1' : '0';
  // 字符串：转义单引号
  return `'${String(val).replace(/'/g, "''")}'`;
}

// 将参数化 SQL 中的 $N 替换为转义后的值（用于简单查询协议）
function interpolate(sql, params) {
  let i = 0;
  return sql.replace(/\$(\d+)/g, (_, n) => {
    const idx = parseInt(n, 10) - 1;
    return idx < params.length ? escapeSql(params[idx]) : _;
  });
}

// 执行 SQL（INSERT/UPDATE/DELETE）
// 使用标准参数化查询，并验证连接状态
async function exec(sql, params = []) {
  try {
    // 确保连接池有效
    const client = await pool.connect();
    try {
      // 验证连接状态
      await client.query('SELECT 1');
      const result = await client.query(sql, params);
      return { changes: result.rowCount, lastInsertRowid: result.rows[0]?.id || 0 };
    } finally {
      client.release();
    }
  } catch (e) {
    console.error('exec error:', e.message, 'SQL:', (sql || '').slice(0, 200), 'Params:', JSON.stringify(params || []).slice(0, 200));
    throw e;
  }
}

// 查询多条记录
async function queryAll(sql, params = []) {
  try {
    // 确保连接池有效
    const client = await pool.connect();
    try {
      // 验证连接状态
      await client.query('SELECT 1');
      const result = params.length > 0 ? await client.query(sql, params) : await client.query(sql);
      return result.rows;
    } finally {
      client.release();
    }
  } catch (e) {
    console.error('queryAll error:', e.message, 'SQL:', (sql || '').slice(0, 200));
    throw e;
  }
}

// 查询单条记录
async function queryOne(sql, params = []) {
  const results = await queryAll(sql, params);
  return results[0] || null;
}

// 在单个连接上执行事务（所有操作共享同一连接）
async function withTransaction(fn) {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const result = await fn(client);
    await client.query('COMMIT');
    return result;
  } catch (e) {
    try { await client.query('ROLLBACK'); } catch (_) {}
    throw e;
  } finally {
    client.release();
  }
}

module.exports = { getDb, exec, queryAll, queryOne, pool, withTransaction };
