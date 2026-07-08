# Supabase → RDS MySQL 主写切换方案 V1

**作者**: csBaby 架构组
**日期**: 2026-07-05
**状态**: 📋 Phase 0 提案,等待评审
**目标读者**: 架构师、后端、Android 端、运维

---

## 0. 摘要 (TL;DR)

将 csBaby 当前的「SQLite 本地 + Supabase PostgreSQL」双写架构,改造为「**Aliyun RDS MySQL 主写 + Supabase 周期备份**」架构。Supabase 不再承担任何写入路径,仅作为冷备恢复源。

- **数据迁移**: Supabase 全部 10 张表 → RDS MySQL
- **代码改造**: `csBaby-server-py` 从 PostgreSQL 全栈改 MySQL;`infrastructure/sync/sync_writer.py` 改为直推 RDS
- **备份机制**: 新增 `backup-supabase-periodic` 容器,每天凌晨全量 dump Supabase → RDS snapshot 目录
- **回滚预案**: 任一阶段失败,5 分钟内 `.env` 切换回 Supabase,数据零丢失

**预计工期**: 5 工作日(评审 0.5 + Phase 1 双写 1.5 + Phase 2 切主 1 + Phase 3 备份 1 + Phase 4 观察 1)

---

## 1. 现状评估

### 1.1 数据流向图(2026-07-05 当前)

```
┌─────────────────┐
│ Android 客户端   │
│ (com.csbaby.kefu)│
└────────┬────────┘
         │ HTTPS (api.agentai0.com)
         ▼
┌─────────────────────────────────────────┐
│ csbaby-api 容器 (Flask + SQLite)        │
│   - /data/csbaby.db (本地主读)           │
│   - SyncWriter.push() ─┐                 │
└────────────────────────┼────────────────┘
                         │ psycopg2 直连
                         ▼
┌─────────────────────────────────────────┐
│ Supabase PostgreSQL (aws-1-us-west-2)   │
│   - users / keyword_rules / ... (10表)  │
│   - 数据量: 1118 keyword_rules / 71 users│
└─────────────────────────────────────────┘
                         ▲
                         │ HTTPS (sync.agentai0.com)
                         │
┌─────────────────────────────────────────┐
│ csbaby-sync 容器 (csBaby-server-py)     │
│   - PostgreSQL 全栈,psycopg2 直连       │
│   - 给 Android 端提供 sync REST API     │
└─────────────────────────────────────────┘
                         ▲
                         │ Android 端 SyncManager
                         │
              (back to Android 客户端)
```

### 1.2 关键发现

| 编号 | 发现 | 证据 |
|------|------|------|
| F-1 | RDS MySQL (`r8371qiaozhou.mysql.aliyun.com:3306`) 的 `r2346qiaozhou` 库已有完整的 10 张 csBaby 表 | `SHOW TABLES` 结果 |
| F-2 | RDS MySQL 表 schema 与 PostgreSQL 一致(类型做了 MySQL 适配) | `DESCRIBE` 对比 |
| F-3 | **当前没有任何代码连 RDS MySQL** | grep `pymysql` / `rds` 0 命中 |
| F-4 | RDS MySQL 已有 6 用户 / 954 rules(938 active)/ 16 deleted | 取证快照 |
| F-5 | RDS MySQL 中 `test@test.com` tenant_id = `41312dd9...`,PostgreSQL = `db810b7b...` | 取证快照 |
| F-6 | `keyword_rules` 已有 `keyword_hash VARCHAR(64)`,但 **RDS 上只有旧索引 `uk_tenant_keyword(tenant_id, keyword_hash)`,缺 `uk_tenant_keyword_hash`** | SHOW INDEX |
| F-7 | `scripts/migrate_supabase_to_rds.py` 是 PG→PG 迁移脚本,需改造为 PG→MySQL | 文件 header |
| F-8 | `csBaby-server-py` 全栈用 `psycopg2`,DB-agnostic 抽象度低 | `config/database.py` 180 行 |

