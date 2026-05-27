// 简化版 sync - 直接使用独立的 pool
const { Router } = require('express');
const { Pool } = require('pg');
const { authMiddleware } = require('./auth');

const router = Router();
router.use(authMiddleware);

// 独立的 pool
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.DATABASE_URL ? { rejectUnauthorized: false } : false,
  max: 5,
});

// 简化 push - 每条记录独立事务
router.post('/simple-push', async (req, res) => {
  console.log('[simple-push v3] Starting...');
  const t = req.tenantId;
  const { keywordRules = [] } = req.body;
  const now = Date.now();

  console.log('[simple-push] Tenant:', t, 'rules:', keywordRules.length);

  try {
    for (const r of keywordRules) {
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        await client.query(
          'INSERT INTO keyword_rules (id,keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14) ON CONFLICT (id) DO UPDATE SET keyword=EXCLUDED.keyword, sync_version=EXCLUDED.sync_version',
          [r.id, r.keyword, r.matchType, r.replyTemplate, r.category, r.targetType, r.targetNamesJson, r.priority, r.enabled ? 1 : 0, r.createdAt, r.updatedAt, t, now, r.deleted ? 1 : 0]
        );
        await client.query('COMMIT');
      } catch (e) {
        try { await client.query('ROLLBACK'); } catch (_) {}
        throw e;
      } finally {
        client.release();
      }
    }

    res.json({ code: 0, message: '成功', data: { accepted: true } });
  } catch (e) {
    console.error('[simple-push] Error:', e.message);
    res.status(500).json({ code: 500, message: e.message });
  }
});

// 测试端点
router.get('/test', async (req, res) => {
  try {
    await pool.query('SELECT 1');
    res.json({ code: 0, message: 'pool test OK' });
  } catch (e) {
    res.status(500).json({ code: 500, message: e.message });
  }
});

module.exports = router;