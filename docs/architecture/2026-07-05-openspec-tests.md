# Supabase → RDS MySQL 迁移 OpenSpec 验证用例清单

**配套文档**: `2026-07-05-supabase-to-rds-migration.md`
**用例总数**: 18 条(4 阶段 + 3 阶段 + 3 阶段 + 3 阶段 + 3 回滚)
**自动化目标**: ≥ 85%

---

## 总览矩阵

| 阶段 | 用例编号 | 数量 | 通过条件 |
|------|---------|------|---------|
| Phase 1 双写 | T1.1 - T1.4 | 4 | 双写一致性、outbox 兜底 |
| Phase 2 切主 | T2.1 - T2.5 | 5 | RDS 权威、客户端零感知 |
| Phase 3 备份 | T3.1 - T3.4 | 4 | OSS 快照、差异告警 |
| Phase 4 下线 | T4.1 - T4.3 | 3 | 7 天观察无异常 |
| 回滚演练 | TR.1 - TR.3 | 3 | 各阶段独立回滚 < 5 分钟 |
| 数据迁移 | TM.1 - TM.3 | 3 | 全量迁移无丢失 |
| 性能基线 | TP.1 - TP.3 | 3 | QPS / P99 / 错误率 |
| **合计** | | **27** | |

---

## Phase 1: 双写过渡

### T1.1 双写成功一致性验证

| 项 | 内容 |
|----|------|
| 场景 | API 端写一条 keyword_rule,两库应同时有这条数据 |
| 前置 | 双写代码已部署,.env 同时配置 RDS_DB_URL + SUPABASE_DB_URL |
| 操作 | `POST /api/keyword-rules {keyword:"test", reply:"reply"}` |
| 预期 RDS | `SELECT COUNT(*) FROM keyword_rules WHERE id=<新id>` = 1 |
| 预期 Supabase | `SELECT COUNT(*) FROM keyword_rules WHERE id=<新id>` = 1 |
| 通过标准 | 两库行数都 = 1,所有字段值完全一致 |
| 自动化 | `tests/infrastructure/sync/test_sync_writer_dual_write.py::test_dual_write_insert` |

### T1.2 RDS 失败回退到 outbox

| 项 | 内容 |
|----|------|
| 场景 | RDS 网络断开时,API 写入仍成功,Supabase 同步,outbox 累积 |
| 前置 | 双写代码已部署 |
| 操作 | `iptables -A OUTPUT -d r8371qiaozhou.mysql.aliyun.com -j DROP` 然后 POST 一条 |
| 预期 API 响应 | 200 OK(不报错) |
| 预期 Supabase | 数据已写入 |
| 预期 outbox | `SELECT COUNT(*) FROM sync_outbox WHERE status='pending' AND table='keyword_rules' AND last_error LIKE '%mysql%'` >= 1 |
| 通过标准 | API 不中断 + Supabase 一致 + outbox 正确记录 RDS 失败 |
| 自动化 | `test_dual_write_rds_failure_fallback` |

### T1.3 Supabase 失败回退到 outbox

| 项 | 内容 |
|----|------|
| 场景 | Supabase 不可达时,API 写入仍成功,RDS 同步 |
| 操作 | 模拟 Supabase 不可达(SIGSTOP csbaby-sync 或改 SUPABASE_DB_URL 指向无效 IP) |
| 预期 API | 200 OK |
| 预期 RDS | 数据已写入 |
| 预期 outbox | 累积 Supabase 失败项 |
| 通过标准 | 同 T1.2 镜像 |
| 自动化 | `test_dual_write_supabase_failure_fallback` |

### T1.4 双库都失败不静默丢失

| 项 | 内容 |
|----|------|
| 场景 | RDS + Supabase 同时不可达,数据不丢失 |
| 操作 | 同时中断两库网络,POST 一条 |
| 预期 API | 200 OK(本地 SQLite 已写) |
| 预期 outbox | 累积 1 条 `pending` 记录,带 `last_error` 标记双失败 |
| 通过标准 | outbox 队列准确反映失败状态;网络恢复后 retry_worker 能补单 |
| 自动化 | `test_dual_write_both_failure_enqueue` |