### 1.3 关键风险

| 风险 | 等级 | 影响 |
|------|------|------|
| R-1: schema 适配错误导致数据截断 | 高 | 字段类型不一致,特别是 `TEXT` → `varchar(500)` 类 |
| R-2: sync 切换瞬间数据竞争 | 高 | API 与 sync 同时写,产生脏数据 |
| R-3: keyword_hash 唯一索引在 RDS 重建失败 | 中 | R11-R14 历史 bug 复发 |
| R-4: 备份链路设计不当导致 Supabase 数据丢失前未备份 | 中 | 失去兜底 |
| R-5: MySQL 连接池配置错误,API 端性能下降 | 中 | 8000 → 100 QPS |
| R-6: docker compose 升级失败,服务起不来 | 低 | 影响范围可控 |

---

## 2. 目标架构

```
┌─────────────────┐
│ Android 客户端   │
└────────┬────────┘
         │ HTTPS (api.agentai0.com)
         ▼
┌─────────────────────────────────────────────┐
│ csbaby-api 容器 (Flask + SQLite 本地缓存)   │
│   - SQLite 保留(API 高速读路径不变)          │
│   - SyncWriter.push() ─┐                    │
└────────────────────────┼────────────────────┘
                         │ pymysql 直连
                         ▼
┌─────────────────────────────────────────────┐
│ Aliyun RDS MySQL ⭐ (主权威)                 │
│   r8371qiaozhou.mysql.aliyun.com:3306        │
│   - users / keyword_rules / ... (10表)      │
│   - 全量数据 + 高频写入                       │
└────────┬────────────────────────────────────┘
         │
         │ daily 03:00 cron
         ▼
┌─────────────────────────────────────────────┐
│ csbaby-backup 容器 (新增)                    │
│   - 拉取 Supabase 全量数据(只读)              │
│   - 写入 RDS MySQL 一张 snapshot 表          │
│   - 或: dump 到 OSS 长期归档                  │
└────────┬────────────────────────────────────┘
         │ 周期只读
         ▼
┌─────────────────────────────────────────────┐
│ Supabase PostgreSQL (冷备恢复源)             │
│   - 不再被 API/sync 直写                      │
│   - 仅供: 备份读取 / 灾难恢复 / 数据考古      │
└─────────────────────────────────────────────┘
                         ▲
                         │ (备选: 当 RDS 故障时切回)
                         │
              (back to csbaby-sync 容器 — 同改造)
```

---

## 3. 阶段实施计划(4 阶段 + 评审)

### Phase 0: 方案评审 (0.5 天)

**目标**: 输出本方案文档 + OpenSpec 用例清单 + 风险评审。

**产出**:
- 本文档 (`docs/architecture/2026-07-05-supabase-to-rds-migration.md`)
- OpenSpec 验证用例清单 (`docs/architecture/2026-07-05-openspec-tests.md`)
- 用户签字

**回滚**: 无,纯评审阶段。

---

### Phase 1: 双写过渡 (1.5 天)

**目标**: `sync_writer.py` 同时写 RDS + Supabase,RDS 失败时回退到 SQLite outbox。生产实际仍以 Supabase 为权威,RDS 仅作镜像。

**改动**:

| 文件 | 改动 |
|------|------|
| `infrastructure/persistence/db_mysql.py` | **新建**: pymysql 连接池,与 db_supabase.py 同结构 |
| `infrastructure/sync/sync_writer.py` | `upsert_to_supabase` 旁加 `upsert_to_mysql`,`push` 双写 |
| `deploy/docker-compose.yml` | API 容器加 `pymysql` 依赖(env 变量新增 `RDS_DB_URL`) |
| `infrastructure/persistence/sync_outbox_repo.py` | 加 `mirror_failed_to_outbox()` 方法 |
| `.env` / `.env.example` | 新增 `RDS_DB_URL`(可空),`SUPABASE_DB_URL` 保留 |
| `tests/infrastructure/sync/test_sync_writer.py` | 新增:双写一致性测试 |

