// Diagnostic endpoint - add to index.js temporarily
app.get('/diagnose', async (req, res) => {
  const { getPool, getDb, exec, queryOne, queryAll } = require('./db');
  const results = {};

  try {
    const pool = getPool();
    results.poolCreated = true;

    // Test 1: simple SELECT
    try {
      const r1 = await pool.query('SELECT 1 as val');
      results.select1 = r1.rows[0];
    } catch (e) { results.select1Err = e.message; }

    // Test 2: INSERT
    try {
      const now = Date.now();
      const r2 = await pool.query(
        'INSERT INTO keyword_rules (keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)',
        ['diag-test', 'CONTAINS', 'test', 'test', 'ALL', '[]', 0, 1, now, now, 'fc0807c8-38ff-4c34-8fc2-b61dd1ce582d', now, 0]
      );
      results.insert = { rowCount: r2.rowCount };
    } catch (e) { results.insertErr = e.message; }

    // Test 3: SELECT after INSERT
    try {
      const r3 = await queryAll('SELECT COUNT(*) as cnt FROM keyword_rules WHERE keyword=$1', ['diag-test']);
      results.countAfterInsert = r3[0];
    } catch (e) { results.countErr = e.message; }

    // Test 4: exec function
    try {
      const r4 = await exec('DELETE FROM keyword_rules WHERE keyword=$1', ['diag-test']);
      results.deleteExec = r4;
    } catch (e) { results.deleteErr = e.message; }

    results.allOk = true;
  } catch (e) {
    results.fatalErr = e.message;
  }

  res.json({ code: 0, data: results });
});
