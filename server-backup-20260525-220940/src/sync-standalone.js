// 独立版本的 sync push - 不使用任何共享模块
const { Router } = require('express');
const { Pool } = require('pg');
const { verifyAccess } = require('./auth');

const router = Router();

// 创建独立的 pool
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.DATABASE_URL ? { rejectUnauthorized: false } : false,
  max: 3,
  idleTimeoutMillis: 15000,
});

// 确保数据库就绪
async function ensureDb() {
  try {
    await pool.query('SELECT 1');
  } catch (e) {
    // 忽略错误
  }
}

// 简化版 push - 直接使用 pool.query
router.post('/standalone-push', async (req, res) => {
  console.log('[standalone-push] Starting...');

  // 验证 token
  const h = req.headers.authorization;
  if (!h || !h.startsWith('Bearer ')) {
    return res.status(401).json({ code: 401, message: '未提供认证令牌' });
  }
  const d = verifyAccess(h.slice(7));
  if (!d) {
    return res.status(401).json({ code: 401, message: '令牌无效或已过期' });
  }

  await ensureDb();

  const t = d.tenantId;
  const { keywordRules = [] } = req.body;
  const now = Date.now();

  console.log('[standalone-push] Tenant:', t, 'rules:', keywordRules.length);

  try {
    for (const r of keywordRules) {
      await pool.query(
        'INSERT INTO keyword_rules (id,keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14) ON CONFLICT (id) DO UPDATE SET keyword=EXCLUDED.keyword, sync_version=EXCLUDED.sync_version',
        [r.id, r.keyword, r.matchType, r.replyTemplate, r.category, r.targetType, r.targetNamesJson, r.priority, r.enabled ? 1 : 0, r.createdAt, r.updatedAt, t, now, r.deleted ? 1 : 0]
      );
      console.log('[standalone-push] Inserted rule:', r.id);
    }

    res.json({ code: 0, message: '成功', data: { accepted: true } });
  } catch (e) {
    console.error('[standalone-push] Error:', e.message);
    res.status(500).json({ code: 500, message: e.message });
  }
});

module.exports = router;