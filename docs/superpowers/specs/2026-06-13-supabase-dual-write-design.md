# Supabase 双写同步设计方案

**日期**: 2026-06-13
**状态**: 设计批准，待编写实施计划
**目标**: 将 7 张业务表（5 张配置表 + users + user_devices）双写到 Supabase Postgres，为后续切换主库做准备

---

## 1. 背景

项目当前使用本地 SQLite（`csBaby.db`）作为唯一数据存储，存在以下问题：

- **无云端备份**: 部署在 Render 的容器若磁盘损坏，所有业务配置（规则、模型、黑名单、租户样式）将丢失
- **无账号体系共享**: users 表纯本地，用户无法跨实例同步
- **后续迁移成本**: 若未来切到 Supabase Postgres，需一次性双写+全量迁移，不如早做过渡

`.env` 中已存在 `SUPABASE_URL`，但全项目未实际接入 Supabase（grep 0 命中）。

---

## 2. 目标与非目标

### 2.1 目标

| # | 目标 | 可量化验收 |
|---|---|---|
| G1 | 5 张配置表 + users + user_devices 全部双写到 Supabase | Supabase 端可查询到与本地一致的数据 |
| G2 | Supabase 写入失败不影响 API 响应 | 1000 次写请求压测中，Supabase 故障下 API 成功率仍 100% |
| G3 | 重试 worker 自动恢复临时故障 | 注入网络抖动后，10 分钟内 outbox 自动清空 |
| G4 | 一次性脚本可重复创建 Supabase 端表 | 执行 `python scripts/bootstrap_supabase.py` 成功（已存在表不报错） |

### 2.2 非目标（双写期不做）

- ❌ 切主库（仍以 SQLite 为主库）
- ❌ reply_history / feedback / sessions 等高频/活跃表（语义不是"配置"，未来再评估）
- ❌ 双向冲突解决（双写期只本地写 Supabase，无冲突场景）
- ❌ RLS 行级权限（service_role 直连，先放开）
- ❌ 实时推送（webhook / listen/notify）

---

## 3. 同步范围（精确表清单）

| 表 | 类型 | 同步 | 备注 |
|---|---|---|---|
| `users` | 账号 | ✅ | 含 password_hash（双写期本地为主） |
| `user_devices` | 多设备绑定 | ✅ | FK users |
| `keyword_rules` | 知识库规则 | ✅ | 用户核心配置 |
| `model_configs` | 大模型配置 | ✅ | 用户核心配置 |
| `blacklist` | 黑名单 | ✅ | 用户核心配置 |
| `tenant_style_config` | 租户样式 | ✅ | 用户核心配置 |
| `tenant_app_config` | 租户应用设置 | ✅ | 用户核心配置 |
| `devices` | 设备（旧） | ❌ | 已被 user_devices 替代 |
| `reply_history` | 回复历史 | ❌ | 高频增长，非"配置" |
| `feedback` | 用户反馈 | ❌ | 同上 |
| `optimization_metrics` | 优化指标 | ❌ | 同上 |
| `agent_status` / `agent_skills` / `routing_config` | 客服路由 | ❌ | 运行时状态 |
| `sessions` | 会话 | ❌ | 活跃会话，双写易冲突 |
| `admin_sessions` | 管理员会话 | ❌ | 安全敏感，不上云 |

---

## 4. 架构

```
┌────────────┐    HTTP     ┌─────────────────────┐    psycopg    ┌──────────────┐
│  Android   │ ──────────► │  Flask app.py        │ ────────────► │  Supabase    │
│  客户端    │             │  (SQLite 主读)       │               │  Postgres    │
└────────────┘             │                      │               │  (写入镜像)  │
                           │  双写编排层          │               │              │
                           │  ▼                  │               │              │
                           │  1. 写 SQLite       │               │              │
                           │  2. 推 Supabase     │               │              │
                           │  3. 失败 → outbox   │               │              │
                           └─────────┬───────────┘               └──────────────┘
                                     │ 轮询
                                     ▼
                           ┌─────────────────────┐
                           │  重试 worker        │
                           │  (独立进程)         │
                           └─────────────────────┘
```

### 关键原则

- **读**: 全部走 SQLite，API 行为不变
- **写**: SQLite 事务成功 → 同步推 Supabase（不阻塞 API）→ 失败入 `sync_outbox`
- **不阻塞**: Supabase 慢/挂不影响 API 响应；用户不感知 Supabase 状态

---

## 5. 模块划分

