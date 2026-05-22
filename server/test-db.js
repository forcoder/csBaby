const { Pool } = require('pg');

const pool = new Pool({
  connectionString: process.env.DATABASE_URL || 'postgresql://postgres:postgres@localhost:5432/csbaby',
  ssl: process.env.DATABASE_URL ? { rejectUnauthorized: false } : false,
  max: 5,
  idleTimeoutMillis: 15000,
});

async function test() {
  console.log('Testing direct pool.query...');

  // Test 1: Simple SELECT
  try {
    const r = await pool.query('SELECT 1 as val');
    console.log('SELECT 1:', r.rows[0]);
  } catch (e) {
    console.error('SELECT error:', e.message);
  }

  // Test 2: INSERT with parameters
  try {
    const r = await pool.query(
      'INSERT INTO keyword_rules (keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)',
      ['test-keyword', 'CONTAINS', 'test reply', '', 'ALL', '[]', 0, 1, Date.now(), Date.now(), 'fc0807c8-38ff-4c34-8fc2-b61dd1ce582d', Date.now(), 0]
    );
    console.log('INSERT result:', r.rowCount);
  } catch (e) {
    console.error('INSERT error:', e.message);
  }

  // Test 3: ON CONFLICT with parameters
  try {
    const r = await pool.query(
      'INSERT INTO keyword_rules (id,keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14) ON CONFLICT (id) DO UPDATE SET keyword=EXCLUDED.keyword, sync_version=EXCLUDED.sync_version',
      [9999, 'test-keyword-2', 'CONTAINS', 'test reply 2', '', 'ALL', '[]', 0, 1, Date.now(), Date.now(), 'fc0807c8-38ff-4c34-8fc2-b61dd1ce582d', Date.now(), 0]
    );
    console.log('ON CONFLICT INSERT result:', r.rowCount);
  } catch (e) {
    console.error('ON CONFLICT error:', e.message);
  }

  await pool.end();
  console.log('Done');
}

test().catch(e => { console.error('Fatal:', e); process.exit(1); });