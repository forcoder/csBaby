# Database Schema

## Overview

csBaby使用Room数据库存储本地数据：

### Entity List

| Entity | 说明 |
|--------|------|
| AppConfig | 监听应用配置 |
| KeywordRule | 关键词规则 |
| Scenario | 场景配置 |
| AIModelConfig | AI模型配置 |
| UserStyleProfile | 用户风格 |
| ReplyHistory | 回复历史 |
| MessageBlacklist | 消息黑名单 |
| SyncCheckpoint | 同步检查点 |

## Schema Definition

### AppConfig
```kotlin
@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isMonitored: Boolean
)
```

### KeywordRule
```kotlin
@Entity(tableName = "keyword_rules")
data class KeywordRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val keyword: String,
    val matchType: String,
    val replyTemplate: String,
    val priority: Int,
    val isEnabled: Boolean
)
```

### ReplyHistory
```kotlin
@Entity(tableName = "reply_history")
data class ReplyHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val originalMessage: String,
    val reply: String,
    val timestamp: Long,
    val sourceApp: String,
    val usedTemplate: Boolean
)
```

## DAOs

| DAO | 操作 |
|-----|------|
| AppConfigDao | 监听应用CRUD |
| KeywordRuleDao | 关键词CRUD |
| ReplyHistoryDao | 历史记录CRUD |
| SyncCheckpointDao | 同步点管理 |

## Related
- [[Clean Architecture]]
- [[Repository Pattern]]
- [[Cloud Sync]]
- [[Message Monitoring]]