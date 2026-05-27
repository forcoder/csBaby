# 数据同步功能迁移与增强实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有的Node.js数据同步服务迁移到Python + web.py框架，并实现增强的数据同步功能，包括全量/增量同步、实时同步、冲突解决和版本化备份管理。

**Architecture:** 采用分层架构设计，将数据同步功能分解为控制器层（API接口）、服务层（业务逻辑）、模型层（数据访问）和工具层（通用功能）。保持与原有API接口的兼容性，同时增强同步性能和可靠性。

**Tech Stack:** Python 3.9+, web.py 0.40, PostgreSQL, psycopg2, PyJWT, WebSocket

---

## 文件结构规划

### 核心文件创建
- `csBaby-server-py/app.py` - 应用入口和路由配置
- `csBaby-server-py/config/database.py` - 数据库连接配置
- `csBaby-server-py/models/user.py` - 用户数据模型
- `csBaby-server-py/models/backup.py` - 备份记录模型
- `csBaby-server-py/models/sync_checkpoint.py` - 同步检查点模型
- `csBaby-server-py/controllers/sync_controller.py` - 同步API控制器
- `csBaby-server-py/controllers/backup_controller.py` - 备份API控制器
- `csBaby-server-py/services/sync_service.py` - 同步业务逻辑
- `csBaby-server-py/services/backup_service.py` - 备份业务逻辑
- `csBaby-server-py/utils/auth.py` - 认证授权工具
- `csBaby-server-py/requirements.txt` - Python依赖配置

### 测试文件
- `csBaby-server-py/tests/test_sync.py` - 同步功能测试
- `csBaby-server-py/tests/test_backup.py` - 备份功能测试
- `csBaby-server-py/tests/test_auth.py` - 认证功能测试

## 任务分解

### Task 0: 健康检查API（优先执行）

**Files:**
- Create: `csBaby-server-py/controllers/health_controller.py`
- Modify: `csBaby-server-py/app.py`
- Create: `csBaby-server-py/tests/test_health.py`

- [ ] **Step 1: 创建健康检查控制器**

```python
# csBaby-server-py/controllers/health_controller.py
import web
from datetime import datetime
import os

class HealthCheck:
    def GET(self):
        try:
            from config.database import execute_query
            execute_query("SELECT 1", fetch='one')
            db_status = 'ok'
        except Exception as e:
            db_status = f'error: {str(e)}'
        
        return {
            'status': 'ok' if db_status == 'ok' else 'degraded',
            'service': 'csbaby-sync-server-py',
            'version': '2.0.0',
            'ts': int(datetime.now().timestamp() * 1000),
            'pid': os.getpid(),
            'uptime': int(datetime.now().timestamp()),
            'checks': {'database': db_status}
        }
```

- [ ] **Step 2: 更新应用入口**

```python
# csBaby-server-py/app.py 添加内容
from controllers.health_controller import HealthCheck

urls = (
    '/', 'Index',
    '/health', 'HealthCheck',  # 添加健康检查路由
    '/auth/register', 'Register',
    # ... 其他路由
)
```

- [ ] **Step 3: 创建测试**

```python
# csBaby-server-py/tests/test_health.py
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

def test_health_controller():
    from controllers.health_controller import HealthCheck
    assert HealthCheck is not None
    assert hasattr(HealthCheck, 'GET')
```

- [ ] **Step 4: 运行测试**

Run: `cd csBaby-server-py && python -m pytest tests/test_health.py -v`
Expected: 1 passed

- [ ] **Step 5: 提交代码**

```bash
cd csBaby-server-py
git add controllers/health_controller.py tests/test_health.py app.py
git commit -m "feat: 添加健康检查API用于保活监控"
```

### Task 1: 项目基础搭建

**Files:**
- Create: `csBaby-server-py/requirements.txt`
- Create: `csBaby-server-py/config/database.py`
- Create: `csBaby-server-py/config/__init__.py`

- [ ] **Step 1: 创建Python依赖文件**

```txt
web.py==0.40
psycopg2-binary==2.9.7
PyJWT==2.8.0
bcrypt==4.0.1
python-dotenv==1.0.0
```

- [ ] **Step 2: 创建数据库配置模块**

