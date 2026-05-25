const { Pool } = require('pg');

let _pool = null;

function getPool() {
  if (!_pool) {
    _pool = new Pool({
      connectionString: process.env.DATABASE_URL || 'postgresql://postgres:postgres@localhost:5432/csbaby',
      ssl: process.env.DATABASE_URL ? { rejectUnauthorized: false } : false,
      max: 3,
      idleTimeoutMillis: 30000,
      connectionTimeoutMillis: 10000,
    });
    _pool.on('error', (e) => console.error('pg pool error:', e.message));
  }
  return _pool;
}

let dbReady = null;

async function getDb() {
  if (dbReady) return dbReady;
  dbReady = (async () => {
    try {
      await getPool().query('SELECT 1');
      console.log('[getDb] Connection OK');
    } catch (e) {
      console.error('[getDb] error:', e.message);
      dbReady = null;
      throw e;
    }
    return getPool();
  })();
  return dbReady;
}

// Simple SQL execution using pool.query directly
async function exec(sql, params = []) {
  console.log('[exec] SQL:', (sql || '').substring(0, 80));
  const result = await getPool().query(sql, params);
  console.log('[exec] OK, rowCount:', result.rowCount);
  return { changes: result.rowCount, lastInsertRowid: result.rows[0]?.id || 0 };
}

async function queryAll(sql, params = []) {
  const result = await getPool().query(sql, params);
  return result.rows;
}

async function queryOne(sql, params = []) {
  const results = await queryAll(sql, params);
  return results[0] || null;
}

// Transaction helper - only use when needed
async function withTransaction(fn) {
  const client = await getPool().connect();
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

module.exports = { getDb, exec, queryAll, queryOne, getPool, withTransaction };