**验证(OpenSpec)**:
- T1.1: 双写成功 → 两库行数一致(用 `test@test.com` 场景)
- T1.2: 故意关闭 RDS 网络 → API 写入仍成功 → Supabase 同步,outbox 队列记录
- T1.3: 故意关闭 Supabase 网络 → API 写入仍成功 → RDS 同步
- T1.4: 双方都失败 → outbox 正确累积,无静默丢失

**回滚方法**: `.env` 把 `RDS_DB_URL` 置空,`csbaby-api` 重启即可,代码层一行不改。

---

### Phase 2: 切主写 (1 天)

**目标**: `.env` 把 `RDS_DB_URL` 设为主,`SUPABASE_DB_URL` 设为可选(只读备份用)。Supabase 不再被 API/sync 直写。

**前置条件**: Phase 1 双写观察 ≥24h,无 outbox 累积异常。

**改动**:

| 文件 | 改动 |
|------|------|
| `infrastructure/sync/sync_writer.py` | `upsert_to_supabase` 调用移除(或置为降级路径) |
| `csBaby-server-py/config/database.py` | 改为 pymysql:`psycopg2.pool.ThreadedConnectionPool` → `pymysqlpool.ThreadedConnectionPool` (或自实现) |
| `csBaby-server-py/schema.sql` | PG 方言 → MySQL 方言(`BIGSERIAL`→`AUTO_INCREMENT`,`TEXT`→`varchar/text`,`BOOLEAN`→`tinyint(1)`,`EXTRACT(EPOCH...)` → 应用层生成时间戳) |
| `csBaby-server-py/services/sync_service.py` | `psycopg2.extras.execute_batch` 替换为 pymysql 兼容写法 |
| `csBaby-server-py/Dockerfile` | `RUN pip install pymysql`(同步服务端需要 MySQL 客户端) |
| `infrastructure/persistence/db_supabase.py` | 标记 deprecated,但保留代码以便回滚 |
| `.env` | `RDS_DB_URL` 必填,`SUPABASE_DB_URL` 标注 `[read-only backup]` |
| `deploy/docker-compose.yml` | sync 容器加 MySQL 客户端 |

**Schema 适配映射表**:

| PostgreSQL | MySQL | 影响 |
|-----------|-------|------|
| `TEXT` | `text`(无长度限制) | ✅ 直接对应 |
| `VARCHAR(N)` | `varchar(N)` | ✅ 直接对应 |
| `BOOLEAN` | `tinyint(1)` | ⚠️ 应用层要 bool↔int 转换 |
| `BIGINT` | `bigint(20)` | ✅ 直接对应 |
| `BIGSERIAL` | `int(11) AUTO_INCREMENT` | ⚠️ 仅 backup_records 用 |
| `TIMESTAMP` | `bigint` 存毫秒 | ✅ schema 已用 bigint,无影响 |
| `EXTRACT(EPOCH FROM NOW()) * 1000` | `UNIX_TIMESTAMP() * 1000` | ⚠️ DEFAULT 表达式语法差异 |
| `DO $$ BEGIN ... EXCEPTION $$` | 需用 `IF NOT EXISTS` 或应用层处理 | ⚠️ ALTER 语句差异 |
| `JSONB` | `json` | ⚠️ 本项目未用 JSONB |

**验证(OpenSpec)**:
- T2.1: API 端写一条 keyword_rule → RDS 立即可见,Supabase 不再被写
- T2.2: Android 端 sync pull → 通过 csbaby-sync (改连 RDS) → 数据完整
- T2.3: csbaby-sync 容器 health_check 通过
- T2.4: `test@test.com` 跨库对照(用 csbaby-db-query skill):RDS=188 rules,Supabase=188 rules(冻结)
- T2.5: 全量行数对照:10 张表全部 0 偏差