---

## Phase 2: 切主写

### T2.1 主写切换后 RDS 即时可见

| 项 | 内容 |
|----|------|
| 场景 | .env 切到 RDS 主写,API 端写一条,RDS 立即可见,Supabase 不再被写 |
| 前置 | Phase 1 通过,观察 ≥ 12h |
| 操作 | 编辑 .env `PRIMARY_DB=mysql`,重启 csbaby-api |
| 步骤 | POST 一条 keyword_rule,等待 1s |
| 预期 RDS | 立即可见 |
| 预期 Supabase | 该条记录不存在(或 sync_version 不变) |
| 通过标准 | RDS 行数 +1,Supabase 行数不变 |
| 自动化 | `test_phase2_primary_db_mysql.py::test_rds_only_write` |

### T2.2 Android 端 sync pull 走 RDS

| 项 | 内容 |
|----|------|
| 场景 | Android 客户端拉取全量同步,数据从 RDS 返回 |
| 前置 | csbaby-sync 容器已切 MySQL 配置 |
| 操作 | Android `SyncManager.fullSync()` |
| 预期 HTTP 响应 | 200,JSON 含 keywordRules / aiModelConfigs / 等 7 个字段 |
| 预期数据条数 | 等于 RDS 中该 tenant 的 active 行数 |
| 通过标准 | 数据完整、字段映射正确、sync_version 单调递增 |
| 自动化 | `tests/integration/test_android_sync_pull.py::test_full_sync_from_rds` |

### T2.3 csbaby-sync 容器健康检查

| 项 | 内容 |
|----|------|
| 场景 | sync 服务启动后,健康检查接口返回 200 |
| 操作 | `curl http://sync.agentai0.com/` |
| 预期 | `{"status":"ok",...}` |
| 自动化 | `test_sync_health.py::test_health_endpoint` |

### T2.4 跨库对照 test@test.com 一致性

| 项 | 内容 |
|----|------|
| 场景 | 切换后,test@test.com 在 RDS 与 PostgreSQL 的 keyword_rules 应完全一致 |
| 操作 | 用 csbaby-db-query skill 跑双库对照 |
| 预期 | `RDS.total_active = PG.total_active = 188`(切换前后 PG 冻结) |
| 通过标准 | 行数 / 字段值 100% 一致(冻结 PG 后再切换) |
| 自动化 | `test_phase2_cross_db_consistency.py` |

### T2.5 全量行数对照 10 张表

| 项 | 内容 |
|----|------|
| 场景 | 切换后,10 张表的 RDS 行数与切换前 PG 行数完全一致 |
| 操作 | 对每张表 `SELECT COUNT(*)` |
| 通过标准 | 10 张表全部 0 偏差 |
| 自动化 | `test_phase2_full_table_count.py`(对 10 张表参数化测试) |

---

## Phase 3: 备份机制

### T3.1 手工触发备份任务成功

| 项 | 内容 |
|----|------|
| 场景 | 手工触发 csbaby-backup 任务,在 OSS 看到当天快照文件 |
| 操作 | `docker exec csbaby-backup python -m infrastructure.backup.supabase_to_rds_snapshot --manual` |
| 预期 OSS | `oss://csbaby-bucket/supabase-snapshots/YYYY-MM-DD.json.gz` 存在 |
| 预期文件大小 | ≥ 100 KB(数据量决定) |
| 通过标准 | 文件可下载、内容含全表数据 |
| 自动化 | `test_phase3_manual_backup.py::test_oss_snapshot_exists` |

### T3.2 Supabase 篡改检测告警

