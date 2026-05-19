const initSqlJs = require('sql.js');
const fs = require('fs');
const path = require('path');

const DB_PATH = path.join(__dirname, '../data.db');
let dbInstance = null;
let dbReady = null;

async function getDb() {
  if (dbInstance) return dbInstance;
  if (dbReady) return dbReady;

  dbReady = (async () => {
    const SQL = await initSqlJs();
    if (fs.existsSync(DB_PATH)) {
      const buffer = fs.readFileSync(DB_PATH);
      dbInstance = new SQL.Database(buffer);
    } else {
      dbInstance = new SQL.Database();
    }
    initSchema(dbInstance);
    saveDb(dbInstance);
    return dbInstance;
  })();

  return dbReady;
}

function initSchema(db) {
  db.run(`
    CREATE TABLE IF NOT EXISTS users (id TEXT PRIMARY KEY, email TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL, display_name TEXT NOT NULL, tenant_id TEXT NOT NULL, created_at INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000));
    CREATE TABLE IF NOT EXISTS refresh_tokens (token TEXT PRIMARY KEY, user_id TEXT NOT NULL, tenant_id TEXT NOT NULL, expires_at INTEGER NOT NULL, created_at INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000));
    CREATE TABLE IF NOT EXISTS keyword_rules (id INTEGER PRIMARY KEY AUTOINCREMENT, keyword TEXT NOT NULL, match_type TEXT NOT NULL DEFAULT 'CONTAINS', reply_template TEXT NOT NULL, category TEXT NOT NULL DEFAULT '', target_type TEXT NOT NULL DEFAULT 'ALL', target_names_json TEXT NOT NULL DEFAULT '[]', priority INTEGER NOT NULL DEFAULT 0, enabled INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, tenant_id TEXT NOT NULL, sync_version INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0);
    CREATE TABLE IF NOT EXISTS ai_model_configs (id INTEGER PRIMARY KEY AUTOINCREMENT, model_type TEXT NOT NULL, model_name TEXT NOT NULL, api_key TEXT NOT NULL DEFAULT '', api_endpoint TEXT NOT NULL DEFAULT '', temperature REAL NOT NULL DEFAULT 0.7, max_tokens INTEGER NOT NULL DEFAULT 1000, is_default INTEGER NOT NULL DEFAULT 0, is_enabled INTEGER NOT NULL DEFAULT 1, monthly_cost REAL NOT NULL DEFAULT 0, last_used INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, tenant_id TEXT NOT NULL, sync_version INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0);
    CREATE TABLE IF NOT EXISTS user_style_profiles (user_id TEXT PRIMARY KEY, formality_level REAL NOT NULL DEFAULT 0.5, enthusiasm_level REAL NOT NULL DEFAULT 0.5, professionalism_level REAL NOT NULL DEFAULT 0.5, word_count_preference INTEGER NOT NULL DEFAULT 50, common_phrases TEXT NOT NULL DEFAULT '', avoid_phrases TEXT NOT NULL DEFAULT '', learning_samples INTEGER NOT NULL DEFAULT 0, accuracy_score REAL NOT NULL DEFAULT 0, last_trained INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, tenant_id TEXT NOT NULL, sync_version INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0);
    CREATE TABLE IF NOT EXISTS app_configs (package_name TEXT PRIMARY KEY, app_name TEXT NOT NULL, icon_uri TEXT, is_monitored INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, last_used INTEGER NOT NULL DEFAULT 0, tenant_id TEXT NOT NULL, sync_version INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0);
    CREATE TABLE IF NOT EXISTS scenarios (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, type TEXT NOT NULL DEFAULT 'ALL_PROPERTIES', target_id TEXT, description TEXT, created_at INTEGER NOT NULL, tenant_id TEXT NOT NULL, sync_version INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0);
    CREATE TABLE IF NOT EXISTS rule_scenario_relation (rule_id INTEGER NOT NULL, scenario_id INTEGER NOT NULL, tenant_id TEXT NOT NULL, PRIMARY KEY (rule_id, scenario_id));
    CREATE TABLE IF NOT EXISTS reply_history (id INTEGER PRIMARY KEY AUTOINCREMENT, source_app TEXT NOT NULL, original_message TEXT NOT NULL, generated_reply TEXT NOT NULL, final_reply TEXT NOT NULL, rule_matched_id INTEGER, model_used_id INTEGER, style_applied INTEGER NOT NULL DEFAULT 0, send_time INTEGER NOT NULL, modified INTEGER NOT NULL DEFAULT 0, tenant_id TEXT NOT NULL, sync_version INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0);
    CREATE TABLE IF NOT EXISTS sync_checkpoints (tenant_id TEXT PRIMARY KEY, last_sync_time INTEGER NOT NULL DEFAULT 0, sync_token TEXT, is_syncing INTEGER NOT NULL DEFAULT 0, last_error TEXT);
    CREATE TABLE IF NOT EXISTS message_blacklist (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, value TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', package_name TEXT, created_at INTEGER NOT NULL, is_enabled INTEGER NOT NULL DEFAULT 1, tenant_id TEXT NOT NULL, sync_version INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0);
    CREATE INDEX IF NOT EXISTS idx_kr_tenant ON keyword_rules(tenant_id);
    CREATE INDEX IF NOT EXISTS idx_am_tenant ON ai_model_configs(tenant_id);
    CREATE INDEX IF NOT EXISTS idx_rh_tenant ON reply_history(tenant_id);
    CREATE INDEX IF NOT EXISTS idx_mb_tenant ON message_blacklist(tenant_id);

    // OTA 版本管理
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
      release_date INTEGER,
      created_at INTEGER NOT NULL
    );

    // 数据备份记录
    CREATE TABLE IF NOT EXISTS backup_records (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      tenant_id TEXT NOT NULL,
      device_name TEXT NOT NULL DEFAULT '',
      app_version TEXT NOT NULL DEFAULT '',
      data_json TEXT NOT NULL,
      data_size INTEGER NOT NULL DEFAULT 0,
      checksum TEXT NOT NULL DEFAULT '',
      created_at INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_backup_tenant ON backup_records(tenant_id);
  `);
}

function saveDb(db) {
  try {
    const data = db.export();
    fs.writeFileSync(DB_PATH, Buffer.from(data));
  } catch (e) { console.error('DB save error:', e.message); }
}

// 同步执行 SQL（sql.js 是同步的）
function exec(sql, params = []) {
  const db = dbInstance;
  if (!db) throw new Error('数据库未初始化');
  try {
    db.run(sql, params);
    saveDb(db);
    return { changes: db.getRowsModified(), lastInsertRowid: db.exec('SELECT last_insert_rowid()')[0]?.values?.[0]?.[0] || 0 };
  } catch (e) { throw e; }
}

function queryAll(sql, params = []) {
  const db = dbInstance;
  if (!db) throw new Error('数据库未初始化');
  const stmt = db.prepare(sql);
  if (params.length > 0) stmt.bind(params);
  const results = [];
  while (stmt.step()) results.push(stmt.getAsObject());
  stmt.free();
  return results;
}

function queryOne(sql, params = []) {
  const results = queryAll(sql, params);
  return results[0] || null;
}

module.exports = { getDb, exec, queryAll, queryOne, saveDb };
