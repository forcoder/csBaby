# Reply Generator

## Overview

回复生成器协调知识库匹配和AI生成：

### Flow

```
消息 → 知识库匹配 → 命中? → 是 → 模板替换 → 返回
                    ↓ 否
              AI生成 → 风格应用 → 返回
```

## Template Engine

### 变量替换
```
您好{{user}}，您的订单{{orderId}}已{{status}}
```

### 变量来源
- 用户信息
- 订单详情
- 时间上下文

## Quality Scoring

```kotlin
data class QualityScore(
    val relevance: Float,      // 0-1
    val completeness: Float,   // 0-1
    val appropriateness: Float // 0-1
)
```

### 评分规则
- 关键词覆盖率
- 上下文相关性
- 语气适配度

## Fallback Strategy

1. 知识库精确匹配
2. 知识库模糊匹配
3. AI生成 (默认模型)
4. AI生成 (备用模型)
5. 默认回复

## History Management

- 记录每次生成
- 支持用户编辑
- 学习用户偏好

## Related
- [[Knowledge Base]]
- [[AI Integration]]
- [[Style Learning]]
- [[Message Monitoring]]