**回滚方法**:
```bash
# 1. 编辑 .env 把 RDS_DB_URL 置空 + SUPABASE_DB_URL 恢复
# 2. docker compose restart csbaby-api csbaby-sync
# 3. 用 Phase 1 的双写代码(代码层未删除,只是 feature flag 切换)
# 预计 5 分钟内恢复
```

---

### Phase 3: 备份机制 (1 天)

**目标**: 部署 `csbaby-backup` 容器,每天凌晨 03:00 拉取 Supabase 全量数据,对比 RDS,有差异则告警 + 写入 `backup_records.snapshot`。

**改动**:

| 文件 | 改动 |
|------|------|
| `infrastructure/backup/supabase_to_rds_snapshot.py` | **新建**: 拉 Supabase 全表 → 对比 RDS → 不一致则 INSERT 到 RDS `backup_records.supabase_snapshot` |
| `infrastructure/backup/scheduler.py` | **新建**: APScheduler,每天 03:00 触发 |
| `deploy/docker-compose.yml` | 新增 `csbaby-backup` 服务 |
| `infrastructure/backup/notify.py` | 差异告警(可选: 邮件 / 企业微信 webhook) |
| `tests/infrastructure/backup/test_supabase_snapshot.py` | 新增:对比一致性测试 |

**备份策略选项**:

| 选项 | 实现 | 优劣 |
|------|------|------|
| B-A | 每天全量 dump Supabase → 写入 RDS 一张 `supabase_daily_snapshot` 表 | 简单可靠,但存储随天数线性增长 |
| B-B | 每天全量 dump → 存到阿里云 OSS `/csbaby/supabase-snapshots/YYYYMMDD.json.gz` | 长期归档,不影响 RDS 性能 |
| B-C | 每天全量 + 每小时增量(binlog 或 trigger) | 最实时,但实现复杂 |
| **推荐 B-B** | dump 存 OSS,RDS 仅保留「最近 7 天 + 当天对比差异」表 | 平衡存储与可恢复性 |

**验证(OpenSpec)**:
- T3.1: 手工触发 backup 任务 → 在 OSS 看到当天快照文件
- T3.2: 故意改 Supabase 一条数据 → 第二天 backup 检测到差异 → 告警
- T3.3: backup 任务失败 → 不影响 API/sync 主流程
- T3.4: 从 OSS 快照恢复演练(在测试环境,不在生产)→ 数据完整

**回滚方法**: `docker compose stop csbaby-backup`,无副作用。

---

### Phase 4: 观察期 (1 天)

**目标**: Phase 2 + 3 持续运行 ≥7 天,无异常后下线 Supabase 写入路径。

**监控指标**:
- csbaby-api 写 RDS 失败率 < 0.01%
- outbox 队列长度 < 100
- Android 端 sync pull 成功率 ≥ 99.5%
- backup 任务每天执行成功

**下线动作**:
- `.env` 移除 `SUPABASE_DB_URL`(完全依赖 RDS)
- `infrastructure/persistence/db_supabase.py` 物理删除
- `deploy/supabase_missing_tables.sql` 等历史文件归档

---

## 4. 文件改动清单(全量)

### 4.1 新增文件 (8 个)

| 路径 | 用途 | 阶段 |
|------|------|------|
| `infrastructure/persistence/db_mysql.py` | pymysql 连接池,镜像 db_supabase.py 结构 | P1 |
| `infrastructure/backup/supabase_to_rds_snapshot.py` | Supabase→RDS 备份对比脚本 | P3 |
| `infrastructure/backup/scheduler.py` | APScheduler 调度 | P3 |
| `infrastructure/backup/notify.py` | 告警通知 | P3 |
| `deploy/oss_backup.py` | 阿里云 OSS 上传(可选) | P3 |
| `tests/infrastructure/sync/test_sync_writer_dual_write.py` | Phase 1 双写测试 | P1 |
| `tests/infrastructure/backup/test_supabase_snapshot.py` | Phase 3 备份测试 | P3 |
| `docs/architecture/2026-07-05-openspec-tests.md` | OpenSpec 用例清单 | P0 |

