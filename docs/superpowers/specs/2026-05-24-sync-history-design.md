# 数据多版本与同步历史功能设计

> 设计日期: 2026-05-24
> 版本: v1.0
> 状态: 已批准

---

## 1. 功能概述

在"我的"页面云端同步卡片下方新增"同步历史"区域，展示历史版本列表，支持选择历史版本进行回滚。

---

## 2. 数据模型

### 2.1 本地存储 (Room)

```kotlin
@Entity(tableName = "sync_history")
data class SyncHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tenantId: String,
    val syncVersion: Long,        // 同步版本号（时间戳）
    val snapshotData: String,     // JSON 压缩快照
    val snapshotSize: Int,       // 快照大小（字节）
    val syncType: String,        // "FULL" 全量 / "INCREMENTAL" 增量
    val recordCount: Int,        // 记录条数（规则数+模型数等）
    val syncTime: Long,           // 同步时间
    val statsInserted: Int = 0,
    val statsUpdated: Int = 0,
    val statsDeleted: Int = 0
)
```

### 2.2 云端存储 (PostgreSQL)

```sql
CREATE TABLE sync_history (
    id SERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    sync_version BIGINT NOT NULL,
    snapshot_data TEXT,          -- 可能为空，仅记录元数据
    snapshot_checksum TEXT,
    sync_type TEXT NOT NULL,
    record_count INT,
    sync_time BIGINT NOT NULL,
    stats_inserted INT DEFAULT 0,
    stats_updated INT DEFAULT 0,
    stats_deleted INT DEFAULT 0,
    created_at BIGINT NOT NULL
);
```

---

## 3. 保留策略

| 层级 | 保留数量 | 时间 | 存储内容 |
|------|---------|------|---------|
| 本地 | 最近 30 次 | 始终 | 完整快照（压缩） |
| 云端 | 90 天 | 按 syncTime | 仅元数据 + 统计 |

---

## 4. UI 设计

### 4.1 布局位置

在"我的"页面云端同步卡片下方新增"同步历史"展开区域。

### 4.2 UI 原型

```
┌─────────────────────────────────────────┐
│ 云端同步                              │
├─────────────────────────────────────────┤
│ 已登录    [同步状态]  [立即同步][登出] │
│ 租户: xxx-xxx-xxx                      │
│ 上次同步: 5分钟前                      │
│ ✓ 新增 3 条，更新 1 条，删除 0 条     │ ← 同步统计
├─────────────────────────────────────────┤
│ ▼ 同步历史 (5条)              [查看全部] │
│ ┌─────────────────────────────────────┐ │
│ │ 今天 14:30 全量同步 获取10条       │ │
│ │ 今天 10:15 增量同步 新增2条        │ │
│ │ 昨天 18:20 增量同步 新增1条        │ │
│ │ [回滚到此版本]  [查看详情]       │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

### 4.3 回滚确认对话框

```
┌─────────────────────────────────────────┐
│ ⚠️ 确认回滚                           │
├─────────────────────────────────────────┤
│ 即将回滚到 [今天 14:30] 的版本        │
│                                         │
│ 当前数据将自动备份为新版本             │
│                                         │
│ 回滚后可通过"历史版本"恢复            │
│                                         │
│              [取消]  [确认回滚]         │
└─────────────────────────────────────────┘
```

---

## 5. 核心 API 设计

### 5.1 Android 端 (SyncManager.kt)

```kotlin
// 保存同步历史快照
suspend fun saveSyncHistory(
    tenantId: String,
    syncType: String,
    stats: SyncStats
): Result<Long>

// 获取本地历史列表
fun getLocalHistory(tenantId: String): Flow<List<SyncHistoryEntity>>

// 获取云端历史列表
suspend fun fetchCloudHistory(tenantId: String): Result<List<SyncHistoryEntity>>

// 回滚到指定版本
suspend fun rollbackToVersion(historyId: Long): Result<Unit>

// 删除过期历史
suspend fun cleanupExpiredHistory(tenantId: String)
```

### 5.2 后端 API

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | /sync/history?tenantId=xxx | 获取云端历史列表 |
| GET | /sync/history/:id/snapshot | 获取指定历史的快照数据（可选） |

---

## 6. 回滚流程

```
1. 用户点击"回滚到此版本"
2. 显示确认对话框：
   "即将回滚到 [时间] 的版本，当前数据将自动备份"
   [取消] [确认回滚]
3. 用户确认 → 执行：
   - 自动保存当前数据为新快照（safety backup）
   - 解压并恢复历史版本数据
   - 更新 syncVersion
4. 显示结果：成功/失败
```

---

## 7. 实现文件清单

| 文件 | 说明 |
|------|------|
| `SyncHistoryEntity.kt` | 新增实体类 |
| `SyncHistoryDao.kt` | 新增 DAO |
| `KefuDatabase.kt` | 添加表和 DAO |
| `SyncManager.kt` | 添加历史相关方法 |
| `ProfileViewModel.kt` | 添加历史状态和操作 |
| `SyncHistoryCard.kt` | 新增 UI 组件 |
| `ProfileScreen.kt` | 集成 SyncHistoryCard |

---

## 8. 验收标准

- [ ] 本地保存最近 30 次同步历史
- [ ] 云端存储 90 天历史元数据
- [ ] UI 展示同步历史列表
- [ ] 支持回滚到指定版本
- [ ] 回滚前自动创建安全备份
- [ ] 过时历史自动清理

---

*本文档基于 brainstorming 流程生成，已经用户评审批准。*