// 简化版的 sync push - 直接内联 pg Pool 创建
const { Router } = require('express');
const { Pool } = require('pg');
const { authMiddleware } = require('./auth');

const router = Router();
router.use(authMiddleware);

// 创建独立的 pool
const pool = new Pool({
  connectionString: process.env.DATABASE_URL || process.env.DATABASE_URL,
  ssl: process.env.DATABASE_URL ? { rejectUnauthorized: false } : false,
  max: 5,
  idleTimeoutMillis: 15000,
});

// 测试端点
router.get('/test', async (req, res) => {
  console.log('[simple-test] Received request');
  try {
    const result = await pool.query('SELECT 1 as val');
    console.log('[simple-test] Query OK:', result.rows[0]);
    res.json({ code: 0, message: 'pool test OK', data: result.rows[0] });
  } catch (e) {
    console.error('[simple-test] Error:', e.message);
    res.status(500).json({ code: 500, message: e.message });
  }
});

// 简化版 push
router.post('/simple-push', async (req, res) => {
  console.log('[simple-push] Starting...');
  const t = req.tenantId;
  const { keywordRules = [], aiModelConfigs = [], appConfigs = [], scenarios = [] } = req.body;
  const now = Date.now();

  try {
    console.log('[simple-push] Inserting', keywordRules.length, 'rules for tenant:', t);

    // 直接用 pool.query
    for (const r of keywordRules) {
      await pool.query(
        `INSERT INTO keyword_rules (id,keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)
         ON CONFLICT (id) DO UPDATE SET keyword=EXCLUDED.keyword, sync_version=EXCLUDED.sync_version`,
        [r.id, r.keyword, r.matchType, r.replyTemplate, r.category, r.targetType, r.targetNamesJson, r.priority, r.enabled ? 1 : 0, r.createdAt, r.updatedAt, t, now, r.deleted ? 1 : 0]
      );
      console.log('[simple-push] Inserted rule:', r.id);
    }

    res.json({ code: 0, message: '成功', data: { accepted: true } });
  } catch (e) {
    console.error('[simple-push] Error:', e.message);
    res.status(500).json({ code: 500, message: e.message });
  }
});

module.exports = router;