### 4.2 修改文件 (10 个)

| 路径 | 改动 | 阶段 |
|------|------|------|
| `infrastructure/sync/sync_writer.py` | 加 `upsert_to_mysql`,`push` 双写/切单 | P1+P2 |
| `infrastructure/persistence/sync_outbox_repo.py` | 失败回退逻辑 | P1 |
| `csBaby-server-py/config/database.py` | psycopg2 → pymysql 全栈 | P2 |
| `csBaby-server-py/services/sync_service.py` | PG 方言 → MySQL 方言 | P2 |
| `csBaby-server-py/controllers/admin_controller.py` | `MD5()` 函数差异,需应用层实现 | P2 |
| `csBaby-server-py/controllers/backup_controller.py` | SQL 语法适配 | P2 |
| `csBaby-server-py/schema.sql` | PG 方言 → MySQL 方言 | P2 |
| `csBaby-server-py/Dockerfile` | `pip install pymysql` | P2 |
| `deploy/docker-compose.yml` | 加 `RDS_DB_URL` / 新增 backup 容器 | P1+P3 |
| `.env` / `.env.example` | 凭据模板 | P1+P2 |

### 4.3 删除/归档文件 (1 个)

| 路径 | 时机 |
|------|------|
| `infrastructure/persistence/db_supabase.py` | Phase 4 物理删除(前期保留) |

---

## 5. Schema 适配细节

### 5.1 关键类型差异

```sql
-- PostgreSQL → MySQL 等价转换

-- BOOLEAN
-- PG:  enabled BOOLEAN DEFAULT TRUE
-- MY:  enabled TINYINT(1) DEFAULT 1   -- 应用层需要 bool ↔ int 转换

-- SERIAL
-- PG:  id SERIAL PRIMARY KEY
-- MY:  id INT(11) AUTO_INCREMENT PRIMARY KEY

-- 时间戳默认值
-- PG:  created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
-- MY:  created_at BIGINT NOT NULL DEFAULT (UNIX_TIMESTAMP() * 1000)

-- TEXT (无长度限制) ↔ text
-- PG:  reply_template TEXT
-- MY:  reply_template TEXT  -- MySQL 也支持 TEXT 类型

-- 数组类型
-- PG:  keyword TEXT[]     -- 项目实际未用数组
-- MY:  keyword JSON       -- 如果需要用
```

### 5.2 关键索引差异

```sql
-- PG 的 EXCLUDE 约束 → MySQL 改用 UNIQUE INDEX
-- PG:  CONSTRAINT uk_tenant_keyword_hash UNIQUE (tenant_id, keyword_hash)
-- MY:  CREATE UNIQUE INDEX uk_tenant_keyword_hash ON keyword_rules(tenant_id, keyword_hash)
```

### 5.3 keyword_rules 唯一索引迁移

RDS MySQL 当前只有旧索引 `uk_tenant_keyword(tenant_id, keyword_hash)`,但这个索引已经能阻止同 `(tenant_id, keyword)` 重复,因为:
- 索引列 = (tenant_id, keyword_hash)
- 实际效果: 同 tenant 内 keyword_hash 必须唯一

但 admin_controller.py 中的迁移逻辑需要重新跑一次:
```sql
-- 步骤 1: 删除旧索引(如有)
DROP INDEX uk_tenant_keyword ON keyword_rules;

-- 步骤 2: 创建新索引
CREATE UNIQUE INDEX uk_tenant_keyword_hash ON keyword_rules(tenant_id, keyword_hash);
```

---