| 项 | 内容 |
|----|------|
| 场景 | 故意改 Supabase 一条数据,第二天 backup 检测到差异 → 告警 |
| 操作 | `UPDATE keyword_rules SET keyword='hacked' WHERE id='xxx'`(在 Supabase) |
| 预期告警 | 企业微信 webhook 收到 `Supabase data divergence detected` |
| 自动化 | `test_phase3_drift_detection.py::test_alert_on_drift` |

### T3.3 backup 失败不影响主流程

| 项 | 内容 |
|----|------|
| 场景 | backup 任务失败,RDS 主写 + Android sync 仍正常 |
| 操作 | 让 backup 容器 OOM 或断网 |
| 预期 | API/sync 正常运行,无级联故障 |
| 通过标准 | backup 容器重启后自动恢复 |
| 自动化 | `test_phase3_backup_isolation.py` |

### T3.4 OSS 快照恢复演练

| 项 | 内容 |
|----|------|
| 场景 | 从 OSS 快照恢复到测试 RDS,数据完整 |
| 操作 | 在测试环境从 OSS 拉快照,导入新 RDS |
| 预期 | 行数与快照生成时一致 |
| 通过标准 | 抽样 100 条数据校验哈希 |
| 自动化 | `test_phase3_oss_restore.py` |

---

## Phase 4: 下线 Supabase 写入

### T4.1 7 天无异常监控

| 项 | 内容 |
|----|------|
| 场景 | Phase 2+3 持续运行 7 天,关键指标无异常 |
| 通过标准 | 全部满足: |
| | - RDS 写失败率 < 0.01% |
| | - outbox 队列长度 < 100 |
| | - Android sync pull 成功率 ≥ 99.5% |
| | - backup 任务每天成功 |
| 自动化 | `monitoring/dashboard/phase4_dashboard.json` |

### T4.2 Supabase 写入路径完全停止

| 项 | 内容 |
|----|------|
| 场景 | .env 移除 SUPABASE_DB_URL,API/sync 启动正常 |
| 操作 | 编辑 .env `SUPABASE_DB_URL=`(置空) |
| 预期 | API 端不再尝试连 Supabase,日志无报错 |
| 自动化 | `test_phase4_supabase_disabled.py::test_no_supabase_connection_attempt` |

### T4.3 db_supabase.py 物理删除后无影响

| 项 | 内容 |
|----|------|
| 场景 | 删除 db_supabase.py 后,代码无 import 引用 |
| 操作 | `rm infrastructure/persistence/db_supabase.py`,跑完整测试套件 |
| 通过标准 | 所有测试通过 |
| 自动化 | `grep -r "db_supabase" infrastructure/ csBaby-server-py/` 必须空 |

---

## 数据迁移 (TM)

### TM.1 全量数据迁移无丢失

| 项 | 内容 |
|----|------|
| 场景 | 从 Supabase 迁移 10 张表全部数据到 RDS |
| 操作 | `python scripts/migrate_supabase_to_rds.py`(改造后) |
| 通过标准 | 每张表 `RDS.count = PG.count` |
| 自动化 | `test_migration_full.py::test_all_tables_count_match` |

### TM.2 抽样校验数据哈希一致

| 项 | 内容 |
|----|------|
| 场景 | 每张表随机抽 10% 行,字段值完全一致 |
| 操作 | 对每张表 `SELECT md5(json_agg(t.*)) FROM (SELECT * FROM tbl ORDER BY id LIMIT 1000 OFFSET 0) t` |
| 通过标准 | PG 与 RDS 的 MD5 完全相等 |
| 自动化 | `test_migration_sample_hash.py` |

### TM.3 keyword_hash 唯一索引重建

| 项 | 内容 |
|----|------|
| 场景 | 迁移完成后,uk_tenant_keyword_hash 索引被创建 |
| 操作 | `SHOW INDEX FROM keyword_rules` |
| 通过标准 | 索引名 `uk_tenant_keyword_hash`,列 `(tenant_id, keyword_hash)`,unique=1 |
| 自动化 | `test_migration_index.py::test_keyword_hash_unique_index` |

