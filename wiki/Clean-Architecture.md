# Clean Architecture

## Layer Structure

csBaby遵循Clean Architecture原则，将代码分为明确的层次：

### 1. Presentation Layer (展示层)
- **职责**: UI渲染、用户交互、状态管理
- **组件**: Activities, Fragments, ViewModels, Compose UI
- **规则**: 不包含业务逻辑，仅处理UI事件

### 2. Domain Layer (领域层)
- **职责**: 业务逻辑、领域规则、实体定义
- **组件**:
  - `model/` - 纯数据模型，不依赖Android
  - `repository/` - 仓储抽象接口
  - `usecase/` - 业务用例
- **规则**: 无Android依赖，可独立测试

### 3. Data Layer (数据层)
- **职责**: 数据访问、数据映射、API调用
- **组件**:
  - `local/` - Room数据库、DAOs、Entity
  - `remote/` - Retrofit API、数据传输对象
  - `repository/` - 仓储接口实现

### 4. Infrastructure Layer (基础设施层)
- **职责**: 系统级服务、外部集成
- **组件**:
  - `notification/` - 消息监听服务
  - `ai/` - AI服务集成
  - `knowledge/` - 知识库管理
  - `sync/` - 云端同步

## Dependency Rule

```
外层 → 内层依赖
展示层 → 领域层 → 数据层
```

**禁止反向依赖**: 内层不能依赖外层

## Implementation

### Domain Model (Example)
```kotlin
data class KeywordRule(
    val id: Long,
    val keyword: String,
    val matchType: MatchType,
    val replyTemplate: String,
    val priority: Int,
    val isEnabled: Boolean
)
```

### Repository Interface
```kotlin
interface KeywordRuleRepository {
    fun getAllRules(): Flow<List<KeywordRule>>
    suspend fun addRule(rule: KeywordRule)
    suspend fun updateRule(rule: KeywordRule)
    suspend fun deleteRule(id: Long)
}
```

## Related
- [[Architecture Overview]]
- [[MVVM Pattern]]
- [[Repository Pattern]]
- [[Database Schema]]