```python
# csBaby-server-py/config/database.py
import os
import psycopg2
from psycopg2 import pool

class DatabaseConfig:
    _pool = None
    
    @classmethod
    def get_pool(cls):
        if cls._pool is None:
            cls._pool = psycopg2.pool.ThreadedConnectionPool(
                minconn=1,
                maxconn=10,
                database=os.getenv('DB_NAME', 'csbaby'),
                user=os.getenv('DB_USER', 'postgres'),
                password=os.getenv('DB_PASSWORD', 'postgres'),
                host=os.getenv('DB_HOST', 'localhost'),
                port=os.getenv('DB_PORT', '5432')
            )
        return cls._pool
    
    @classmethod
    def get_connection(cls):
        return cls.get_pool().getconn()
    
    @classmethod
    def return_connection(cls, conn):
        cls.get_pool().putconn(conn)

def execute_query(sql, params=None, fetch='all'):
    conn = DatabaseConfig.get_connection()
    try:
        cursor = conn.cursor()
        cursor.execute(sql, params or ())
        if fetch == 'one':
            result = cursor.fetchone()
        else:
            result = cursor.fetchall()
        conn.commit()
        return result
    except Exception as e:
        conn.rollback()
        raise e
    finally:
        DatabaseConfig.return_connection(conn)

def execute_update(sql, params=None):
    conn = DatabaseConfig.get_connection()
    try:
        cursor = conn.cursor()
        cursor.execute(sql, params or ())
        conn.commit()
        return cursor.rowcount
    except Exception as e:
        conn.rollback()
        raise e
    finally:
        DatabaseConfig.return_connection(conn)
```

- [ ] **Step 3: 创建 `__init__.py` 文件**

```python
# csBaby-server-py/config/__init__.py
from .database import DatabaseConfig, execute_query, execute_update
```

- [ ] **Step 4: 验证项目结构**

Run: `python -c "from config.database import DatabaseConfig; print('Database config OK')"`
Expected: Database config OK

- [ ] **Step 5: 提交代码**

```bash
cd csBaby-server-py
---

## 任务 3: 同步功能实现

**Files:**
- Create: `csBaby-server-py/models/sync_checkpoint.py`
- Create: `csBaby-server-py/services/sync_service.py`
- Create: `csBaby-server-py/services/__init__.py`
- Create: `csBaby-server-py/controllers/sync_controller.py`
- Create: `csBaby-server-py/tests/test_sync.py`

- [ ] **Step 1: 创建同步检查点模型**

```python
# csBaby-server-py/models/sync_checkpoint.py
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config.database import execute_query, execute_update
from datetime import datetime

def get_checkpoint(tenant_id):
    result = execute_query(
        "SELECT * FROM sync_checkpoints WHERE tenant_id=%s",
        (tenant_id,), fetch='one'
    )
    if not result:
        return None
    return {
        'tenant_id': result[0], 'last_sync_time': result[1],
        'is_syncing': result[2], 'last_error': result[3],
        'device_info': json.loads(result[4]) if result[4] else None,
        'created_at': result[5], 'updated_at': result[6]
    }

def update_checkpoint(tenant_id, last_sync_time, is_syncing=False, last_error=None, device_info=None):
    now = int(datetime.now().timestamp() * 1000)
    existing = get_checkpoint(tenant_id)
    if existing:
        execute_update(
            """UPDATE sync_checkpoints SET last_sync_time=%s, is_syncing=%s, 
               last_error=%s, device_info=%s, updated_at=%s WHERE tenant_id=%s""",
            (last_sync_time, is_syncing, last_error, 
             json.dumps(device_info) if device_info else None, now, tenant_id)
        )
    else:
        execute_update(
            """INSERT INTO sync_checkpoints 
               (tenant_id, last_sync_time, is_syncing, last_error, device_info, created_at, updated_at)
               VALUES (%s, %s, %s, %s, %s, %s, %s)""",
            (tenant_id, last_sync_time, is_syncing, last_error,
             json.dumps(device_info) if device_info else None, now, now)
        )
```

- [ ] **Step 2: 创建同步服务**

```python
# csBaby-server-py/services/sync_service.py
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config.database import execute_query, execute_update
from models.sync_checkpoint import update_checkpoint
from datetime import datetime

def to_rule(r):
    return {'id': r[0], 'keyword': r[1], 'matchType': r[2], 'replyTemplate': r[3],
            'category': r[4], 'targetType': r[5], 'targetNamesJson': r[6],
            'priority': r[7], 'enabled': bool(r[8]), 'createdAt': r[9], 'updatedAt': r[10],
            'tenantId': r[11], 'syncVersion': r[12], 'deleted': bool(r[13])}

