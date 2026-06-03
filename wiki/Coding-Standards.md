# Coding Standards

## Kotlin编码规范

### 命名规范

| 类型 | 规则 | 示例 |
|------|------|------|
| 包名 | 全小写 | `com.csbaby.kefu.data` |
| 类名 | PascalCase | `KeywordRuleRepository` |
| 函数 | camelCase | `getAllRules()` |
| 常量 | UPPER_SNAKE | `MAX_RETRY_COUNT` |
| 资源ID | snake_case | `btn_send` |

### 代码格式

- 缩进: 4空格
- 单行长度: ≤120字符
- 大括号: 紧跟代码语句

### 空安全 (铁律)

```kotlin
// ✅ 正确
val result: String? = getValue()
result?.let { process(it) }

// ❌ 禁止
val result: String = getValue()!!  // 除非确定非空
```

### 函数规范

- 单函数 ≤20行
- 单一职责
- 强制早期返回

```kotlin
fun processMessage(msg: String): Result<String> {
    if (msg.isBlank()) return Result.failure(IllegalArgumentException())
    // ... 业务逻辑
}
```

## Architecture Rules

### 层级依赖
- 展示层 → 领域层 → 数据层
- 禁止反向依赖

### ViewModel 规则
- 禁止持有 View/Context
- 使用 StateFlow 管理状态
- 处理业务逻辑

### Repository 规则
- 接口在 domain 层
- 实现在 data 层
- 统一返回 Flow/Result

## Testing Requirements

- 单元测试覆盖率 ≥85%
- TDD 开发流程
- 每功能必须有测试

## Related
- [[Clean Architecture]]
- [[MVVM Pattern]]
- [[Testing Strategy]]