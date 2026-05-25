const { Pool } = require('pg');

let _pool = null;

function getPool() {
  if (!_pool) {
    _pool = new Pool({
      connectionString: process.env.DATABASE_URL || 'postgresql://postgres:postgres@localhost:5432/csbaby',
      ssl: process.env.DATABASE_URL ? { rejectUnauthorized: false } : false,
      max: 5,
      idleTimeoutMillis: 30000,
      connectionTimeoutMillis: 10000,
    });
  }
  return _pool;
}

let dbReady = null;

async function getDb() {
  if (dbReady) return dbReady;
  dbReady = (async () => {
    try {
      const pool = getPool();
      // Just test connection, don't init schema on every request
      await pool.query('SELECT 1');
      return pool;
    } catch (e) {
      console.error('[getDb] error:', e.message);
      dbReady = null;
      throw e;
    }
  })();
  return dbReady;
}

async function exec(sql, params = []) {
  const result = await getPool().query(sql, params);
  return { changes: result.rowCount || 0, lastInsertRowid: result.rows[0]?.id || 0 };
}

async function queryAll(sql, params = []) {
  const result = await getPool().query(sql, params);
  return result.rows;
}

async function queryOne(sql, params = []) {
  const rows = await queryAll(sql, params);
  return rows[0] || null;
}

module.exports = { getDb, exec, queryAll, queryOne, getPool };