def to_model(m):
    return {'id': m[0], 'modelType': m[1], 'modelName': m[2], 'apiKey': m[3],
            'apiEndpoint': m[4], 'temperature': m[5], 'maxTokens': m[6],
            'isDefault': bool(m[7]), 'isEnabled': bool(m[8]), 'monthlyCost': m[9],
            'lastUsed': m[10], 'createdAt': m[11], 'tenantId': m[12],
            'syncVersion': m[13], 'deleted': bool(m[14])}

def to_profile(p):
    if not p: return None
    return {'userId': p[0], 'formalityLevel': p[1], 'enthusiasmLevel': p[2],
            'professionalismLevel': p[3], 'wordCountPreference': p[4],
            'commonPhrases': p[5], 'avoidPhrases': p[6], 'learningSamples': p[7],
            'accuracyScore': p[8], 'lastTrained': p[9], 'createdAt': p[10],
            'tenantId': p[11], 'syncVersion': p[12], 'deleted': bool(p[13])}

def to_app(a):
    return {'packageName': a[0], 'appName': a[1], 'iconUri': a[2],
            'isMonitored': bool(a[3]), 'createdAt': a[4], 'lastUsed': a[5],
            'tenantId': a[6], 'syncVersion': a[7], 'deleted': bool(a[8])}

def to_scenario(s):
    return {'id': s[0], 'name': s[1], 'type': s[2], 'targetId': s[3],
            'description': s[4], 'createdAt': s[5], 'tenantId': s[6],
            'syncVersion': s[7], 'deleted': bool(s[8])}

def to_reply(h):
    return {'id': h[0], 'sourceApp': h[1], 'originalMessage': h[2],
            'generatedReply': h[3], 'finalReply': h[4], 'ruleMatchedId': h[5],
            'modelUsedId': h[6], 'styleApplied': bool(h[7]), 'sendTime': h[8],
            'modified': bool(h[9]), 'tenantId': h[10], 'syncVersion': h[11], 'deleted': bool(h[12])}

def to_blacklist(b):
    return {'id': b[0], 'type': b[1], 'value': b[2], 'description': b[3],
            'packageName': b[4], 'createdAt': b[5], 'isEnabled': bool(b[6]),
            'tenantId': b[7], 'syncVersion': b[8], 'deleted': bool(b[9])}