## 6. 风险评估与缓解

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| 数据迁移中数据不一致 | 高 | 全量迁移 + 行数对比 + 抽样校验,任一不过则终止迁移 |
| Phase 2 切换瞬间数据竞争 | 高 | 先停 csbaby-sync + csbaby-retry,改 .env,再启动;`docker compose stop && start` 一气呵成 |
| keyword_hash 重复导致迁移失败 | 中 | 先 `SELECT COUNT(DISTINCT tenant_id, keyword_hash), COUNT(*) FROM keyword_rules` 必须相等;否则先 dedup 再迁 |
| Supabase 备份链路断裂 | 中 | backup 任务失败告警,RDS 主写不影响 |
| MySQL 性能不足 | 中 | 上线前压测,baseline QPS ≥ 500 |
| RDS 连接耗尽 | 中 | 连接池 min=1 max=10,API 容器单实例够用 |
| Android 端 sync 失败 | 低 | schema 兼容性好,JSON 字段不变,客户端零改动 |

---

## 7. 回滚预案(每阶段独立)

### Phase 1 回滚 (双写)
```bash
# 编辑 .env
sed -i 's/^RDS_DB_URL=.*/# RDS_DB_URL=/' .env
docker compose restart csbaby-api
# 验证: 数据继续写入 Supabase,RDS 不再被写
# 时间: < 2 分钟
```

### Phase 2 回滚 (主写)
```bash
# 编辑 .env: SUPABASE_DB_URL 恢复,RDS_DB_URL 置空
# 把 sync_writer.py 的 feature flag 切回
sed -i 's/PRIMARY_DB=.*/PRIMARY_DB=supabase/' .env
docker compose restart csbaby-api csbaby-sync
# 时间: < 5 分钟
```

### Phase 3 回滚 (备份)
```bash
docker compose stop csbaby-backup
# 时间: < 30 秒
```

---

## 8. 时间表与里程碑

| 日期 | 阶段 | 准入条件 | 退出条件 |
|------|------|---------|---------|
| Day 1 AM | Phase 0 评审 | 文档发布 | 用户签字 |
| Day 1 PM - Day 2 PM | Phase 1 双写 | 评审通过 | 双写测试通过 + 观察 12h 无异常 |
| Day 3 - Day 4 | Phase 2 切主 | Phase 1 退出条件 | 主写测试通过 + Android 端 sync pull 通过 |
| Day 5 | Phase 3 备份 | Phase 2 退出条件 | 备份任务跑通 + OSS 文件存在 |
| Day 6+ | Phase 4 观察 | Phase 3 退出条件 | 7 天无异常 → 下线 Supabase 写入 |

---

## 9. 验证用例清单(详细见 OpenSpec 文档)

- T1.1 ~ T1.4: Phase 1 双写
- T2.1 ~ T2.5: Phase 2 切主
- T3.1 ~ T3.4: Phase 3 备份
- T4.1 ~ T4.3: Phase 4 下线前最终确认
- TR.1 ~ TR.3: 回滚演练

每条用例有:场景、输入、预期结果、通过标准、自动化脚本位置。

---

## 10. 待定决策 (Pending Decisions)

| 编号 | 决策点 | 推荐 | 备选 |
|------|-------|------|------|
| PD-1 | 备份存储介质 | 阿里云 OSS | RDS snapshot 表 |
| PD-2 | 备份频率 | 每天 03:00 全量 | 每小时增量 |
| PD-3 | RDS 主写切换时机 | 凌晨低峰 | 立即切换 |
| PD-4 | Supabase 下线时机 | Phase 4 观察 7 天后 | 永不下线(只读) |
| PD-5 | 失败告警通道 | 企业微信 webhook | 邮件 |

---

## 11. 相关文档

- OpenSpec 验证用例: `docs/architecture/2026-07-05-openspec-tests.md`(待 P0 完成)
- 双库取证 skill: `~/.claude/skills/csbaby-db-query/`
- 部署目标: [[csbaby-deployment]]
- 历史 bug 修复: [[csbaby-sync-dup-rootcause]]
- tenant_id 错位: [[csbaby-tenant-divergence]]

---

**版本**: V1
**下次评审**: Phase 1 双写代码 PR 出来时