| 模块 | 路径 | 职责 |
|---|---|---|
| `db_supabase.py` | `infrastructure/persistence/` | psycopg 连接池（`ThreadedConnectionPool`，min=1, max=5）、`get_connection()` 单例、health check |
| `sync_writer.py` | `infrastructure/sync/` | `SyncWriter.push(table, op, payload)` — 编排 SQLite → Supabase → outbox 写入 |
| `sync_outbox_repo.py` | `infrastructure/sync/` | `enqueue / claim_due / mark_done / mark_failed / move_to_dead_letter` |
| `retry_worker.py` | `infrastructure/sync/` | `python -m infrastructure.sync.retry_worker`，30s 间隔拉取 `next_retry_at <= now` |
| `bootstrap_supabase.py` | `scripts/` | 一次性 DDL：`CREATE TABLE IF NOT EXISTS` + 索引，幂等可重入 |
| `config.py`（扩展） | 根目录 | 新增 `SUPABASE_DB_URL`、`SYNC_RETRY_INTERVAL_SECONDS=30`、`SYNC_MAX_ATTEMPTS=10` |

---

## 6. 数据流

### 6.1 写入流程（API 内）

```
POST /api/rules  →  create_rule()
  │
  ├── 1. SqliteRuleRepository.create(data)
  │      └─ 事务提交，本地 row.id = 42
  │
  ├── 2. SyncWriter.push(
  │        table="keyword_rules",
  │        op="INSERT",
  │        row_id=42,
  │        payload={... row dict ...}
  │      )
  │      │
  │      ├─ psycopg execute_values(INSERT ... ON CONFLICT (id) DO UPDATE)
  │      │   └─ 成功 → 跳过 outbox（如有）
  │      │
  │      └─ 异常（连接/超时/SQL 错误）
  │          └─ outbox.enqueue(table, op, payload, last_error=str(e))
  │
  └── 3. return 201 (用户无感知)
```

### 6.2 重试流程（worker 进程）

```
retry_worker.tick()  (每 30s)
  │
  ├── 1. outbox.claim_due(limit=50) → [row1, row2, ...]
  │
  ├── 2. for each row:
  │      ├─ Supabase 写入成功 → outbox.mark_done(row.id)
  │      └─ 失败 → outbox.mark_failed(row.id, error, next_retry_at)
  │              └─ attempts >= 10 → outbox.move_to_dead_letter(row.id)
  │
  └── 3. sleep(30s)
```

### 6.3 重试退避

| attempts | next_retry_at（相对） |
|---|---|
| 1 | +10s |
| 2 | +30s |
| 3 | +2min |
| 4 | +10min |
| 5+ | +1h |

---

## 7. Schema 设计

### 7.1 本地 SQLite 新增

```sql
CREATE TABLE IF NOT EXISTS sync_outbox (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  table_name TEXT NOT NULL,
  op TEXT NOT NULL CHECK(op IN ('INSERT','UPDATE','DELETE')),
  row_id INTEGER,                    -- 业务表 row.id
  payload TEXT NOT NULL,             -- JSON
  attempts INTEGER DEFAULT 0,
  last_error TEXT,
  next_retry_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_outbox_next_retry ON sync_outbox(next_retry_at);

CREATE TABLE IF NOT EXISTS sync_outbox_dead (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  table_name TEXT NOT NULL,
  op TEXT NOT NULL,
  row_id INTEGER,
  payload TEXT NOT NULL,
  attempts INTEGER,
  last_error TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  moved_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 7.2 Supabase Postgres 端 7 张表

每张表 DDL 由 `bootstrap_supabase.py` 生成。关键差异：

| 字段 | SQLite 原 | Supabase Postgres |
|---|---|---|
| `id` | `INTEGER PRIMARY KEY AUTOINCREMENT` | `BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY` |
| `created_at`/`updated_at` | `DATETIME DEFAULT CURRENT_TIMESTAMP` | `TIMESTAMPTZ DEFAULT now()` |
| 布尔字段（`enabled`/`is_default`） | `INTEGER` (0/1) | `BOOLEAN DEFAULT false` |
| 字符串默认值 | 直接 `DEFAULT ''` | `DEFAULT ''::text` |

`users.password_hash` 列：仍同步（双写期本地仍是主，Supabase 是镜像，仅 service_role 可读）。

启动校验：`bootstrap_supabase.py --check` 启动 Flask 时调用，缺表则启动失败（早暴露）。

---

## 8. 错误处理

| 场景 | 行为 |
|---|---|
| Supabase 连接失败 | 捕获 `OperationalError`/`InterfaceError` → outbox 入队 → log warning |
| Supabase 慢（>5s） | psycopg 连接超时 3s + statement 超时 5s → outbox 入队 |
| Schema 不存在 | 启动 `bootstrap_supabase.py --check` → 缺表则 `RuntimeError` 阻止 Flask 启动 |
| Supabase 写入幂等冲突 | `INSERT ... ON CONFLICT (id) DO UPDATE` 自动 upsert，避免重复 |
| Outbox 堆积 >1000 | retry_worker log warning（不阻塞） |
| 重试 worker 宕机 | 独立进程；重启后从上次 `next_retry_at` 续跑，outbox 不丢 |
| 超 10 次重试失败 | `move_to_dead_letter`，记录到 `sync_outbox_dead` 表，需人工介入 |

---

## 9. 配置（.env 扩展）

```bash
# 已有
SUPABASE_URL=https://xxx.supabase.co

