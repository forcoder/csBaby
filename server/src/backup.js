const { Router } = require('express');
const { getDb, queryAll, queryOne, exec } = require('./db');
const { authMiddleware } = require('./auth');

const router = Router();
router.use(authMiddleware);

// ========== 数据备份 ==========

/**
 * 上传备份
 * 客户端将本地所有数据序列化为 JSON 上传，服务端存储
 */
router.post('/upload', async (req, res) => {
  try {
    await getDb();
    const t = req.tenantId;
    const { deviceName, appVersion, data, checksum } = req.body;

    if (!data) {
      return res.status(400).json({ code: 400, message: '缺少备份数据' });
    }

    const now = Date.now();
    const dataJson = typeof data === 'string' ? data : JSON.stringify(data);
    const dataSize = Buffer.byteLength(dataJson, 'utf8');

    // 限制单用户最多 5 份备份
    const existing = queryAll(
      'SELECT id FROM backup_records WHERE tenant_id=? ORDER BY created_at ASC',
      [t]
    );
    if (existing.length >= 5) {
      // 删除最旧的
      exec('DELETE FROM backup_records WHERE id=?', [existing[0].id]);
    }

    exec(
      'INSERT INTO backup_records (tenant_id,device_name,app_version,data_json,data_size,checksum,created_at) VALUES (?,?,?,?,?,?,?)',
      [t, deviceName || '未知设备', appVersion || '', dataJson, dataSize || 0, checksum || '', now]
    );

    const record = queryOne(
      'SELECT id,device_name,app_version,data_size,checksum,created_at FROM backup_records WHERE tenant_id=? ORDER BY created_at DESC LIMIT 1',
      [t]
    );

    res.json({
      code: 0, message: '备份成功',
      data: {
        id: record.id,
        deviceName: record.device_name,
        appVersion: record.app_version,
        dataSize: record.data_size,
        checksum: record.checksum,
        createdAt: record.created_at
      }
    });
  } catch (e) {
    console.error('Backup upload error:', e);
    res.status(500).json({ code: 500, message: e.message });
  }
});

/**
 * 获取备份列表
 */
router.get('/list', async (req, res) => {
  try {
    await getDb();
    const t = req.tenantId;

    const records = queryAll(
      'SELECT id,device_name,app_version,data_size,checksum,created_at FROM backup_records WHERE tenant_id=? ORDER BY created_at DESC',
      [t]
    ).map(r => ({
      id: r.id,
      deviceName: r.device_name,
      appVersion: r.app_version,
      dataSize: r.data_size,
      checksum: r.checksum,
      createdAt: r.created_at
    }));

    res.json({ code: 0, message: '成功', data: records });
  } catch (e) {
    res.status(500).json({ code: 500, message: e.message });
  }
});

/**
 * 下载备份数据
 */
router.get('/download/:id', async (req, res) => {
  try {
    await getDb();
    const t = req.tenantId;
    const id = parseInt(req.params.id);

    const record = queryOne(
      'SELECT * FROM backup_records WHERE id=? AND tenant_id=?',
      [id, t]
    );

    if (!record) {
      return res.status(404).json({ code: 404, message: '备份不存在' });
    }

    const raw = JSON.parse(record.data_json);
    res.json({
      code: 0, message: '成功',
      data: {
        id: record.id,
        deviceName: record.device_name,
        appVersion: record.app_version,
        data: raw,
        checksum: record.checksum,
        createdAt: record.created_at,
        dataSize: record.data_size
      }
    });
  } catch (e) {
    console.error('Backup download error:', e);
    res.status(500).json({ code: 500, message: e.message });
  }
});

/**
 * 删除备份
 */
router.delete('/:id', async (req, res) => {
  try {
    await getDb();
    const t = req.tenantId;
    const id = parseInt(req.params.id);

    exec('DELETE FROM backup_records WHERE id=? AND tenant_id=?', [id, t]);

    res.json({ code: 0, message: '备份已删除' });
  } catch (e) {
    res.status(500).json({ code: 500, message: e.message });
  }
});

module.exports = router;
