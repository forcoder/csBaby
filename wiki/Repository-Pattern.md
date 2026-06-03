# Repository Pattern

## Pattern Overview

仓储模式封装数据访问，提供统一接口：

```
┌──────────────────┐      ┌──────────────────┐
│    ViewModel     │ ◄─── │   Repository     │
└──────────────────┘      └────────┬─────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              ▼                   ▼                   ▼
       ┌────────────┐      ┌────────────┐      ┌────────────┐
       │    Room    │      │   Remote   │      │   Cache    │
       │  Database  │      │    API     │      │            │
       └────────────┘      └────────────┘      └────────────┘
```

## Interface Definition

```kotlin
interface KeywordRuleRepository {
    fun getAllRules(): Flow<List<KeywordRule>>
    fun getEnabledRules(): Flow<List<KeywordRule>>
    suspend fun addRule(rule: KeywordRule)
    suspend fun updateRule(rule: KeywordRule)
    suspend fun deleteRule(id: Long)
    suspend fun syncRules()
}
```

## Implementation

```kotlin
class KeywordRuleRepositoryImpl @Inject constructor(
    private val dao: KeywordRuleDao,
    private val remote: RemoteDataSource
) : KeywordRuleRepository {

    override fun getAllRules(): Flow<List<KeywordRule>> {
        return dao.getAllFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addRule(rule: KeywordRule) {
        dao.insert(rule.toEntity())
    }
}
```

## Benefits

- **数据源抽象**: 屏蔽数据来源细节
- **可测试性**: 可用Mock替换真实数据源
- **可替换性**: 便于切换数据存储方案
- **职责分离**: 清晰的数据访问边界

## Related
- [[Clean Architecture]]
- [[MVVM Pattern]]
- [[Database Schema]]
- [[Cloud Sync]]