# 新增
SUPABASE_DB_URL=postgresql://postgres.[ref]:[password]@aws-0-[region].pooler.supabase.com:6543/postgres
# ↑ 6543 端口 = transaction pooler（推荐用于服务端）
# ↑ 5432 端口 = session pooler（短连接用）
# ↑ 直连 IP = 不推荐

SYNC_RETRY_INTERVAL_SECONDS=30
SYNC_MAX_ATTEMPTS=10
SYNC_SUPABASE_TIMEOUT_SECONDS=5
```

`SUPABASE_DB_URL` **不在 git 跟踪**，只入 `.gitignore` 与部署平台的 Secret。

---

## 10. 部署与运维

### 10.1 启动顺序（Render / Docker）

```
1. 启动前执行 bootstrap_supabase.py --check（缺表自动建）
2. 启动 Flask app.py
3. 启动重试 worker（独立进程 / Render Background Worker）
```

### 10.2 监控指标（log 自带）

| 指标 | 输出位置 | 告警阈值 |
|---|---|---|
| `sync_outbox.size` | 每次 retry_worker tick log | > 1000 |
| `sync_outbox_dead.size` | 启动 Flask 时 log | > 0 |
| Supabase 推送耗时 | SyncWriter.push() 内 timing | p99 > 3s |

### 10.3 回滚预案

如上线后发现重大问题：
1. 关闭重试 worker → 停止重试
2. 注释 `SyncWriter.push()` 调用 → 停止双写
3. 回滚代码 commit

---

## 11. 测试方案

目标：单元测试覆盖率 ≥ 85%（按 CLAUDE.md 要求）。

### 11.1 单元测试

| 模块 | 测试点 |
|---|---|
| `SyncWriter.push` | (a) SQLite 成功 + Supabase 成功 (b) SQLite 成功 + Supabase 失败 → outbox (c) SQLite 失败 → 不调用 Supabase (d) payload JSON 序列化正确 |
| `sync_outbox_repo` | (a) enqueue (b) claim_due 只取到期项 (c) mark_done/mark_failed (d) move_to_dead_letter 超阈值 |
| `db_supabase` | (a) 连接池获取/归还 (b) health check |
| 重试退避 | attempts 1→5 的 next_retry_at 序列 |

### 11.2 集成测试

- 用 SQLite in-memory + docker postgres 起真实双写路径
- 验证：API 写入 → 本地 + Supabase 同时有数据 → 一致性

### 11.3 E2E

- POST /api/rules → 本地 SELECT + Supabase SELECT 都能查到
- 注入 Supabase 故障 → API 仍 200 → outbox 有记录 → worker 恢复后 outbox 清空

---

## 12. 实施清单（概要，详细计划由 writing-plans 产出）

| # | 任务 | 估时 |
|---|---|---|
| 1 | `infrastructure/persistence/db_supabase.py` | 0.5d |
| 2 | `infrastructure/sync/sync_outbox_repo.py` + SQLite DDL | 0.5d |
| 3 | `infrastructure/sync/sync_writer.py` | 1d |
| 4 | `infrastructure/sync/retry_worker.py` | 0.5d |
| 5 | `scripts/bootstrap_supabase.py` | 0.5d |
| 6 | 接入 7 张表的 repository / API endpoint | 1d |
| 7 | 单元 + 集成测试 | 1d |
| 8 | .env 模板更新 / 部署文档 | 0.5d |
| **合计** | | **5.5d** |

---

## 13. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| Supabase 账号配额耗尽 | 推送失败堆积 | 监控 + 告警；可临时降级为单写本地 |
| 网络分区导致 outbox 堆积 | 数据镜像延迟 | 退避策略 + 堆积告警 |
| users.password_hash 上云合规风险 | 审计/合规 | 双写期仅 service_role 访问；未来切主库前评估 RLS |
| Render 免费实例 worker 不持久 | 重启丢 in-flight | outbox 存 SQLite 不丢；worker 幂等 |
| psycopg 版本不匹配 Python 3.x | 安装失败 | 用 psycopg2-binary 避免编译 |

---

## 14. 后续工作（本期外）

- 切换主库为 Supabase（待双写稳定后）
- RLS 行级权限
- reply_history 等高频表评估
- 客户端实时订阅（Supabase Realtime）
- 数据对账工具（定期 SQLite vs Supabase diff）