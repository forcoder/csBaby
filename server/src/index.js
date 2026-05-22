const express = require('express');
const cors = require('cors');
const { register, login, refreshTokens, authMiddleware } = require('./auth');
const syncRouter = require('./sync');
const syncSimpleRouter = require('./sync-simple');
const otaRouter = require('./ota');
const backupRouter = require('./backup');
const { getDb } = require('./db');

const app = express();
const PORT = process.env.PORT || 8080;

app.use(cors());
app.use(express.json({ limit: '10mb' }));

// 健康检查
app.get('/', (req, res) => {
  res.json({ status: 'ok', service: 'csbaby-sync-server', version: '1.0.0' });
});

// 直接在 index.js 中的测试端点 - 不需要任何外部模块
app.post('/direct-push', async (req, res) => {
  console.log('[direct-push] Starting...');
  const t = req.body.tenantId || req.query.tenantId;
  const { keywordRules = [] } = req.body;

  if (!t) {
    return res.status(400).json({ code: 400, message: '缺少 tenantId' });
  }

  const now = Date.now();
  const { getDb, exec } = require('./db');

  try {
    await getDb();
    console.log('[direct-push] DB initialized, inserting', keywordRules.length, 'rules');

    for (const r of keywordRules) {
      await exec(
        'INSERT INTO keyword_rules (id,keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)',
        [r.id || Date.now(), r.keyword || 'test', r.matchType || 'CONTAINS', r.replyTemplate || 'reply', r.category || '', r.targetType || 'ALL', r.targetNamesJson || '[]', r.priority || 0, r.enabled !== false ? 1 : 0, now, now, t, now, 0]
      );
    }

    console.log('[direct-push] Success!');
    res.json({ code: 0, message: '成功', data: { accepted: true } });
  } catch (e) {
    console.error('[direct-push] Error:', e.message);
    res.status(500).json({ code: 500, message: e.message });
  }
});

app.use('/sync', syncRouter);
app.use('/sync-simple', syncSimpleRouter);

// 认证路由
app.post('/auth/register', async (req, res) => {
  try {
    const { email, password, displayName } = req.body;
    if (!email || !password || !displayName) {
      return res.status(400).json({ code: 400, message: '缺少必填字段' });
    }
    const result = await register(email, password, displayName);
    res.json({ code: 0, message: '注册成功', data: result });
  } catch (e) {
    if (e.message === 'EMAIL_EXISTS') return res.status(409).json({ code: 409, message: '该邮箱已被注册' });
    console.error('注册错误:', e);
    res.status(500).json({ code: 500, message: e.message });
  }
});

app.post('/auth/login', async (req, res) => {
  try {
    const { email, password } = req.body;
    if (!email || !password) return res.status(400).json({ code: 400, message: '缺少必填字段' });
    const result = await login(email, password);
    res.json({ code: 0, message: '登录成功', data: result });
  } catch (e) {
    if (e.message === 'INVALID_CREDENTIALS') return res.status(401).json({ code: 401, message: '邮箱或密码错误' });
    console.error('登录错误:', e);
    res.status(500).json({ code: 500, message: e.message });
  }
});

app.post('/auth/refresh', (req, res) => {
  try {
    const { refreshToken } = req.body;
    if (!refreshToken) return res.status(400).json({ code: 400, message: '缺少 refreshToken' });
    const result = refreshTokens(refreshToken);
    res.json({ code: 0, message: '刷新成功', data: result });
  } catch (e) {
    res.status(401).json({ code: 401, message: '刷新令牌无效或已过期' });
  }
});

// 同步路由
app.use('/sync', syncRouter);

// OTA 更新路由（/api/v1/ota）
app.use('/api/v1/ota', otaRouter);

// 数据备份路由（/api/v1/backup）
app.use('/api/v1/backup', backupRouter);

// 等待数据库初始化后启动
getDb().then(() => {
  app.listen(PORT, () => {
    console.log(`客服小秘同步服务端已启动，端口: ${PORT}`);
  });
}).catch(e => {
  console.error('数据库初始化失败:', e);
  process.exit(1);
});
// v1.2.0 - 2026年05月18日 20:11:54
// test comment
