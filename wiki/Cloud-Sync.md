# Cloud Sync

## Sync Architecture

云端同步实现多设备数据一致：

### Sync Entities

| 实体 | 说明 |
|------|------|
| KeywordRule | 知识库规则 |
| UserStyleProfile | 风格配置 |
| AIModelConfig | AI模型配置 |
| ReplyHistory | 回复历史 |

## Sync Protocol

```
┌──────────┐         ┌────────────┐         ┌──────────┐
│  Client  │ ──────► │   Server   │ ◄────── │  Other   │
│          │ ◄────── │            │ ──────► │ Devices  │
└──────────┘         └────────────┘         └──────────┘
```

### Sync Flow
1. 本地变更 → 写入本地数据库
2. 同步队列 → 添加待同步任务
3. 后台worker → 批量同步
4. 冲突解决 → 时间戳优先

## Authentication

```kotlin
data class AuthState(
    val userId: String,
    val token: String,
    val expiresAt: Long
)
```

### Token Refresh
- 自动刷新过期token
- 离线缓存
- 安全存储

## Sync Manager

```kotlin
class SyncManager @Inject constructor(
    private val authManager: AuthManager,
    private val syncWorker: SyncWorker,
    private val queue: SyncQueue
) {
    suspend fun triggerSync() {
        if (!authManager.isAuthenticated()) return
        queue.flush()
    }
}
```

## Related
- [[OTA Update]]
- [[Database Schema]]
- [[Repository Pattern]]
- [[Sync Protocol]]