class SyncService:
    ENTITY_TABLES = {
        'keyword_rules': 'keyword_rules', 'ai_model_configs': 'ai_model_configs',
        'user_style_profiles': 'user_style_profiles', 'app_configs': 'app_configs',
        'scenarios': 'scenarios', 'reply_history': 'reply_history',
        'message_blacklist': 'message_blacklist'
    }
    
    def full_sync(self, tenant_id):
        now = int(datetime.now().timestamp() * 1000)
        keyword_rules = execute_query(
            "SELECT * FROM keyword_rules WHERE tenant_id=%s AND deleted=0 ORDER BY priority DESC",
            (tenant_id,)
        )
        ai_models = execute_query(
            "SELECT * FROM ai_model_configs WHERE tenant_id=%s AND deleted=0", (tenant_id,)
        )
        profile = execute_query(
            "SELECT * FROM user_style_profiles WHERE tenant_id=%s AND deleted=0",
            (tenant_id,), fetch='one'
        )
        apps = execute_query(
            "SELECT * FROM app_configs WHERE tenant_id=%s AND deleted=0", (tenant_id,)
        )
        scenarios = execute_query(
            "SELECT * FROM scenarios WHERE tenant_id=%s AND deleted=0", (tenant_id,)
        )
        replies = execute_query(
            "SELECT * FROM reply_history WHERE tenant_id=%s AND deleted=0 LIMIT 500", (tenant_id,)
        )
        blacklist = execute_query(
            "SELECT * FROM message_blacklist WHERE tenant_id=%s AND deleted=0", (tenant_id,)
        )
        return {
            'keywordRules': [to_rule(r) for r in keyword_rules],
            'aiModelConfigs': [to_model(m) for m in ai_models],
            'userStyleProfile': to_profile(profile),
            'appConfigs': [to_app(a) for a in apps],
            'scenarios': [to_scenario(s) for s in scenarios],
            'replyHistory': [to_reply(h) for h in replies],
            'messageBlacklist': [to_blacklist(b) for b in blacklist],
            'serverTime': now
        }
    
    def incremental_sync(self, tenant_id, since, page=1, limit=100):
        now = int(datetime.now().timestamp() * 1000)
        deleted_ids = {}
        for entity_name, table in self.ENTITY_TABLES.items():
            result = execute_query(
                f"SELECT id FROM {table} WHERE tenant_id=%s AND sync_version>%s AND deleted=1",
                (tenant_id, since)
            )
            if result:
                deleted_ids[entity_name] = [str(r[0]) for r in result]
        
        keyword_rules = execute_query(
            "SELECT * FROM keyword_rules WHERE tenant_id=%s AND sync_version>%s LIMIT %s OFFSET %s",
            (tenant_id, since, limit, (page-1)*limit)
        )
        return {
            'keywordRules': [to_rule(r) for r in keyword_rules],
            'deletedIds': deleted_ids,
            'serverTime': now, 'page': page, 'limit': limit,
            'hasMore': len(keyword_rules) >= limit
        }
    
    def push_changes(self, tenant_id, data):
        now = int(datetime.now().timestamp() * 1000)
        stats = {'inserted': 0}
        for r in data.get('keywordRules', []):
            execute_update(
                """INSERT INTO keyword_rules (id, keyword, match_type, reply_template, category,
                    target_type, target_names_json, priority, enabled, created_at, updated_at,
                    tenant_id, sync_version, deleted)
                   VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
                   ON CONFLICT (id) DO UPDATE SET
                   keyword=EXCLUDED.keyword, sync_version=EXCLUDED.sync_version""",
                (r['id'], r.get('keyword'), r.get('matchType'), r.get('replyTemplate'),
                 r.get('category'), r.get('targetType'), r.get('targetNamesJson'),
                 r.get('priority', 0), 1 if r.get('enabled') else 0,
                 r.get('createdAt', now), r.get('updatedAt', now),
                 tenant_id, now, 1 if r.get('deleted') else 0)
            )
            stats['inserted'] += 1
        update_checkpoint(tenant_id, now)
        return {'accepted': True, 'conflicts': [], 'newServerVersion': now, 'serverTime': now, 'stats': stats}
```

- [ ] **Step 3: 创建同步控制器**

```python
# csBaby-server-py/controllers/sync_controller.py
import web, json
from services.sync_service import SyncService
from utils.auth import require_auth

class Sync:
    @require_auth
    def GET(self):
        try:
            user_data = web.input(since=0, page=1, limit=100)
            tenant_id = web.ctx.tenant_id
            service = SyncService()
            if int(user_data.since) == 0:
                result = service.full_sync(tenant_id)
            else:
                result = service.incremental_sync(tenant_id, int(user_data.since), 
                                                  int(user_data.page), min(int(user_data.limit), 100))
            return json.dumps({'code': 0, 'message': '成功', 'data': result})
        except Exception as e:
            web.ctx.status = '500 Internal Server Error'
            return json.dumps({'code': 500, 'message': str(e)})

class SyncPush:
    @require_auth
    def POST(self):
        try:
            tenant_id = web.ctx.tenant_id
            data = json.loads(web.data())
            service = SyncService()
            result = service.push_changes(tenant_id, data)
            return json.dumps({'code': 0, 'message': '成功', 'data': result})
        except Exception as e:
            web.ctx.status = '500 Internal Server Error'
            return json.dumps({'code': 500, 'message': str(e)})
```

- [ ] **Step 4: 创建同步测试**

```python
# csBaby-server-py/tests/test_sync.py
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from services.sync_service import SyncService, to_rule, to_model, to_profile

def test_entity_transformers():
    rule_tuple = ('123', 'test', 'CONTAINS', 'Reply', 'cat', 'ALL', '[]', 0, True, 1000, 2000, 't1', 3000, False)
    rule = to_rule(rule_tuple)
    assert rule['id'] == '123'
    assert rule['keyword'] == 'test'
    assert rule['enabled'] == True
    assert rule['deleted'] == False
    
    model_tuple = ('456', 'openai', 'GPT-4', 'key', 'url', 0.7, 1000, True, True, 100, 2000, 3000, 't1', 4000, False)
    model = to_model(model_tuple)
    assert model['id'] == '456'
    assert model['isDefault'] == True
    
    profile_tuple = ('u1', 0.5, 0.6, 0.7, 50, '[]', '[]', '[]', 0.8, 1000, 2000, 't1', 3000, False)
    profile = to_profile(profile_tuple)
    assert profile['userId'] == 'u1'
    assert profile['formalityLevel'] == 0.5

