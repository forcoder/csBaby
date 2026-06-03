# Style Learning

## Overview

风格学习引擎分析用户历史回复，提取风格特征：

### Style Dimensions

| 维度 | 范围 | 说明 |
|------|------|------|
| formality | 0.0 - 1.0 | 正式程度 |
| enthusiasm | 0.0 - 1.0 | 热情程度 |
| professionalism | 0.0 - 1.0 | 专业程度 |

### Phrase Management
- commonPhrases - 常用短语
- avoidPhrases - 避免短语

## Learning Algorithm

### Data Collection
```kotlin
data class StyleSample(
    val originalMessage: String,
    val reply: String,
    val timestamp: Long
)
```

### Feature Extraction
1. 句子长度分析
2. 表情符号统计
3. 正式/非正式词汇
4. 问候语/结束语

### Profile Update
- 增量学习
- 权重衰减
- 定期同步

## Usage in AI

生成回复时应用风格：
```kotlin
val profile = styleRepository.getProfile()
val prompt = buildPrompt(context, profile)
val reply = aiService.generateReply(prompt)
```

## Storage

存储在 [[Database Schema]] 的 `user_style_profiles` 表

## Related
- [[AI Integration]]
- [[Reply Generator]]
- [[Cloud Sync]]
- [[Database Schema]]