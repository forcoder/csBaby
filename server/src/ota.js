const { Router } = require('express');
const { getDb, queryAll, queryOne, exec } = require('./db');
const { authMiddleware } = require('./auth');

const router = Router();

// 公开接口：检查更新（不需要登录）
router.get('/check', async (req, res) => {
  try {
    await getDb();
    const currentVersion = parseInt(req.query.versionCode) || 0;
    const channel = req.query.channel || 'default';

    const latest = queryOne(
      'SELECT * FROM ota_versions WHERE channel=? AND is_published=1 ORDER BY version_code DESC LIMIT 1',
      [channel]
    );

    if (!latest) {
      return res.json({ code: 0, message: '暂无可用版本', data: null });
    }

    if (latest.version_code <= currentVersion) {
      return res.json({ code: 0, message: '已是最新版本', data: null });
    }

    res.json({
      code: 0, message: '有新版本',
      data: {
        versionCode: latest.version_code,
        versionName: latest.version_name,
        downloadUrl: latest.download_url,
        fileSize: latest.file_size,
        md5: latest.md5 || '',
        releaseNotes: latest.release_notes || '',
        releaseDate: latest.release_date ? String(latest.release_date) : '',
        isForceUpdate: !!latest.is_force_update,
        minRequiredVersion: latest.min_required_version || 1,
        channel: latest.channel
      }
    });
  } catch (e) {
    console.error('OTA check error:', e);
    res.status(500). json({ code: 500, message: e.message });
  }
});

// ========== 以下需要登录 ==========
router.use(authMiddleware);

// 获取最新版本列表（管理员功能）
router.get('/versions', async (req, res) => {
  try {
    await getDb();
    const limit = Math.min(parseInt(req.query.limit) || 50, 100);
    const offset = parseInt(req.query.offset) || 0;
    const versions = queryAll(
      'SELECT id,version_code,version_name,channel,is_published,is_force_update,release_date,file_size,created_at FROM ota_versions ORDER BY version_code DESC LIMIT ? OFFSET ?',
      [limit, offset]
    );
    res.json({ code: 0, message: '成功', data: versions });
  } catch (e) {
    res.status(500).json({ code: 500, message: e.message });
  }
});

// 管理员权限检查中间件
function adminMiddleware(req, res, next) {
  const adminKey = process.env.ADMIN_KEY || 'csbaby-admin-secret';
  const providedKey = req.headers['x-admin-key'] || req.body.adminKey;
  if (providedKey !== adminKey) {
    return res.status(403).json({ code: 403, message: '需要管理员权限' });
  }
  next();
}

// 发布新版本（管理员功能）
router.post('/versions', adminMiddleware, async (req, res) => {
  try {
    await getDb();
    const { versionCode, versionName, downloadUrl, fileSize, md5, releaseNotes, channel, isForceUpdate, minRequiredVersion } = req.body;
    const notes = releaseNotes || req.body.release_notes || '';

    if (!versionCode || !versionName || !downloadUrl) {
      return res.status(400).json({ code: 400, message: '缺少必填字段: versionCode, versionName, downloadUrl' });
    }

    const now = Date.now();
    exec(
      'INSERT OR REPLACE INTO ota_versions (version_code,version_name,download_url,file_size,md5,release_notes,channel,is_force_update,min_required_version,is_published,release_date,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',
      [versionCode, versionName, downloadUrl, fileSize || 0, md5 || '', notes, channel || 'default', isForceUpdate ? 1 : 0, minRequiredVersion || 1, 1, now, now]
    );

    res.json({ code: 0, message: '版本发布成功', data: { versionCode, versionName } });
  } catch (e) {
    console.error('OTA publish error:', e);
    res.status(500).json({ code: 500, message: e.message });
  }
});

// 下架版本（管理员功能）
router.delete('/versions/:versionCode', adminMiddleware, async (req, res) => {
  try {
    await getDb();
    const vc = parseInt(req.params.versionCode);
    exec('UPDATE ota_versions SET is_published=0 WHERE version_code=?', [vc]);
    res.json({ code: 0, message: '版本已下架' });
  } catch (e) {
    res.status(500).json({ code: 500, message: e.message });
  }
});

module.exports = router;
