# AI Integration

## Supported Providers

| Provider | Model | API |
|----------|-------|-----|
| OpenAI | GPT-4, GPT-3.5 | chat.completions |
| Anthropic | Claude-3 | messages |
| 智谱AI | GLM-4 | glm-4 |
| 通义千问 | qwen-turbo | dashscope |
| 文心一言 | ernie-bot | yiyan |

## Architecture

```
┌─────────────────────────────────────┐
│           AIService                  │
│  (统一接口，工厂模式)                  │
├─────────────────────────────────────┤
│  OpenAI Client │ Claude Client │ ... │
│     AIClient.kt                      │
└─────────────────────────────────────┘
```

## Usage

```kotlin
interface AIService {
    suspend fun generateReply(
        context: String,
        style: UserStyleProfile?
    ): Result<String>
}
```

## Prompt Engineering

### System Prompt
```
你是一个专业的客服助手，回复要：
1. 专业且友好
2. 简洁明了
3. 解决问题导向
```

### Style Integration
- 整合 [[Style Learning]] 的用户风格
- 调整正式度、热情度
- 避免特定短语

## Error Handling

- 超时重试 (3次)
- 降级策略 (切换模型)
- 优雅降级 (返回默认回复)

## Related
- [[Style Learning]]
- [[Reply Generator]]
- [[Database Schema]]
- [[Coding Standards]]