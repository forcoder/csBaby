// 简化版的 sync push，使用 pool.query 直接
const { Router } = require('express');
const { Pool } = require('pg');
const { authMiddleware } = require('./auth');

const router = Router();
router.use(authMiddleware);

// 获取 pool
let _pool = null;
function getPool() {
  if (!_pool) {
    _pool = new Pool({
      connectionString: process.env.DATABASE_URL,
      ssl: { rejectUnauthorized: false }
    });
  }
  return _pool;
}

// 简化版 push
router.post('/simple-push', async (req, res) => {
  const pool = getPool();
  const t = req.tenantId;
  const { keywordRules=[], aiModelConfigs=[], appConfigs=[], scenarios=[] } = req.body;
  const now = Date.now();
  
  console.log('[simple-push] Starting for tenant:', t);
  
  try {
    // 直接用 pool.query 插入
    for (const r of keywordRules) {
      await pool.query(
        'INSERT INTO keyword_rules (id,keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14) ON CONFLICT (id) DO UPDATE SET keyword=EXCLUDED.keyword,match_type=EXCLUDED.match_type,reply_template=EXCLUDED.reply_template,category=EXCLUDED.category,target_type=EXCLUDED.target_type,target_names_json=EXCLUDED.target_names_json,priority=EXCLUDED.priority,enabled=EXCLUDED.enabled,created_at=EXCLUDED.created_at,updated_at=EXCLUDED.updated_at,tenant_id=EXCLUDED.tenant_id,sync_version=EXCLUDED.sync_version,deleted=EXCLUDED.deleted',
        [r.id, r.keyword, r.matchType, r.replyTemplate, r.category, r.targetType, r.targetNamesJson, r.priority, r.enabled?1:0, r.createdAt, r.updatedAt, t, now, r.deleted?1:0]
      );
    }
    
    for (const m of aiModelConfigs) {
      await pool.query(
        'INSERT INTO ai_model_configs (id,model_type,model_name,api_key,api_endpoint,temperature,max_tokens,is_default,is_enabled,monthly_cost,last_used,created_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15) ON CONFLICT (id) DO UPDATE SET model_type=EXCLUDED.model_type,model_name=EXCLUDED.model_name,api_key=EXCLUDED.api_key,api_endpoint=EXCLUDED.api_endpoint,temperature=EXCLUDED.temperature,max_tokens=EXCLUDED.max_tokens,is_default=EXCLUDED.is_default,is_enabled=EXCLUDED.is_enabled,monthly_cost=EXCLUDED.monthly_cost,last_used=EXCLUDED.last_used,created_at=EXCLUDED.created_at,tenant_id=EXCLUDED.tenant_id,sync_version=EXCLUDED.sync_version,deleted=EXCLUDED.deleted',
        [m.id, m.modelType, m.modelName, m.apiKey, m.apiEndpoint, m.temperature, m.maxTokens, m.isDefault?1:0, m.isEnabled?1:0, m.monthlyCost, m.lastUsed, m.createdAt, t, now, m.deleted?1:0]
      );
    }
    
    res.json({ code: 0, message: '成功', data: { accepted: true } });
  } catch (e) {
    console.error('[simple-push] Error:', e.message);
    res.status(500).json({ code: 500, message: e.message });
  }
});

module.exports = router;
