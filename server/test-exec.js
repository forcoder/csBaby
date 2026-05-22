const { Pool } = require('pg');

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.DATABASE_URL ? { rejectUnauthorized: false } : false,
  max: 3,
  idleTimeoutMillis: 15000,
  connectionTimeoutMillis: 5000,
});

let dbReady = null;

async function getDb() {
  if (dbReady) return dbReady;
  dbReady = (async () => {
    const client = await pool.connect();
    try {
      await client.query(`CREATE EXTENSION IF NOT EXISTS "uuid-ossp"`);
    } finally {
      client.release();
    }
    return pool;
  })();
  return dbReady;
}

async function exec(sql, params = []) {
  try {
    const finalSql = params.length > 0 ? interpolate(sql, params) : sql;
    console.log('[exec] SQL:', finalSql.slice(0, 100));
    const result = await pool.query(finalSql);
    return { changes: result.rowCount };
  } catch (e) {
    console.error('[exec] Error:', e.message);
    throw e;
  }
}

function interpolate(sql, params) {
  let i = 0;
  return sql.replace(/\$(\d+)/g, (_, n) => {
    const idx = parseInt(n, 10) - 1;
    return idx < params.length ? "'" + String(params[idx]).replace(/'/g, "''") + "'" : _;
  });
}

async function test() {
  console.log('=== Test 1: Direct pool.query with params ===');
  try {
    const r = await pool.query('SELECT 1 as val');
    console.log('SELECT OK:', r.rows[0]);
  } catch (e) { console.error('Test1 error:', e.message); }

  console.log('\n=== Test 2: INSERT via exec (uses interpolate) ===');
  await getDb();
  try {
    const r = await exec(
      'INSERT INTO keyword_rules (keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)',
      ['exec-test', 'CONTAINS', 'test reply', '', 'ALL', '[]', 0, 1, Date.now(), Date.now(), 'fc0807c8-38ff-4c34-8fc2-b61dd1ce582d', Date.now(), 0]
    );
    console.log('exec INSERT OK:', r);
  } catch (e) { console.error('Test2 error:', e.message); }

  console.log('\n=== Test 3: INSERT with ON CONFLICT ===');
  try {
    const r = await exec(
      'INSERT INTO keyword_rules (id,keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14) ON CONFLICT (id) DO UPDATE SET keyword=EXCLUDED.keyword, sync_version=EXCLUDED.sync_version',
      [99999, 'conflict-test', 'CONTAINS', 'test', '', 'ALL', '[]', 0, 1, Date.now(), Date.now(), 'fc0807c8-38ff-4c34-8fc2-b61dd1ce582d', Date.now(), 0]
    );
    console.log('exec ON CONFLICT OK:', r);
  } catch (e) { console.error('Test3 error:', e.message); }

  console.log('\n=== Test 4: UPDATE via exec ===');
  try {
    const r = await exec(
      'UPDATE keyword_rules SET keyword=$1 WHERE id=$2',
      ['updated-keyword', 1]
    );
    console.log('exec UPDATE OK:', r);
  } catch (e) { console.error('Test4 error:', e.message); }

  console.log('\n=== Test 5: Batch inserts (multiple exec calls) ===');
  try {
    for (let i = 0; i < 3; i++) {
      await exec(
        'INSERT INTO keyword_rules (keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)',
        [`batch-${i}`, 'CONTAINS', 'batch reply', '', 'ALL', '[]', 0, 1, Date.now(), Date.now(), 'fc0807c8-38ff-4c34-8fc2-b61dd1ce582d', Date.now(), 0]
      );
    }
    console.log('Batch inserts OK');
  } catch (e) { console.error('Test5 error:', e.message); }

  await pool.end();
  console.log('\n=== All tests completed ===');
}

test().catch(e => { console.error('Fatal:', e); process.exit(1); });