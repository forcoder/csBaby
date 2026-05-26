# AI回复生成 — 产品能力定义

> 基于 `/product-capability` 框架生成
> 版本: 1.0.0 | 日期: 2026-05-27

---

## CAPABILITY

csBaby 的 AI 回复生成能力为客服人员提供**实时、智能、个性化**的回复建议。当用户指定的即时通讯应用（微信、百居易、美团民宿等）收到新消息时，系统通过通知监听服务捕获消息内容，经过知识库关键词匹配 → AI 模型生成 → 风格调整三级流水线，在悬浮窗中展示建议回复，支持一键复制或自动发送，将客服响应时间从分钟级压缩到秒级。

---

## CONSTRAINTS

### 固定规则

1. **知识库优先**: 关键词规则匹配（Trie 树）始终先于 AI 调用，降低延迟和成本
2. **三级降级链路**: 知识库命中 → AI 生成 → 兜底默认回复，不允许空结果返回给 UI
3. **空结果兜底**: 当知识库无匹配且 AI 不可用时，返回固定回复 `"感谢您的留言，我们会尽快处理您的问题。"`
4. **消息过滤前置**: 占位通知（如"给你发送了新消息"、"[图片]"、"[表情]"等）在进入生成流水线前必须过滤
5. **应用白名单**: 仅处理用户明确选中的监控应用包名列表
6. **监控开关前置**: `monitoringEnabled=false` 时直接跳过所有处理
7. **取消传播**: 新消息到达时取消前一个未完成的生成任务（`currentJob?.cancel()`），避免结果乱序
8. **Result<T> 错误封装**: 所有可能失败的操作（AI 调用、风格分析）必须使用 `Result<T>` 返回，禁止抛异常到 UI 层

### 不可变约束

- **单向数据流**: View → ViewModel → ReplyOrchestrator → ReplyGenerator → AIService → AIClient，禁止反向数据流
- **UI 层零业务逻辑**: Activity/Fragment 只负责渲染和事件分发
- **ViewModel 无 View 引用**: 通过 StateFlow 暴露不可变状态
- **API 密钥不明文日志输出**: 日志中不得打印 apiKey 值

### 边界

- 单条消息生成超时由 OkHttp 客户端超时控制（当前默认配置）
- AI 生成 `maxTokens` 硬上限 500，`temperature` 固定 0.7
- 风格学习最少需要 5 个样本才触发 AI 深度分析
- 批量建议模式最多返回 `count` 条（默认 3），优先知识库匹配，不足时补充 AI 结果

---

## IMPLEMENTATION CONTRACT

### 参与者

| 参与者 | 角色 | 职责 |
|--------|------|------|
| 客服人员 | 终端用户 | 接收回复建议、选择/编辑/发送回复 |
| MessageMonitor | 消息采集 | 通过 NotificationListenerService 捕获通知，产出 `MonitoredMessage` |
| ReplyOrchestrator | 编排调度 | 订阅消息流，构建上下文，调用生成器，展示悬浮窗 |
| ReplyGenerator | 核心生成 | 三级流水线编排：知识库匹配 → AI 生成 → 兜底 |
| KnowledgeBaseManager + KeywordMatcher | 知识库引擎 | Trie 树关键词匹配，支持 EXACT/CONTAINS/REGEX 三种模式 |
| AIService | AI 服务层 | 模型选择、Prompt 构建、风格分析/调整、费用统计 |
| AIClient / AIClientImpl | HTTP 客户端 | 多模型适配（OpenAI/Claude/Zhipu/Tongyi/Custom），请求/响应格式转换 |
| StyleLearningEngine | 风格学习 | 本地信号分析 + AI 深度分析，增量更新用户画像 |
| FloatingWindowService | UI 展示 | 悬浮窗/气泡展示回复建议，支持复制、编辑、一键发送 |

### 界面 / 表面

