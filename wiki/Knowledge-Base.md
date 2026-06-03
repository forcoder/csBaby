# Knowledge Base

## Overview

知识库管理关键词规则，实现精准匹配和回复：

### Match Types

| 类型 | 说明 | 示例 |
|------|------|------|
| EXACT | 精确匹配 | "退货" 完全匹配 |
| FUZZY | 模糊匹配 | "退货*" 支持通配符 |
| REGEX | 正则匹配 | `\d+元` 匹配金额 |

## Rule Structure

```kotlin
data class KeywordRule(
    val id: Long,
    val keyword: String,
    val matchType: MatchType,
    val replyTemplate: String,
    val priority: Int,
    val isEnabled: Boolean,
    val scenarios: List<Scenario> = emptyList()
)
```

## Matching Algorithm

### Trie Tree Optimization
```
              退
             /
            货 ── 货
           /
          换
```
- O(n) 时间复杂度
- 支持前缀匹配
- 空间优化

### Priority Resolution
1. 高优先级规则优先
2. 同优先级按创建时间
3. 未匹配则转 [[AI Integration]]

## Scenario Filter

规则可关联场景：
- 全局规则
- 特定产品
- 特定时间

## Related
- [[Message Monitoring]]
- [[Reply Generator]]
- [[Database Schema]]
- [[MVVM Pattern]]