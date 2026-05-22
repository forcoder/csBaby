// 独立测试端点 - 完全独立，不使用任何共享模块
const express = require('express');
const { Pool } = require('pg');

const app = express();
const PORT = process.env.PORT || 8080;

// 独立的 pool
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.DATABASE_URL ? { rejectUnauthorized: false } : false,
  max: 3,
});

// 健康检查
app.get('/', (req, res) => {
  res.json({ status: 'ok', service: 'csbaby-sync-server', version: '1.0.0', endpoint: 'standalone-server' });
});

// 简单测试端点
app.get('/test-insert', async (req, res) => {
  console.log('[test-insert] Starting...');
  try {
    await pool.query('SELECT 1');
    await pool.query(
      'INSERT INTO keyword_rules (keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)',
      ['standalone-test', 'CONTAINS', 'hello', 'test', 'ALL', '[]', 0, 1, Date.now(), Date.now(), 'fc0807c8-38ff-4c34-8fc2-b61dd1ce582d', Date.now(), 0]
    );
    res.json({ code: 0, message: 'standalone test success' });
  } catch (e) {
    console.error('[test-insert] Error:', e.message);
    res.status(500).json({ code: 500, message: e.message });
  }
});

app.listen(PORT, () => {
  console.log(`Standalone server started on port ${PORT}`);
});