| 表面 | 类型 | 描述 |
|------|------|------|
| 悬浮窗面板 | 原生 Android View | 展示原文、建议回复、来源标签、置信度、操作按钮 |
| 气泡模式 | 原生 Android View | 可拖拽小圆点，点击展开面板 |
| 知识库管理页 | Jetpack Compose | 关键词规则的增删改查、分类、目标应用筛选 |
| AI 模型配置页 | Jetpack Compose | 模型增删、API Key 配置、默认模型选择、连接测试 |
| 个人风格设置页 | Jetpack Compose | 风格参数调整、学习状态查看 |

### 状态与转换

```
[消息到达]
    │
    ▼
[消息过滤] ──(占位通知/非白名单应用/监控关闭)──→ [丢弃]
    │
    ▼
[构建 ReplyContext]
    │
    ▼
[知识库匹配] ──(命中且 confidence ≥ 0.5)──→ [返回 RULE_MATCH 结果]
    │ (未命中)
    ▼
[AI 生成] ──(模型可用 && API Key 有效)──→ [AI 生成] ──(风格学习开启)──→ [风格调整]──→ [返回 AI_GENERATED 结果]
    │                                        │
    │                                        └─(风格学习关闭)──→ [返回 AI_GENERATED 结果]
    │ (AI 不可用/调用失败)
    ▼
[兜底默认回复] ──→ [返回 RULE_MATCH 结果, confidence=0.1]
    │
    ▼
[展示悬浮窗]
    │
    ▼
[用户操作: 复制/编辑/发送/忽略]
    │
    ▼
[记录 ReplyHistory] ──→ [风格学习引擎增量更新]
```

### 接口 / 数据

**核心输入:**
```kotlin
data class MonitoredMessage(
    val packageName: String,
    val title: String,
    val content: String,
    val conversationTitle: String?,
    val isGroupConversation: Boolean,
    val timestamp: Long,
    val appName: String
)

data class ReplyContext(
    val appPackage: String,
    val scenarioId: String?,
    val conversationTitle: String?,
    val propertyName: String?,
    val isGroupConversation: Boolean,
    val userId: String
)
```

**核心输出:**
```kotlin
data class ReplyResult(
    val reply: String,          // 建议回复文本
    val source: ReplySource,    // RULE_MATCH | AI_GENERATED
    val confidence: Float,      // 0.0 ~ 1.0
    val ruleId: Long?,          // 匹配的知识库规则 ID
    val modelId: Long?          // 使用的 AI 模型 ID
)

enum class ReplySource { RULE_MATCH, AI_GENERATED }
```

**生成器接口:**
```kotlin
suspend fun generateReply(message: String, context: ReplyContext): ReplyResult
suspend fun generateSuggestions(message: String, context: ReplyContext, count: Int = 3): List<ReplyResult>
suspend fun recordUserReply(originalMessage: String, generatedReply: String, finalReply: String, context: ReplyContext, result: ReplyResult)
```

### 数据模型影响

| 模型 | 影响 |
|------|------|
| KeywordRule | 知识库匹配的源数据，支持 EXACT/CONTAINS/REGEX 三种匹配类型，按 priority 排序 |
| AIModelConfig | AI 调用配置，包含 apiKey（加密存储）、endpoint、temperature、maxTokens |
| UserStyleProfile | 风格学习产出，包含 formalityLevel/enthusiasmLevel/professionalismLevel 三维度 + 常用短语 |
| ReplyHistory | 用户行为记录，用于风格学习和效果分析 |

### 安全 / 计费 / 策略约束

- API Key 使用 EncryptedSharedPreferences 存储，不明文写入日志
- 每次 AI 调用后更新费用统计（`aiModelRepository.addCost`），按 ModelType 估算单价
- 所有 HTTP 请求强制 HTTPS
- OkHttp 客户端配置超时，防止网络挂起阻塞协程

### 可观测性

- 全链路日志标记：百居易消息使用 `isBaijuyiMessage()` 标记，便于追踪
- 日志截断：`previewForLog()` 限制 120 字符，换行符转义
- 关键节点日志：消息到达、知识库命中/未命中、AI 开始/成功/失败、悬浮窗展示

