const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { v4: uuidv4 } = require('uuid');
const { getDb, queryOne, exec } = require('./db');

const JWT_SECRET = process.env.JWT_SECRET || 'csbaby-dev-secret-key-change-in-production';
const ACCESS_TOKEN_EXPIRY = 3600;
const REFRESH_TOKEN_EXPIRY = 7 * 86400;

function genAccess(userId, tenantId) {
  return jwt.sign({ userId, tenantId, type: 'access' }, JWT_SECRET, { expiresIn: ACCESS_TOKEN_EXPIRY });
}

function genRefresh(userId, tenantId) {
  const token = jwt.sign({ userId, tenantId, type: 'refresh', id: uuidv4() }, JWT_SECRET, { expiresIn: REFRESH_TOKEN_EXPIRY });
  exec('INSERT OR REPLACE INTO refresh_tokens (token, user_id, tenant_id, expires_at) VALUES (?, ?, ?, ?)',
    [token, userId, tenantId, Date.now() + REFRESH_TOKEN_EXPIRY * 1000]);
  return token;
}

function verifyAccess(token) {
  try { const d = jwt.verify(token, JWT_SECRET); return d.type === 'access' ? d : null; } catch { return null; }
}

function verifyRefresh(token) {
  try {
    const d = jwt.verify(token, JWT_SECRET);
    if (d.type !== 'refresh') return null;
    const row = queryOne('SELECT * FROM refresh_tokens WHERE token = ? AND expires_at > ?', [token, Date.now()]);
    return row ? d : null;
  } catch { return null; }
}

async function register(email, password, displayName) {
  await getDb();
  // 验证邮箱格式
  if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    throw new Error('INVALID_EMAIL');
  }
  // 验证密码强度
  if (!password || password.length < 6) {
    throw new Error('WEAK_PASSWORD');
  }
  // 验证显示名称
  if (!displayName || !displayName.trim()) {
    throw new Error('INVALID_DISPLAY_NAME');
  }

  const existing = queryOne('SELECT id FROM users WHERE email = ?', [email]);
  if (existing) throw new Error('EMAIL_EXISTS');

  const userId = uuidv4();
  const tenantId = uuidv4();
  const hash = await bcrypt.hash(password, 10);
  const now = Date.now();

  exec('INSERT INTO users (id, email, password_hash, display_name, tenant_id, created_at) VALUES (?, ?, ?, ?, ?, ?)',
    [userId, email, hash, displayName.trim(), tenantId, now]);
  exec('INSERT OR IGNORE INTO sync_checkpoints (tenant_id) VALUES (?)', [tenantId]);

  return {
    userId, tenantId,
    accessToken: genAccess(userId, tenantId),
    refreshToken: genRefresh(userId, tenantId),
    expiresAt: Date.now() + ACCESS_TOKEN_EXPIRY * 1000
  };
}

async function login(email, password) {
  await getDb();
  const user = queryOne('SELECT * FROM users WHERE email = ?', [email]);
  if (!user || !await bcrypt.compare(password, user.password_hash)) throw new Error('INVALID_CREDENTIALS');

  return {
    userId: user.id, tenantId: user.tenant_id,
    accessToken: genAccess(user.id, user.tenant_id),
    refreshToken: genRefresh(user.id, user.tenant_id),
    expiresAt: Date.now() + ACCESS_TOKEN_EXPIRY * 1000
  };
}

function refreshTokens(token) {
  const d = verifyRefresh(token);
  if (!d) throw new Error('INVALID_REFRESH_TOKEN');
  exec('DELETE FROM refresh_tokens WHERE token = ?', [token]);
  return {
    userId: d.userId, tenantId: d.tenantId,
    accessToken: genAccess(d.userId, d.tenantId),
    refreshToken: genRefresh(d.userId, d.tenantId),
    expiresAt: Date.now() + ACCESS_TOKEN_EXPIRY * 1000
  };
}

function authMiddleware(req, res, next) {
  const h = req.headers.authorization;
  if (!h || !h.startsWith('Bearer ')) return res.status(401).json({ code: 401, message: '未提供认证令牌' });
  const d = verifyAccess(h.slice(7));
  if (!d) return res.status(401).json({ code: 401, message: '令牌无效或已过期' });
  req.userId = d.userId;
  req.tenantId = d.tenantId;
  next();
}

module.exports = { register, login, refreshTokens, authMiddleware };
