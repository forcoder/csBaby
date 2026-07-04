# RDS 数据库迁移 + 知识库多端同步 API 实现计划

> **日期:** 2026-07-01
> **作者:** AtomCode (deepseek-v4-flash)

---

## 一、背景与目标

### 当前架构

| 组件 | 技术 | 地址 | 数据库 |
|------|------|------|--------|
| csbaby-api (Flask API) | Python/Flask | csbaby-api.onrender.com | SQLite (csbaby.db) |
| csbaby-sync (同步服务) | Python/Flask | sync.agentai0.com | Supabase PostgreSQL |
| csbaby-admin | Python/Flask | csbaby-admin.onrender.com | 代理至 api |
| Android 客户端 | Kotlin/Jetpack | — | Room (本地) + sync |
| Chrome 扩展 | JavaScript | github.com/forcoder/myhostex-assistant | 待对接 |

### Supabase PostgreSQL 连接（已在 Docker 环境变量中配置）

```
HOST: aws-1-west-2.pooler.supabase.com
PORT: 6543 (Supavisor 池化) / 5432 (直连)
USER: postgres.lvfpgbwpulchtfbtkklp
DB:   postgres
```

### 目标

1. **RDS 迁移**: 自动在 AWS RDS PostgreSQL 上建库建表，将 Supabase 数据全量迁移到 RDS，验证后切换
2. **知识库规则多端同步**: Chrome 扩展调用 `sync.agentai0.com` 的 API 进行规则 CRUD 操作，变更通过 sync 机制实时同步到所有终端（Android App、其他 Chrome 扩展等）

---

## 二、方案设计

### 2.1 RDS 迁移方案

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Supabase PG    │────>│  迁移脚本        │────>│   AWS RDS PG    │
│  (生产数据源)    │     │  migrate_to_rds  │     │  (新生产目标)    │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                                │
                                ▼
                        ┌─────────────────┐
                        │  验证报告         │
                        │  表名/行数/SHA256 │
                        └─────────────────┘
```

**迁移步骤:**
1. 自动在 RDS 上创建所有表（基于 schema.sql 中定义的 10 张表）
2. 逐表从 Supabase 读取数据，批量写入 RDS
3. 验证：每张表行数对比 + 数据抽样校验
4. 支持 `--dry-run` 模式（只建表不迁移数据）
5. 支持断点续传（记录已迁移的表）

### 2.2 知识库规则多端同步方案

```
┌─────────────────┐      ┌───────────────────┐      ┌─────────────────┐
│  Chrome 扩展     │─────>│  sync.agentai0.com │<─────│  Android App    │
│  (规则 CRUD)    │      │  (Flask Sync Server)│     │  (自动同步轮询)  │
└─────────────────┘      └───────────────────┘      └─────────────────┘
                                 │
                                 ▼
                        ┌─────────────────┐
                        │  PostgreSQL DB   │
                        │  (keyword_rules  │
                        │   + sync_version)│
                        └─────────────────┘
```

**同步机制:**
1. **Chrome 扩展** 调用 RESTful CRUD API (`/api/knowledge/rules`) 操作规则
2. 每次 CRUD 操作自动更新该规则的 `sync_version`（时间戳）
3. **Android App** 周期性轮询 `/sync/changes?since={lastSyncVersion}` 获取增量变更
4. 双方都能及时获取对方的最新规则变更

---

## 三、新增文件清单

| 文件 | 说明 | 类型 |
|------|------|------|
| `scripts/migrate_supabase_to_rds.py` | Supabase → RDS 迁移脚本 | 新建 |
| `scripts/migrate_supabase_to_rds_test.py` | 迁移脚本单元测试 | 新建 |
| `server/csBaby-server-py/controllers/knowledge_controller.py` | 知识库规则 CRUD API | 新建 |
| `server/csBaby-server-py/services/knowledge_service.py` | 知识库规则业务逻辑 | 新建 |
| `server/csBaby-server-py/tests/test_knowledge.py` | 知识库 API 测试 | 新建 |
| `server/csBaby-server-py/tests/test_knowledge_regression.py` | 知识库回归测试 | 新建 |

---

## 四、API 设计

### 4.1 知识库规则 CRUD API

所有接口: `Authorization: Bearer {token}`

#### `GET /api/knowledge/rules`
- 描述: 获取当前租户的所有知识库规则
- 响应: `{ code: 0, data: { rules: [...], total: N } }`

#### `POST /api/knowledge/rules`
- 描述: 创建新规则
- 请求: `{ keyword, matchType, replyTemplate, category, targetType, priority }`
- 响应: `{ code: 0, data: { id, ... } }`

#### `PUT /api/knowledge/rules/<id>`
- 描述: 更新规则
- 请求: `{ keyword, matchType, replyTemplate, ... }`
- 响应: `{ code: 0, data: { id, ... } }`

#### `DELETE /api/knowledge/rules/<id>`
- 描述: 软删除规则
- 响应: `{ code: 0, message: "删除成功" }`

### 4.2 RDS 迁移脚本 CLI

```bash
# 迁移所有表
python scripts/migrate_supabase_to_rds.py

# 仅检查不迁移
python scripts/migrate_supabase_to_rds.py --dry-run

# 指定连接
python scripts/migrate_supabase_to_rds.py \
  --supabase-url "postgresql://..." \
  --rds-url "postgresql://..."
```

---

## 五、数据库表清单

| 表名 | 数据量预估 | 说明 |
|------|-----------|------|
| users | 少量 | 用户账号 |
| keyword_rules | 中量 | 知识库规则（核心） |
| ai_model_configs | 少量 | AI 模型配置 |
| user_style_profiles | 少量 | 用户风格 |
| app_configs | 少量 | 应用配置 |
| scenarios | 少量 | 场景配置 |
| reply_history | 大量 | 回复历史 |
| message_blacklist | 少量 | 消息黑名单 |
| sync_checkpoints | 少量 | 同步检查点 |
| backup_records | 少量 | 备份记录 |

---

## 六、测试计划

### 6.1 RDS 迁移测试
| 用例 | 场景 | 输入 | 预期 |
|------|------|------|------|
| 1 | 正常迁移 | 有效连接串 | 所有表创建成功，数据行数一致 |
| 2 | 空数据库迁移 | Supabase 无数据 | RDS 表结构创建成功 |
| 3 | 断点续传 | 中途中断后重跑 | 不重复创建/迁移 |
| 4 | 连接失败 | 无效连接串 | 清晰错误信息 |
| 5 | 表结构不匹配 | Supabase 多一列 | 兼容处理或报错 |

### 6.2 知识库 CRUD API 测试
| 用例 | 场景 | 输入 | 预期 |
|------|------|------|------|
| 1 | 创建规则 | 完整规则数据 | 返回 id，sync_version 更新 |
| 2 | 获取所有规则 | 无参数 | 返回规则列表 |
| 3 | 更新规则 | 修改 keyword | 更新成功，版本自增 |
| 4 | 删除规则 | 规则 id | 软删除，sync_version 更新 |
| 5 | 未认证访问 | 无 token | 401 |
| 6 | 无效 token | 伪造 token | 401 |
| 7 | 创建空规则 | 空数据 | 400 参数校验 |
| 8 | 跨租户隔离 | A 租户不能操作 B 的规则 | 404 或 403 |
