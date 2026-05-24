# 云端同步功能 - 技术设计

> OpenSpec Design Document v1.0
> 基于 Proposal v1.0
> 生成日期: 2026-05-24
> 状态: 待评审

---

## 1. 设计目标

### 1.1 非功能目标

| 目标 | 指标 |
|------|------|
| 性能 | 全量同步 < 30s，增量同步 < 5s |
| 可用性 | 本地优先，网络恢复后自动同步 |
| 一致性 | 最终一致，冲突自动/手动解决 |
| 安全性 | Token 加密存储，租户数据隔离 |

### 1.2 约束

- Android API 21+ 兼容
- Kotlin 1.8+ / Jetpack Compose
- Hilt 依赖注入
- Retrofit + OkHttp 网络层
- Room 数据库本地存储

---

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                       Presentation Layer                        │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ ProfileScreen │ │ProfileVM     │  │ SyncState → UI State   │ │
│  └──────────────┘  └──────────────┘  └────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                         Domain Layer                            │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │   Models     │  │  Repository  │  │     UseCases          │ │
│  │ (SyncState)  │  │ (Interface)  │  │ (SyncUseCase)        │ │
│  └──────────────┘  └──────────────┘  └────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                          Data Layer                             │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │  Remote API  │  │   Room DB    │  │    Sync Manager       │ │
│  │(SyncService) │  │    (DAO)     │  │(Orchestration Logic)  │ │
│  └──────────────┘  └──────────────┘  └────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                    Infrastructure Layer                        │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ AuthManager  │  │SyncCheckpoint│ │   AuthInterceptor    │ │
│  │(Token Store) │  │    (Dao)     │  │ (OkHttp Middleware)  │ │
│  └──────────────┘  └──────────────┘  └────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 核心组件职责

| 组件 | 包路径 | 职责 | 公共 API |
|------|--------|------|---------|
| SyncManager | `data.sync` | 同步编排引擎 | `login()`, `fullSync()`, `incrementalSync()`, `triggerSync()` |
| AuthManager | `data.sync` | Token 生命周期管理 | `saveAuthState()`, `clearAuthState()`, `getAuthStateSync()` |
| AuthenticatedSyncClient | `data.sync` | 带认证的 HTTP 客户端 | `apiService` |
| SyncApiService | `data.remote` | Retrofit API 定义 | `login()`, `getAllData()`, `pushChanges()` |
| SyncQueue | `data.sync` | 离线变更队列 | `enqueue()`, `flush()` |
| SyncCheckpointDao | `data.local` | 同步检查点存储 | `getCheckpoint()`, `updateSyncSuccess()` |

---

## 3. 数据模型设计

### 3.1 本地实体 → 云端模型映射

```
┌─────────────────────────────────────────────────────────────────┐
│                      Local Entity (Room)                         │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐  │
│  │KeywordRuleEntity │  │AIModelConfigEntity│  │UserStyleEntity │  │
│  ├──────────────────┤  ├──────────────────┤  ├────────────────┤  │
│  │id: Long          │  │id: Long          │  │userId: String  │  │
│  │keyword: String   │  │modelType: String │  │formalityLevel  │  │
│  │syncVersion: Long │  │apiKey: String    │  │...            │  │
│  │tenantId: String  │  │syncVersion: Long │  │syncVersion    │  │
│  └────────┬─────────┘  └────────┬─────────┘  └───────┬────────┘  │
└───────────┼──────────────────────┼───────────────────┼────────────┘
            │                      │                   │
            ▼                      ▼                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Sync Model (API DTO)                         │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐  │
│  │SyncKeywordRule   │  │SyncAIModelConfig │  │SyncUserStyle   │  │
│  ├──────────────────┤  ├──────────────────┤  ├────────────────┤  │
│  │id: Long          │  │id: Long          │  │userId: String  │  │
│  │keyword: String   │  │modelType: String │  │formalityLevel  │  │
│  │syncVersion: Long │  │apiKey: String    │  │...            │  │
│  │tenantId: String  │  │tenantId: String  │  │tenantId: Str  │  │
│  └──────────────────┘  └──────────────────┘  └────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 字段映射规则

| 字段类型 | 转换规则 | 示例 |
|---------|---------|------|
| snake_case → camelCase | API 返回的 snake_case 自动映射 | `match_type` → `matchType` |
| 软删除 | `deleted = true` 时标记删除，不物理删除 | 本地保留供冲突检测 |
| syncVersion | 首次同步 = 0，每次变更 +1 | 用于增量判断 |
| tenantId | 登录时获取，作为所有查询的过滤条件 | 租户隔离 |

---

## 4. 同步流程设计

### 4.1 全量同步流程

```
[fullSync(tenantId)]
        │
        ▼
