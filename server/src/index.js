const express = require('express');
const cors = require('cors');
const { register, login, refreshTokens, authMiddleware } = require('./auth');
const syncRouter = require('./sync');
const otaRouter = require('./ota');
const backupRouter = require('./backup');
const { getDb, getPool } = require('./db');

const app = express();
const PORT = process.env.PORT || 8080;

app.use(cors());
app.use(express.json({ limit: '10mb' }));

app.get('/', (req, res) => {
  res.json({ status: 'ok', service: 'csbaby-sync-server', version: '1.0.100', ts: Date.now() });
});

// 最简单的测试
app.get('/diagnose', async (req, res) => {
  const { getPool, exec, queryAll } = require('./db');
  const results = {};
  try {
    const pool = getPool();
    results.poolCreated = true;
    try {
      const r1 = await pool.query('SELECT 1 as val');
      results.select1 = r1.rows[0];
    } catch (e) { results.select1Err = e.message; }
    try {
      const now = Date.now();
      const r2 = await pool.query('INSERT INTO keyword_rules (keyword,match_type,reply_template,category,target_type,target_names_json,priority,enabled,created_at,updated_at,tenant_id,sync_version,deleted) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)', ['diag-test', 'CONTAINS', 'test', 'test', 'ALL', '[]', 0, 1, now, now, 'fc0807c8-38ff-4c34-8fc2-b61dd1ce582d', now, 0]);
      results.insert = { rowCount: r2.rowCount };
    } catch (e) { results.insertErr = e.message; }
    try {
      const r3 = await queryAll('SELECT COUNT(*) as cnt FROM keyword_rules WHERE keyword=$1', ['diag-test']);
      results.countAfterInsert = r3[0];
    } catch (e) { results.countErr = e.message; }
    try {
      await exec('DELETE FROM keyword_rules WHERE keyword=$1', ['diag-test']);
      results.deleteExec = 'ok';
    } catch (e) { results.deleteErr = e.message; }
  } catch (e) {
    results.fatalErr = e.message;
  }
  res.json({ code: 0, data: results });
});
});

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

// OTA 更新路由
app.use('/api/v1/ota', otaRouter);

// 数据备份路由
app.use('/api/v1/backup', backupRouter);

// 等待数据库初始化后启动
getDb().then(() => {
  app.listen(PORT, () => {
    console.log(`Server started on port ${PORT}`);
  });
}).catch(e => {
  console.error('DB init failed:', e);
  process.exit(1);
});