---

## 性能基线 (TP)

### TP.1 API QPS 不下降

| 项 | 内容 |
|----|------|
| 场景 | 切到 RDS 后,API 端 QPS 不低于切换前 95% |
| 操作 | `wrk -t4 -c100 -d30s http://api.agentai0.com/api/health` |
| 通过标准 | QPS ≥ 500(切换前 baseline × 0.95) |
| 自动化 | `tests/performance/test_qps_baseline.py` |

### TP.2 P99 延迟不上升

| 项 | 内容 |
|----|------|
| 场景 | API 写 keyword_rules 接口 P99 ≤ 200ms |
| 操作 | 1000 次连续 POST,统计 P99 |
| 通过标准 | P99 ≤ 200ms |
| 自动化 | `tests/performance/test_p99_latency.py` |

### TP.3 错误率 < 0.01%

| 项 | 内容 |
|----|------|
| 场景 | 切到 RDS 后,API 5xx 错误率不高于切换前 |
| 操作 | 监控 24h |
| 通过标准 | 5xx 错误率 < 0.01% |
| 自动化 | `monitoring/error_rate.py` |

---

## 回滚演练 (TR)

### TR.1 Phase 1 回滚 < 2 分钟

| 项 | 内容 |
|----|------|
| 场景 | 双写期间发现 RDS 有问题,2 分钟内回到单写 Supabase |
| 操作 | `sed -i 's/^RDS_DB_URL=.*/# RDS_DB_URL=/' .env && docker compose restart csbaby-api` |
| 预期 | API 仅写 Supabase,RDS 不再被写 |
| 通过标准 | 回滚完成 < 2 分钟,无数据丢失 |
| 自动化 | `tests/rollback/test_phase1_rollback.py` |

### TR.2 Phase 2 回滚 < 5 分钟

| 项 | 内容 |
|----|------|
| 场景 | 切主后 RDS 出现严重问题,5 分钟内切回 Supabase |
| 操作 | 改 .env 切回 Supabase 主写 + 重启 |
| 预期 | API 写 Supabase,Android sync 正常 |
| 通过标准 | 回滚完成 < 5 分钟,数据无丢失 |
| 自动化 | `tests/rollback/test_phase2_rollback.py` |

### TR.3 从 OSS 快照灾难恢复

| 项 | 内容 |
|----|------|
| 场景 | RDS 完全损坏,从 OSS 快照恢复 |
| 操作 | 创建新 RDS,从 OSS 拉取最近快照导入 |
| 预期 | 数据恢复到快照时刻 |
| 通过标准 | 关键数据(用户数 / 规则数)与快照一致 |
| 自动化 | `tests/rollback/test_disaster_recovery.py` |

---

## 用例执行跟踪表

| 阶段 | 用例 | 执行人 | 状态 | 日期 | 备注 |
|------|------|--------|------|------|------|
| Phase 0 | T1.1 - T4.3, TM, TP, TR 全部 | 架构组 | 📋 待评审 | - | 评审通过后开始实施 |
| Phase 1 | T1.1 - T1.4 | 后端 | 🕒 待启动 | - | - |
| Phase 2 | T2.1 - T2.5 + TM.1 - TM.3 | 后端 + 架构 | 🕒 待启动 | - | - |
| Phase 3 | T3.1 - T3.4 | 运维 | 🕒 待启动 | - | - |
| Phase 4 | T4.1 - T4.3 + TP.1 - TP.3 | 全员 | 🕒 待启动 | - | - |

---

## 关联

- 迁移方案 V1: `2026-07-05-supabase-to-rds-migration.md`
- 记忆: [[csbaby-deployment]], [[csbaby-sync-dup-rootcause]], [[csbaby-tenant-divergence]]
- Skill: `csbaby-db-query`(跨库取证)