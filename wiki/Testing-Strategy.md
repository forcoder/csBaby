# Testing Strategy

## Test Coverage Target

**≥ 85%** 单元测试覆盖率

## Test Categories

### 1. Unit Tests
- 独立函数测试
- ViewModel测试
- Repository测试

### 2. Integration Tests
- DAO + Database
- Repository + Remote
- 同步流程

### 3. UI Tests
- Compose组件
- 导航流程
- 交互验证

## Test Structure (AAA)

```kotlin
@Test
fun `keyword matching returns correct result`() {
    // Arrange
    val matcher = KeywordMatcher()
    val rule = KeywordRule(keyword = "退货", matchType = EXACT)

    // Act
    val result = matcher.match("我想退货", rule)

    // Assert
    assertTrue(result.isMatched)
}
```

## Key Test Scenarios

### Knowledge Base Tests
- 精确匹配
- 模糊匹配
- 正则匹配
- 优先级排序

### AI Integration Tests
- 正常调用
- 超时重试
- 降级处理

### Sync Tests
- 上传成功
- 下载同步
- 冲突解决

## CI Integration

```yaml
test:
  script:
    - ./gradlew test
    - ./gradlew jacocoTestReport
  coverage:
    check:
      minimum: 85%
```

## Related
- [[Coding Standards]]
- [[Repository Pattern]]
- [[Cloud Sync]]