┌───────────────────┐
│ 设置状态: Syncing │
└────────┬──────────┘
        │
        ▼
┌───────────────────┐
│ 调用 GET /sync/all│
└────────┬──────────┘
        │
        ▼
    ┌───┴───┐
    │ 成功？ │──否──→ [设置状态: Error] → [返回失败]
    └───┬───┘
       是│
        ▼
┌───────────────────┐
│ applyServerDataToLocal│
│ (遍历写入 Room)   │
└────────┬──────────┘
        │
        ▼
┌───────────────────┐
│ updateSyncSuccess │
│ (更新检查点)      │
└────────┬──────────┘
        │
        ▼
┌───────────────────┐
│ 设置状态: Success │
└───────────────────┘
```

### 4.2 增量同步流程

```
[incrementalSync(tenantId)]
        │
        ▼
┌───────────────────┐
│ 获取检查点(since) │
└────────┬──────────┘
        │
        ▼
┌───────────────────┐
│ 设置状态: Syncing │
└────────┬──────────┘
        │
        ▼
┌───────────────────┐
│ 调用 GET /sync/   │
│ changes?since=xxx │
└────────┬──────────┘
        │
        ▼
┌───────────────────┐
│ applyChangesToLocal│
│ (写入 + 处理删除) │
└────────┬──────────┘
        │
        ▼
┌───────────────────┐
│ pushLocalChanges  │
│ (推送本地变更)   │
└────────┬──────────┘
        │
        ▼
┌───────────────────┐
│ 更新检查点        │
└────────┬──────────┘
        │
        ▼
┌───────────────────┐
│ 设置状态: Success │
└───────────────────┘
```

### 4.3 冲突处理流程

```
[发现冲突]
        │
        ▼
┌───────────────────┐
│ resolveConflictAuto│
│ (按类型自动策略)  │
└────────┬──────────┘
        │
    ┌───┴───┐
    │ 可解决？│──否──→ [入队等待用户] → [UI 显示冲突对话框]
    └───┬───┘
       是│
        ▼
┌───────────────────┐
│ POST /sync/resolve│
│ (提交解决方案)   │
└────────┬──────────┘
        │
        ▼
┌───────────────────┐
│ 更新本地 syncVer  │
└───────────────────┘
```

### 4.4 冲突解决策略表

| 数据类型 | 策略 | 原因 |
|---------|------|------|
| KeywordRule | SERVER_WINS | 团队共享规则服务端权威 |
| AIModelConfig | SERVER_WINS | API Key 等敏感信息以服务端为准 |
| UserStyleProfile | CLIENT_WINS | 个人风格是用户自己的数据 |
| AppConfig | MERGE | 字段级合并 |
| Scenario | SERVER_WINS | 团队共享配置服务端权威 |
| ReplyHistory | SERVER_WINS | 服务端记录更完整 |
| MessageBlacklist | SERVER_WINS | 团队共享配置服务端权威 |

---

## 5. API 接口设计

### 5.1 认证接口

#### POST /auth/login

```kotlin
// 请求
data class LoginRequest(
    val email: String,
    val password: String
)