def test_sync_service():
    service = SyncService()
    assert hasattr(service, 'full_sync')
    assert hasattr(service, 'incremental_sync')
    assert hasattr(service, 'push_changes')
```

- [ ] **Step 5: 运行同步测试**

Run: `cd csBaby-server-py && python -m pytest tests/test_sync.py -v`
Expected: 2 passed

- [ ] **Step 6: 提交代码**

```bash
cd csBaby-server-py
git add models/sync_checkpoint.py services/ controllers/ tests/
git commit -m "feat: 同步功能实现"
```

---

## 任务 4: 备份功能实现

**Files:**
- Create: `csBaby-server-py/models/backup.py`
- Create: `csBaby-server-py/services/backup_service.py`
- Create: `csBaby-server-py/controllers/backup_controller.py`
- Create: `csBaby-server-py/tests/test_backup.py`
- Create: `csBaby-server-py/app.py`

- [ ] **Step 1: 创建备份模型**

```python
# csBaby-server-py/models/backup.py
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from config.database import execute_query, execute_update
from datetime import datetime

MAX_BACKUPS_PER_TENANT = 5

def create_backup(tenant_id, device_name, app_version, data, checksum=None, version='1.0', backup_type='manual'):
    now = int(datetime.now().timestamp() * 1000)
    data_json = json.dumps(data) if not isinstance(data, str) else data
    data_size = len(data_json.encode('utf-8'))
    existing = execute_query(
        "SELECT id FROM backup_records WHERE tenant_id=%s ORDER BY created_at ASC", (tenant_id,)
    )
    if len(existing) >= MAX_BACKUPS_PER_TENANT:
        execute_update("DELETE FROM backup_records WHERE id=%s", (existing[0][0],))
    execute_update(
        """INSERT INTO backup_records (tenant_id, device_name, app_version, data_json, data_size, 
           checksum, version, backup_type, created_at) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
        (tenant_id, device_name or '未知设备', app_version or '', data_json, data_size,
         checksum or '', version, backup_type, now)
    )
    record = execute_query(
        "SELECT id, device_name, app_version, data_size, checksum, version, backup_type, created_at FROM backup_records WHERE tenant_id=%s ORDER BY created_at DESC LIMIT 1",
        (tenant_id,), fetch='one'
    )
    return {
        'id': record[0], 'deviceName': record[1], 'appVersion': record[2],
        'dataSize': record[3], 'checksum': record[4], 'version': record[5],
        'backupType': record[6], 'createdAt': record[7]
    }

def list_backups(tenant_id):
    records = execute_query(
        "SELECT id, device_name, app_version, data_size, checksum, version, backup_type, created_at FROM backup_records WHERE tenant_id=%s ORDER BY created_at DESC",
        (tenant_id,)
    )
    return [{
        'id': r[0], 'deviceName': r[1], 'appVersion': r[2],
        'dataSize': r[3], 'checksum': r[4], 'version': r[5],
        'backupType': r[6], 'createdAt': r[7]
    } for r in records]

def get_backup(backup_id, tenant_id):
    record = execute_query(
        "SELECT * FROM backup_records WHERE id=%s AND tenant_id=%s",
        (backup_id, tenant_id), fetch='one'
    )
    if not record:
        return None
    return {
        'id': record[0], 'tenant_id': record[1], 'deviceName': record[2], 'appVersion': record[3],
        'data': json.loads(record[4]), 'checksum': record[5], 'version': record[6],
        'backupType': record[7], 'createdAt': record[8]
    }

def delete_backup(backup_id, tenant_id):
    execute_update("DELETE FROM backup_records WHERE id=%s AND tenant_id=%s", (backup_id, tenant_id))
```

- [ ] **Step 2: 创建备份服务**

```python
# csBaby-server-py/services/backup_service.py
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from models.backup import create_backup, list_backups, get_backup, delete_backup
import hashlib

class BackupService:
    def upload_backup(self, tenant_id, device_name, app_version, data, checksum=None, version='1.0', backup_type='manual'):
        if checksum is None:
            data_str = json.dumps(data) if not isinstance(data, str) else data
            checksum = hashlib.md5(data_str.encode('utf-8')).hexdigest()
        return create_backup(tenant_id, device_name, app_version, data, checksum, version, backup_type)
    
    def get_backup_list(self, tenant_id):
        return list_backups(tenant_id)
    
    def download_backup(self, backup_id, tenant_id):
        backup = get_backup(backup_id, tenant_id)
        if not backup:
            raise Exception('BACKUP_NOT_FOUND')
        return backup
    
    def restore_backup(self, backup_id, tenant_id):
        backup = get_backup(backup_id, tenant_id)
        if not backup:
            raise Exception('BACKUP_NOT_FOUND')
        return backup['data']
    
    def delete_backup(self, backup_id, tenant_id):
        backup = get_backup(backup_id, tenant_id)
        if not backup:
            raise Exception('BACKUP_NOT_FOUND')
        delete_backup(backup_id, tenant_id)
```

- [ ] **Step 3: 创建备份控制器**

```python
# csBaby-server-py/controllers/backup_controller.py
import web, json
from services.backup_service import BackupService
from utils.auth import require_auth

class BackupUpload:
    @require_auth
    def POST(self):
        try:
            data = json.loads(web.data())
            tenant_id = web.ctx.tenant_id
            service = BackupService()
            result = service.upload_backup(
                tenant_id, data.get('deviceName'), data.get('appVersion'),
                data.get('data'), data.get('checksum'), data.get('version', '1.0'),
                data.get('backupType', 'manual')
            )
            return json.dumps({'code': 0, 'message': '备份成功', 'data': result})
        except Exception as e:
            web.ctx.status = '500 Internal Server Error'
            return json.dumps({'code': 500, 'message': str(e)})

class BackupList:
    @require_auth
    def GET(self):
        try:
            tenant_id = web.ctx.tenant_id
            service = BackupService()
            result = service.get_backup_list(tenant_id)
            return json.dumps({'code': 0, 'message': '成功', 'data': result})
        except Exception as e:
            web.ctx.status = '500 Internal Server Error'
            return json.dumps({'code': 500, 'message': str(e)})

class BackupDownload:
    @require_auth
    def GET(self, backup_id):
        try:
            tenant_id = web.ctx.tenant_id
            service = BackupService()
            result = service.download_backup(int(backup_id), tenant_id)
            return json.dumps({'code': 0, 'message': '成功', 'data': result})
        except Exception as e:
            if 'BACKUP_NOT_FOUND' in str(e):
                web.ctx.status = '404 Not Found'
                return json.dumps({'code': 404, 'message': '备份不存在'})
            web.ctx.status = '500 Internal Server Error'
            return json.dumps({'code': 500, 'message': str(e)})

class BackupRestore:
    @require_auth
    def POST(self, backup_id):
        try:
            tenant_id = web.ctx.tenant_id
            service = BackupService()
            result = service.restore_backup(int(backup_id), tenant_id)
            return json.dumps({'code': 0, 'message': '恢复成功', 'data': result})
        except Exception as e:
            if 'BACKUP_NOT_FOUND' in str(e):
                web.ctx.status = '404 Not Found'
                return json.dumps({'code': 404, 'message': '备份不存在'})
            web.ctx.status = '500 Internal Server Error'
            return json.dumps({'code': 500, 'message': str(e)})

class BackupDelete:
    @require_auth
    def DELETE(self, backup_id):
        try:
            tenant_id = web.ctx.tenant_id
            service = BackupService()
            service.delete_backup(int(backup_id), tenant_id)
            return json.dumps({'code': 0, 'message': '备份已删除'})
        except Exception as e:
            web.ctx.status = '500 Internal Server Error'
            return json.dumps({'code': 500, 'message': str(e)})
```

- [ ] **Step 4: 创建备份测试**

```python
# csBaby-server-py/tests/test_backup.py
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from services.backup_service import BackupService

def test_backup_service():
    service = BackupService()
    assert hasattr(service, 'upload_backup')
    assert hasattr(service, 'get_backup_list')
    assert hasattr(service, 'download_backup')
    assert hasattr(service, 'restore_backup')
    assert hasattr(service, 'delete_backup')
```

- [ ] **Step 5: 创建应用入口**

```python
# csBaby-server-py/app.py
import web
import os
from dotenv import load_dotenv
load_dotenv()

from controllers.auth_controller import Register, Login, Refresh
from controllers.sync_controller import Sync, SyncPush
from controllers.backup_controller import BackupUpload, BackupList, BackupDownload, BackupRestore, BackupDelete

urls = (
    '/', 'Index',
    '/auth/register', 'Register',
    '/auth/login', 'Login',
    '/auth/refresh', 'Refresh',
    '/sync', 'Sync',
    '/sync/push', 'SyncPush',
    '/api/v1/backup/upload', 'BackupUpload',
    '/api/v1/backup/list', 'BackupList',
    '/api/v1/backup/download/(.+)', 'BackupDownload',
    '/api/v1/backup/restore/(.+)', 'BackupRestore',
    '/api/v1/backup/(.+)', 'BackupDelete',
)

app = web.application(urls, globals())

class Index:
    def GET(self):
        from datetime import datetime
        return {
            'status': 'ok',
            'service': 'csbaby-sync-server-py',
            'version': '2.0.0',
            'ts': int(datetime.now().timestamp() * 1000)
        }

if __name__ == "__main__":
    app.run()
```

- [ ] **Step 6: 提交代码**

```bash
cd csBaby-server-py
git add models/backup.py services/backup_service.py controllers/backup_controller.py tests/test_backup.py app.py
git commit -m "feat: 备份功能实现和主应用入口"
```

---

## 任务 5: 包初始化和最终测试

**Files:**
- Create: `csBaby-server-py/utils/__init__.py`
- Create: `csBaby-server-py/models/__init__.py`
- Create: `csBaby-server-py/services/__init__.py`
- Create: `csBaby-server-py/controllers/__init__.py`
- Create: `csBaby-server-py/tests/__init__.py`
- Create: `csBaby-server-py/.gitignore`
- Create: `csBaby-server-py/.env.example`
- Modify: `csBaby-server-py/render.yaml`

- [ ] **Step 1: 创建包初始化文件**

```bash
# 创建空的 __init__.py 文件
touch csBaby-server-py/utils/__init__.py
touch csBaby-server-py/models/__init__.py
touch csBaby-server-py/services/__init__.py
touch csBaby-server-py/controllers/__init__.py
touch csBaby-server-py/tests/__init__.py
```

- [ ] **Step 2: 创建 .gitignore**

```bash
# csBaby-server-py/.gitignore
__pycache__/
*.py[cod]
*$py.class
*.so
.Python
env/
venv/
.env
.venv
*.egg-info/
dist/
build/
.pytest_cache/
.coverage
htmlcov/
```

- [ ] **Step 3: 创建 .env.example**

```bash
# csBaby-server-py/.env.example
DB_NAME=csbaby
DB_USER=postgres
DB_PASSWORD=your_password_here
DB_HOST=localhost
DB_PORT=5432
JWT_SECRET=your_jwt_secret_here
```

- [ ] **Step 4: 创建部署配置**

```yaml
# csBaby-server-py/render.yaml
services:
  - type: web
    name: csbaby-sync-server-py
    env: python
    plan: free
    buildCommand: pip install -r requirements.txt
    startCommand: python app.py
    envVars:
      - key: DB_NAME
        value: csbaby
      - key: DB_HOST
        fromDatabase:
          name: csbaby
          property: host
      - key: DB_PORT
        value: 5432
      - key: DB_USER
        fromDatabase:
          name: csbaby
          property: user
      - key: DB_PASSWORD
        fromDatabase:
          name: csbaby
          property: password
      - key: JWT_SECRET
        generateValue: true
```

- [ ] **Step 5: 运行完整测试**

Run: `cd csBaby-server-py && python -m pytest tests/ -v`
Expected: All tests passed

- [ ] **Step 6: 提交代码**

```bash
cd csBaby-server-py
git add utils/__init__.py models/__init__.py services/__init__.py controllers/__init__.py tests/__init__.py
git add .gitignore .env.example render.yaml
git commit -m "chore: 包初始化、配置和部署文件"
```

---

## 执行选项

**计划完成并保存至 `docs/superpowers/plans/2026-05-25-data-sync-migration.md`。两种执行方式：**

**1. 子代理驱动（推荐）** - 我按任务分派子代理，任务间进行审查，快速迭代

**2. 内联执行** - 在本会话中使用 executing-plans 技能执行任务，批量执行并设置检查点

**选择哪种方式？**