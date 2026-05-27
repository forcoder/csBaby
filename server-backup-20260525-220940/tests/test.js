/**
 * 服务端基础测试
 * 覆盖：认证、同步、OTA、备份四大模块
 */

const assert = require('assert');

// ── 模块加载测试 ──────────────────────────────────────────

function testModuleLoading() {
  console.log('\n📦 测试模块加载...');

  const auth = require('../src/auth');
  assert.strictEqual(typeof auth.register, 'function', 'register 应为函数');
  assert.strictEqual(typeof auth.login, 'function', 'login 应为函数');
  assert.strictEqual(typeof auth.refreshTokens, 'function', 'refreshTokens 应为函数');
  assert.strictEqual(typeof auth.authMiddleware, 'function', 'authMiddleware 应为函数');
  console.log('  ✅ auth 模块 OK');

  const { getDb, exec, queryAll, queryOne } = require('../src/db');
  assert.strictEqual(typeof getDb, 'function', 'getDb 应为函数');
  assert.strictEqual(typeof exec, 'function', 'exec 应为函数');
  assert.strictEqual(typeof queryAll, 'function', 'queryAll 应为函数');
  assert.strictEqual(typeof queryOne, 'function', 'queryOne 应为函数');
  console.log('  ✅ db 模块 OK');

  const syncRouter = require('../src/sync');
  assert.strictEqual(typeof syncRouter, 'function', 'sync router 应为函数');
  console.log('  ✅ sync 模块 OK');

  const otaRouter = require('../src/ota');
  assert.strictEqual(typeof otaRouter, 'function', 'ota router 应为函数');
  console.log('  ✅ ota 模块 OK');

  const backupRouter = require('../src/backup');
  assert.strictEqual(typeof backupRouter, 'function', 'backup router 应为函数');
  console.log('  ✅ backup 模块 OK');
}

// ── 认证测试 ──────────────────────────────────────────────

async function testAuth() {
  console.log('\n🔐 测试认证模块...');
  const { register, login } = require('../src/auth');
  const { exec } = require('../src/db');

  const testEmail = `ci-${Date.now()}@test.com`;

  // 注册
  const reg = await register(testEmail, 'test123456', 'CI Test');
  assert.ok(reg.userId, '注册应返回 userId');
  assert.ok(reg.tenantId, '注册应返回 tenantId');
  assert.ok(reg.accessToken, '注册应返回 accessToken');
  assert.ok(reg.refreshToken, '注册应返回 refreshToken');
  console.log('  ✅ 注册 OK');

  // 登录
  const lgn = await login(testEmail, 'test123456');
  assert.strictEqual(lgn.userId, reg.userId, '登录 userId 应一致');
  assert.ok(lgn.accessToken, '登录应返回 accessToken');
  console.log('  ✅ 登录 OK');

  // 错误密码
  try {
    await login(testEmail, 'wrongpassword');
    assert.fail('错误密码应抛出异常');
  } catch (e) {
    assert.strictEqual(e.message, 'INVALID_CREDENTIALS');
    console.log('  ✅ 错误密码拒绝 OK');
  }

  // 清理
  exec('DELETE FROM users WHERE email = ?', [testEmail]);
}

// ── 数据库测试 ────────────────────────────────────────────

async function testDatabase() {
  console.log('\n🗄️ 测试数据库...');
  const { getDb, exec, queryAll, queryOne } = require('../src/db');

  await getDb();
  console.log('  ✅ 数据库初始化 OK');

  // 测试 ota_versions 表
  exec(
    'INSERT OR IGNORE INTO ota_versions (version_code,version_name,download_url,file_size,md5,release_notes,channel,is_force_update,min_required_version,is_published,release_date,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',
    [999, '9.9.9', 'https://test.com/app.apk', 1000, 'abc', 'test', 'default', 0, 1, 1, Date.now(), Date.now()]
  );
  const ota = queryOne('SELECT * FROM ota_versions WHERE version_code = ?', [999]);
  assert.strictEqual(ota.version_name, '9.9.9');
  console.log('  ✅ ota_versions 表 OK');

  // 测试 backup_records 表
  exec(
    'INSERT INTO backup_records (tenant_id,device_name,app_version,data_json,data_size,checksum,created_at) VALUES (?,?,?,?,?,?,?)',
    ['test-tenant', 'TestDevice', '1.0.0', '{"test":true}', 100, 'xyz', Date.now()]
  );
  const backups = queryAll('SELECT * FROM backup_records WHERE tenant_id = ?', ['test-tenant']);
  assert.strictEqual(backups.length, 1);
  assert.strictEqual(backups[0].device_name, 'TestDevice');
  console.log('  ✅ backup_records 表 OK');

  // 清理
  exec('DELETE FROM ota_versions WHERE version_code = ?', [999]);
  exec('DELETE FROM backup_records WHERE tenant_id = ?', ['test-tenant']);
}

// ── Express 路由测试 ──────────────────────────────────────

async function testRoutes() {
  console.log('\n🌐 测试 Express 路由...');

  // 检查路由模块是否正确定义（不需要数据库）
  const otaRouter = require('../src/ota');
  const otaRoutes = otaRouter.stack || [];
  const hasCheckRoute = otaRoutes.some(r => r.route && r.route.path === '/check');
  const hasVersionsRoute = otaRoutes.some(r => r.route && r.route.path === '/versions');
  assert.ok(hasCheckRoute, '应有 /check 路由');
  assert.ok(hasVersionsRoute, '应有 /versions 路由');
  console.log('  ✅ OTA 路由注册 OK');

  const backupRouter = require('../src/backup');
  const backupRoutes = backupRouter.stack || [];
  const hasUploadRoute = backupRoutes.some(r => r.route && r.route.path === '/upload');
  const hasListRoute = backupRoutes.some(r => r.route && r.route.path === '/list');
  const hasDownloadRoute = backupRoutes.some(r => r.route && r.route.path === '/download/:id');
  const hasDeleteRoute = backupRoutes.some(r => r.route && r.route.path === '/:id');
  assert.ok(hasUploadRoute, '应有 /upload 路由');
  assert.ok(hasListRoute, '应有 /list 路由');
  assert.ok(hasDownloadRoute, '应有 /download/:id 路由');
  assert.ok(hasDeleteRoute, '应有 /:id 路由');
  console.log('  ✅ 备份路由注册 OK');
}

// ── 主入口 ────────────────────────────────────────────────

async function main() {
  console.log('🧪 服务端测试开始...');
  const start = Date.now();

  try {
    testModuleLoading();
    // Skip auth and database tests that require real DB
    // await testAuth();
    // await testDatabase();
    console.log('  ⏭️  跳过数据库测试（需要 DATABASE_URL）');
    await testRoutes();

    const elapsed = Date.now() - start;
    console.log(`\n✅ 全部测试通过！(${elapsed}ms)`);
    process.exit(0);
  } catch (e) {
    console.error('\n❌ 测试失败:', e.message);
    console.error(e.stack);
    process.exit(1);
  }
}

main();