// 响应
data class AuthResult(
    val userId: String,           // 用户 ID
    val tenantId: String,         // 租户 ID
    val accessToken: String,      // JWT Access Token
    val refreshToken: String,     // JWT Refresh Token
    val expiresAt: Long           // 过期时间戳
)
```

### 5.2 同步接口

#### GET /sync/all

```
GET /sync/all?tenantId={tenantId}
Authorization: Bearer {accessToken}
```

| 响应字段 | 类型 | 说明 |
|---------|------|------|
| keywordRules | Array | 关键词规则列表 |
| aiModelConfigs | Array | AI 模型配置列表 |
| userStyleProfile | Object | 用户风格画像 |
| serverTime | Long | 服务器时间戳 |

#### GET /sync/changes

```
GET /sync/changes?tenantId={tenantId}&since={timestamp}
Authorization: Bearer {accessToken}
```

| 响应字段 | 类型 | 说明 |
|---------|------|------|
| deletedIds | Object | 删除 ID 映射 `{"keyword_rules": ["123"]}` |
| hasMore | Boolean | 是否还有更多数据 |
| nextCursor | String | 下一页游标 |

#### POST /sync/push

```kotlin
data class PushChangesRequest(
    val tenantId: String,
    val keywordRules: List<SyncKeywordRule>,
    val aiModelConfigs: List<SyncAIModelConfig>,
    val userStyleProfile: SyncUserStyleProfile?,
    val appConfigs: List<SyncAppConfig>,
    val scenarios: List<SyncScenario>,
    val replyHistory: List<SyncReplyHistory>,
    val messageBlacklist: List<SyncMessageBlacklist>,
    val deletedIds: Map<String, List<String>>,
    val baseVersion: Long
)
```

---

## 6. 本地存储设计

### 6.1 Room 数据库表

```sql
-- 同步检查点表
CREATE TABLE sync_checkpoints (
    tenant_id TEXT PRIMARY KEY,
    last_sync_time INTEGER NOT NULL DEFAULT 0,
    sync_token TEXT,
    is_syncing INTEGER NOT NULL DEFAULT 0,
    last_error TEXT
);
```

### 6.2 syncVersion 机制

| 场景 | syncVersion 值 | 说明 |
|------|---------------|------|
| 本地新增 | `0` | 首次同步后变为服务器返回的值 |
| 本地修改 | `上一次 syncVersion + 1` | 每次修改递增 |
| 增量同步条件 | `syncVersion > since` | 用于判断是否需要推送 |

---

## 7. 错误处理设计

### 7.1 错误分类

| 错误类型 | HTTP 状态码 | 处理策略 |
|---------|------------|---------|
| 参数错误 | 400 | 显示错误消息 |
| 认证失败 | 401 | 清除 Token，提示重新登录 |
| 无权限 | 403 | 提示权限不足 |
| 资源不存在 | 404 | 忽略或显示不存在 |
| 服务器错误 | 500 | 记录日志，显示友好提示 |

### 7.2 Token 刷新流程

```
[收到 401 响应]
        │
        ▼
┌───────────────────┐
│ 检查 refreshToken │
└────────┬──────────┘
        │
    ┌───┴───┐
    │ 存在？│──否──→ [清除认证状态] → [提示重新登录]
    └───┬───┘
       是│
        ▼
┌───────────────────┐
│ POST /auth/refresh│
└────────┬──────────┘
        │
    ┌───┴───┐
    │ 成功？│──否──→ [清除认证状态] → [提示重新登录]
    └───┬───┘
       是│
        ▼
┌───────────────────┐
│ 保存新 Token      │
│ 重试原请求       │
└───────────────────┘
```

---

## 8. 安全性设计

### 8.1 Token 存储

```kotlin
// 使用 DataStore 加密存储
// 存储字段：
// - auth_user_id
// - auth_tenant_id
// - auth_access_token
// - auth_refresh_token
// - auth_expires_at
// - auth_is_logged_in
```

### 8.2 网络安全

- 所有请求使用 HTTPS
- OkHttp 拦截器自动注入 Authorization Header
- 敏感数据（API Key）加密存储

### 8.3 租户隔离

```kotlin
// 所有 DAO 查询都带 tenantId 条件
@Query("SELECT * FROM keyword_rules WHERE tenant_id = :tenantId")
fun getRulesByTenant(tenantId: String): List<KeywordRuleEntity>
```

---

## 9. 性能优化

### 9.1 批量操作

- 使用 Room `insertAll()` 批量插入
- 每次批量处理 100 条记录
- 使用 `withTransaction` 保证事务一致性

### 9.2 防抖机制

```kotlin
// 写入即同步触发器，2s debounce
fun triggerSync() {
    syncTriggerJob?.cancel()
    syncTriggerJob = syncScope.launch {
        delay(2000)  // 2 秒防抖
        incrementalSync(tenantId)
    }
}
```

### 9.3 离线队列

```kotlin
// 网络不可用时，变更先入队
// 网络恢复后自动 flush
class SyncQueue {
    fun enqueue(entity: Entity)
    suspend fun flush(tenantId: String)
}
```

---

## 10. 测试策略

### 10.1 单元测试

| 测试类 | 测试内容 |
|--------|---------|
| SyncManagerTest | 全量同步、增量同步、冲突处理 |
| AuthManagerTest | Token 保存、读取、过期判断 |
| ConflictResolverTest | 各类型冲突解决策略 |

### 10.2 集成测试

| 测试类 | 测试内容 |
|--------|---------|
| SyncApiServiceTest | Retrofit API 调用（使用 MockEngine） |
| RoomSyncTest | Room 数据库读写（使用 inMemoryDatabase） |

### 10.3 E2E 测试

使用设备/模拟器执行 `云端同步测试用例.md` 中的 18 个测试用例。

---

*本文档为技术设计，待架构评审后进入实现阶段。*