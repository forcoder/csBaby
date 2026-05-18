# 客服小秘 - 智能客服自动回复APP

一个基于Android的智能客服自动回复应用，支持消息监听、知识库管理、AI大模型集成和用户风格学习。

## 功能特性

### 1. 消息监听与自动回复
- 支持选择监听的APP（新消息通知）
- 自动弹出回复确认窗口（需用户点击发送）
- 消息预览和快速回复

### 2. OTA热更新系统
- 应用内一键升级更新
- 自动检查新版本（每24小时）
- 后台下载和自动安装
- 支持强制更新和增量更新
- 完整的更新日志和版本管理

### 2. 知识库管理
- 关键词规则配置
- 支持多种匹配类型（精确匹配、模糊匹配、正则表达式）
- 场景配置（适用所有房源或特定房源/产品）
- 导入/导出功能

### 3. AI大模型集成
- 支持多个AI服务商：
  - OpenAI (GPT-4, GPT-3.5)
  - Anthropic (Claude)
  - 智谱AI (GLM-4)
  - 阿里云 (通义千问)
  - 百度AI (文心一言)
- API密钥管理
- 参数配置（温度、最大Token数）

### 4. 用户风格学习
- 分析历史回复数据
- 学习用户风格特征：
  - 正式度
  - 热情度
  - 专业度
- 常用短语和避免短语管理

### 5. OTA热更新
- **应用内更新**：无需重新下载安装包
- **自动检查**：每24小时自动检查新版本
- **智能下载**：支持断点续传和后台下载
- **安全安装**：使用Android FileProvider安全机制
- **版本管理**：完整的更新日志和版本控制
- **灰度发布**：支持部分用户先更新

## 技术栈

- **语言**: Kotlin 1.9.21
- **UI框架**: Jetpack Compose
- **数据库**: Room 2.6.1
- **依赖注入**: Hilt 2.48.1
- **网络**: Retrofit 2.9.0
- **异步**: Coroutines + Flow
- **状态管理**: ViewModel + StateFlow

## 项目结构

```
csBaby/
├── app/
│   └── src/main/java/com/csbaby/kefu/
│       ├── data/                 # 数据层
│       │   ├── local/           # 本地数据库
│       │   │   ├── entity/      # 数据实体
│       │   │   ├── dao/         # 数据访问对象
│       │   │   └── PreferencesManager.kt
│       │   ├── remote/          # 远程API
│       │   │   └── AIClient.kt  # AI客户端
│       │   └── repository/      # Repository实现
│       │
│       ├── domain/              # 领域层
│       │   ├── model/           # 领域模型
│       │   └── repository/      # Repository接口
│       │
│       ├── di/                  # 依赖注入
│       │   └── *.kt            # Hilt模块
│       │
│       ├── infrastructure/      # 基础设施层
│       │   ├── notification/   # 消息监听服务
│       │   ├── knowledge/       # 知识库管理
│       │   ├── ai/             # AI服务
│       │   ├── style/          # 风格学习引擎
│       │   ├── reply/          # 回复生成器
│       │   └── window/         # 浮动窗口服务
│       │
│       └── presentation/       # 展示层
│           ├── MainActivity.kt
│           ├── navigation/     # 导航
│           ├── screens/        # 界面
│           └── theme/          # 主题
│
├── SPEC.md                     # 需求分析文档
└── ARCHITECTURE_DIAGRAM.md     # 架构图
```

## 核心模块

### 消息监听 (NotificationListenerService)
```kotlin
// 监听指定APP的通知
// 使用 NotificationListenerService 实现
// 支持过滤特定应用的通知
```

### 知识库匹配 (KeywordMatcher)
```kotlin
// Trie树优化的关键词匹配
// 支持精确匹配、模糊匹配、正则表达式
// 场景过滤逻辑
```

### AI服务 (AIService)
```kotlin
// 统一AI服务接口
// 支持多个AI服务商
// 统一的错误处理和重试机制
```

### 风格学习 (StyleLearningEngine)
```kotlin
// 分析历史回复
// 提取风格特征向量
// 生成个性化回复建议
```

## 界面预览

1. **首页** - 监控状态、监听应用、消息统计
2. **知识库** - 关键词规则管理、分类筛选
3. **模型配置** - AI模型列表、API配置
4. **个人中心** - 风格学习、设置

## 安装使用

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34 (API 34)

### 构建步骤

1. **克隆项目**
```bash
git clone <repository_url>
cd csBaby
```

2. **配置本地环境**
确保 `local.properties` 文件包含正确的 SDK 路径：
```properties
sdk.dir=/path/to/android/sdk
```

3. **构建Debug APK**
```bash
./gradlew assembleDebug
```

4. **安装到设备**
```bash
./gradlew installDebug
```

5. **配置通知访问权限**
首次运行需要在系统设置中开启：
- 通知访问权限
- 悬浮窗权限
- 自启动权限

## 数据模型

### AppConfig - 监听应用配置
| 字段 | 类型 | 说明 |
|------|------|------|
| packageName | String | 应用包名 |
| appName | String | 应用名称 |
| isMonitored | Boolean | 是否监听 |

### KeywordRule - 关键词规则
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 唯一标识 |
| keyword | String | 关键词 |
| matchType | MatchType | 匹配类型 |
| replyTemplate | String | 回复模板 |
| priority | Int | 优先级 |
| isEnabled | Boolean | 是否启用 |

### AIModelConfig - AI模型配置
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 唯一标识 |
| provider | String | 服务商 |
| apiKey | String | API密钥 |
| modelName | String | 模型名称 |
| isDefault | Boolean | 是否默认 |

### UserStyleProfile - 用户风格
| 字段 | 类型 | 说明 |
|------|------|------|
| formality | Float | 正式度 (0-1) |
| enthusiasm | Float | 热情度 (0-1) |
| professionalism | Float | 专业度 (0-1) |
| commonPhrases | List | 常用短语 |
| avoidPhrases | List | 避免短语 |

### ReplyHistory - 回复历史
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 唯一标识 |
| originalMessage | String | 原消息 |
| reply | String | 回复内容 |
| timestamp | Long | 时间戳 |
| sourceApp | String | 来源应用 |
| usedTemplate | Boolean | 是否使用模板 |

## 回复生成流程

```
新消息到达
    ↓
检查监听列表
    ↓
关键词匹配 (知识库)
    ↓ (命中)
应用对应规则
    ↓ (未命中)
AI生成回复 (风格学习)
    ↓
生成回复内容
    ↓
弹出确认窗口
    ↓ (用户确认)
发送回复
    ↓
记录历史数据
    ↓
更新风格模型
```

## 配置说明

### 通知访问权限
需要用户手动开启：
1. 设置 → 应用 → 客服小秘 → 通知
2. 允许通知访问

### 悬浮窗权限
1. 设置 → 应用 → 客服小秘 → 悬浮窗
2. 允许悬浮窗

### 自启动权限
1. 设置 → 应用 → 客服小秘 → 自启动
2. 允许自启动

## 开发说明

### 添加新的AI服务商

1. 在 `AIClient.kt` 中添加新的服务商适配器
2. 实现 `AIService` 接口
3. 在 `AIServiceFactory` 中注册

### 添加新的数据实体

1. 在 `entity/` 目录下创建 Entity 类
2. 在 `dao/` 目录下创建 DAO 接口
3. 在 `KefuDatabase.kt` 中注册
4. 生成 Repository 接口和实现

## License

MIT License