---

## NON-GOALS

本能力范围**不包含**:

1. **消息发送执行**: 自动发送由无障碍服务（AutoSendAccessibilityService）负责，不在回复生成能力范围内
2. **消息监控采集**: NotificationListenerService 的消息采集属于独立能力
3. **知识库管理 UI**: 知识库的增删改查页面属于知识库管理能力
4. **AI 模型市场/发现**: 不提供模型推荐、模型排行榜等功能，用户自行配置
5. **多轮对话上下文**: 当前仅基于单条消息生成回复，不维护对话历史上下文
6. **回复质量评分**: 不对 AI 生成的回复做自动质量打分（仅记录用户是否修改）
7. **A/B 测试**: 不支持同时生成多个版本做效果对比

---

## OPEN QUESTIONS

1. **多轮对话上下文**: 是否需要将最近 N 条消息作为上下文传入 AI，提升回复连贯性？这会增加 token 消耗和延迟。
2. **生成超时控制**: 当前依赖 OkHttp 默认超时，是否需要为 AI 生成单独设置更严格的超时（如 10s）？
3. **知识库规则与 AI 结果的置信度校准**: 知识库匹配固定返回规则匹配的 confidence，AI 生成固定返回 0.8，是否需要动态校准？
4. **风格学习的隐私边界**: 风格分析需要发送用户历史回复给 AI 模型，是否需要明确的用户授权？
5. **离线模式**: 当 AI 不可用时，是否允许用户选择"仅知识库模式"并给出明确提示？
6. **多模型并行生成**: 是否支持同时调用多个 AI 模型，选择最优结果？
7. **知识库规则的变量替换**: `KeywordMatcher.applyTemplate()` 已集成到 `generateReplyFromRule()` 中，但 `ReplyGenerator` 调用时始终传入空 `variables`，模板中的 `{price}` 等变量不会被实际替换。需要明确变量来源（从消息上下文提取？用户配置？）。
8. **批量建议的排序策略**: `generateSuggestions` 中知识库结果和 AI 结果的混合排序逻辑是否需要优化？

---

## HANDOFF

### 能力状态: 已实现，需迭代优化

核心链路已完整实现并验证通过（E2E）。以下方向可按需迭代:

### 推荐 ECC 工作流

| 优先级 | 工作项 | 推荐工作流 |
|--------|--------|-----------|
| P0 | 知识库规则变量替换（`variables` 始终为空，模板变量形同虚设） | `tdd-workflow` |
| P1 | 生成超时控制 | `tdd-workflow` |
| P1 | 多轮对话上下文支持 | `project-flow-ops` → `tdd-workflow` |
| P2 | 风格学习隐私授权 | `workspace-surface-audit` |
| P2 | 置信度动态校准 | `tdd-workflow` |

### 关键文件索引

| 文件 | 职责 |
|------|------|
| `infrastructure/reply/ReplyOrchestrator.kt` | 编排调度入口，消息流订阅 |
| `infrastructure/reply/ReplyGenerator.kt` | 三级流水线核心 |
| `infrastructure/ai/AIService.kt` | AI 服务层，风格分析/调整 |
| `data/remote/AIClient.kt` | HTTP 客户端，多模型适配 |
| `data/remote/AIClient.kt` | HTTP 客户端，多模型适配 |
| `infrastructure/knowledge/KeywordMatcher.kt` | Trie 树关键词匹配 |
| `infrastructure/knowledge/KnowledgeBaseManager.kt` | 知识库管理 |
| `infrastructure/style/StyleLearningEngine.kt` | 风格学习引擎 |
| `infrastructure/window/FloatingWindowService.kt` | 悬浮窗展示 |
| `domain/model/ReplyContext.kt` | 回复上下文模型 |
| `domain/model/ReplyResult.kt` | 回复结果模型 |

---

*本文档为产品能力合同，实现细节以代码为准。当代码与本文档冲突时，以代码实现为基准更新文档。*
