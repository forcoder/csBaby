const express = require('express');
const cors = require('cors');
const { register, login, refreshTokens } = require('./auth');
const syncRouter = require('./sync');
const otaRouter = require('./ota');
const backupRouter = require('./backup');
const { getDb, getPool } = require('./db');

const app = express();
const PORT = process.env.PORT || 8080;

app.use(cors());
app.use(express.json({ limit: '10mb' }));

app.get('/', (req, res) => {
  res.json({ status: 'ok', service: 'csbaby-sync-server', version: '1.0.13', ts: Date.now() });
});

// Auth routes
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
    console.error('register error:', e);
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
    console.error('login error:', e);
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

// API routes
app.use('/sync', syncRouter);
app.use('/api/v1/ota', otaRouter);
app.use('/api/v1/backup', backupRouter);

// Start server
getDb().then(() => {
  app.listen(PORT, () => console.log(`Server started on port ${PORT}`));
}).catch(e => {
  console.error('DB init failed:', e);
  process.exit(1);
});
