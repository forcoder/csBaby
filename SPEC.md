# 客服小秘 Android App - 技术规格说明书 (SPEC)

## 1. 项目概述和目标

### 1.1 项目概述
"客服小秘"是一款智能客服辅助Android应用程序，旨在帮助客服人员提高工作效率和回复质量。应用通过AI技术自动生成回复建议，结合知识库匹配和用户风格学习，为客服人员提供智能化的回复支持。

### 1.2 核心目标
1. **自动化回复建议**：在用户选择的应用中自动弹出回复建议窗口
2. **智能内容生成**：基于关键词匹配和AI生成提供准确的回复内容
3. **个性化风格学习**：通过学习用户历史回复形成个性化的回复风格
4. **多模型支持**：支持配置和管理多个大语言模型
5. **便捷操作**：一键发送，减少重复劳动

### 1.3 目标用户
- 电商客服人员
- 社交媒体客服
- 在线客服团队
- 个人自由职业者需要处理大量重复咨询

### 1.4 关键特性
1. **应用监控**：监控指定应用的新消息，自动弹出回复窗口
2. **智能回复生成**：关键词匹配 + AI生成混合模式
3. **知识库管理**：可配置关键词和对应回复规则
4. **风格学习**：AI学习用户回复风格并模仿
5. **多模型管理**：支持OpenAI、Claude等大模型切换

## 2. 技术栈选择

### 2.1 核心技术栈
- **开发语言**：Kotlin 1.9+
- **UI框架**：Jetpack Compose
- **数据库**：Room Database
- **依赖注入**：Hilt
- **架构模式**：MVVM + Clean Architecture
- **网络请求**：Retrofit + OkHttp
- **异步处理**：Coroutines + Flow
- **权限管理**：Android Permissions API

### 2.2 第三方库
- **消息监控**：AccessibilityService + NotificationListener
- **文件处理**：Android Storage Access Framework
- **AI集成**：
  - OpenAI API
  - Claude API
  - 国内大模型API（智谱、通义等）
- **数据存储**：DataStore（配置存储）
- **UI组件**：Material Design 3
- **测试框架**：JUnit 5, Espresso, MockK

### 2.3 开发环境要求
- **Android SDK**：API 34+ (Android 14)
- **Android Studio**：Flamingo 2022.2.1+
- **Gradle**：8.2+
- **JDK**：17+

## 3. 详细的UI/UX设计

### 3.1 整体设计风格
- **设计系统**：Material Design 3
- **配色方案**：主色调蓝色（专业、可信赖），辅助色绿色（效率、成功）
- **字体**：Roboto + Noto Sans SC（中英文适配）
- **图标**：Material Icons

### 3.2 主要界面设计

#### 3.2.1 主界面
- **底部导航**：首页、知识库、模型配置、个人中心
- **快捷操作卡片**：快速启用/禁用监控、一键发送统计
- **最近回复**：显示最近生成的回复建议

#### 3.2.2 应用选择界面
- **应用列表**：显示已安装的通讯类应用
- **选择开关**：每个应用旁的选择开关
- **权限提示**：首次使用时引导开启通知权限

#### 3.2.3 回复窗口（浮动窗口）
- **位置**：屏幕底部或键盘上方
- **内容区域**：
  - 原消息预览
  - AI生成回复（可编辑）
  - 知识库匹配提示
- **操作按钮**：发送、修改、取消、查看详情
- **响应式设计**：适配不同屏幕尺寸和方向

#### 3.2.4 知识库管理界面
- **分类管理**：按业务类型分类（如：售前咨询、售后问题、投诉处理）
- **关键词规则**：
  - 关键词/短语匹配
  - 适用场景选择（全局/特定房源/特定产品）
  - 回复模板（支持变量替换）
- **批量导入**：支持CSV/Excel格式批量导入
- **搜索过滤**：按关键词、分类、场景筛选

#### 3.2.5 模型配置界面
- **模型列表**：显示已配置的大模型
- **模型详情**：
  - API密钥管理
  - 请求参数配置（温度、最大token数）
  - 费用统计
- **模型切换**：设置默认模型和备用模型
- **测试连接**：测试模型连接和响应

#### 3.2.6 风格学习界面
- **风格分析**：展示AI分析的用户回复风格特征
- **学习进度**：显示模型训练进度和准确率
- **风格调整**：手动调整风格参数（正式度、热情度、专业度）
- **历史数据**：查看学习使用的历史回复数据

### 3.3 交互设计
1. **简洁操作**：核心操作3步内完成
2. **实时反馈**：操作后立即有视觉或震动反馈
3. **容错设计**：重要操作二次确认
4. **离线支持**：核心功能支持离线使用
5. **无障碍支持**：支持TalkBack等辅助功能

## 4. 数据模型设计

### 4.1 核心数据模型

#### 4.1.1 AppConfig（应用配置）
```kotlin
@Entity(tableName = "app_configs")
data class AppConfig(
    @PrimaryKey val packageName: String,
    val appName: String,
    val iconUri: String?,
    val isMonitored: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis()
)
```

#### 4.1.2 KeywordRule（关键词规则）
```kotlin
@Entity(tableName = "keyword_rules")
data class KeywordRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val matchType: MatchType, // EXACT, CONTAINS, REGEX
    val replyTemplate: String,
    val category: String, // 分类：售前、售后、投诉等
    val applicableScenarios: List<String>, // 适用场景ID列表
    val priority: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class MatchType {
    EXACT, CONTAINS, REGEX
}
```

#### 4.1.3 Scenario（适用场景）
```kotlin
@Entity(tableName = "scenarios")
data class Scenario(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ScenarioType, // ALL_PROPERTIES, SPECIFIC_PROPERTY, PRODUCT
    val targetId: String?, // 特定房源ID或产品ID
    val description: String?,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ScenarioType {
    ALL_PROPERTIES, // 所有房源
    SPECIFIC_PROPERTY, // 具体房源
    SPECIFIC_PRODUCT // 具体产品
}
```

#### 4.1.4 AIModelConfig（AI模型配置）
```kotlin
@Entity(tableName = "ai_model_configs")
data class AIModelConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modelType: ModelType, // OPENAI, CLAUDE, ZHIPU, TONGYI
    val modelName: String,
    val apiKey: String, // 加密存储
    val apiEndpoint: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1000,
    val isDefault: Boolean = false,
    val isEnabled: Boolean = true,
    val monthlyCost: Double = 0.0,
    val lastUsed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

enum class ModelType {
    OPENAI, CLAUDE, ZHIPU, TONGYI, CUSTOM
}
```

#### 4.1.5 UserStyleProfile（用户风格画像）
```kotlin
@Entity(tableName = "user_style_profiles")
data class UserStyleProfile(
    @PrimaryKey val userId: String,
    val formalityLevel: Float = 0.5f, // 0-1：正式度
    val enthusiasmLevel: Float = 0.5f, // 0-1：热情度
    val professionalismLevel: Float = 0.5f, // 0-1：专业度
    val wordCountPreference: Int = 50, // 平均字数偏好
    val commonPhrases: List<String> = emptyList(), // 常用短语
    val avoidPhrases: List<String> = emptyList(), // 避免使用的短语
    val learningSamples: Int = 0, // 学习样本数
    val accuracyScore: Float = 0.0f, // 准确率评分
    val lastTrained: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
```

#### 4.1.6 ReplyHistory（回复历史）
```kotlin
@Entity(tableName = "reply_history")
data class ReplyHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceApp: String, // 来源应用包名
    val originalMessage: String,
    val generatedReply: String,
    val finalReply: String, // 用户最终发送的内容
    val ruleMatchedId: Long?, // 匹配的规则ID
    val modelUsedId: Long?, // 使用的模型ID
    val styleApplied: Boolean = false, // 是否应用了风格
    val sendTime: Long = System.currentTimeMillis(),
    val modified: Boolean = false // 用户是否修改过
)
```

### 4.2 数据库设计
```sql
-- 应用配置表
CREATE TABLE app_configs (
    package_name TEXT PRIMARY KEY,
    app_name TEXT NOT NULL,
    icon_uri TEXT,
    is_monitored INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL,
    last_used INTEGER NOT NULL
);

-- 关键词规则表
CREATE TABLE keyword_rules (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    keyword TEXT NOT NULL,
    match_type TEXT NOT NULL,
    reply_template TEXT NOT NULL,
    category TEXT NOT NULL,
    priority INTEGER DEFAULT 0,
    enabled INTEGER DEFAULT 1,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- 规则-场景关联表（多对多）
CREATE TABLE rule_scenario_relation (
    rule_id INTEGER NOT NULL,
    scenario_id INTEGER NOT NULL,
    PRIMARY KEY (rule_id, scenario_id),
    FOREIGN KEY (rule_id) REFERENCES keyword_rules(id) ON DELETE CASCADE,
    FOREIGN KEY (scenario_id) REFERENCES scenarios(id) ON DELETE CASCADE
);
```

## 5. 核心功能模块划分

### 5.1 模块架构
```
客服小秘App
├── 应用层 (Presentation Layer)
│   ├── UI组件 (Compose UI Components)
│   ├── 视图模型 (ViewModels)
│   └── 导航 (Navigation)
│
├── 领域层 (Domain Layer)
│   ├── 用例 (Use Cases)
│   ├── 实体 (Entities)
│   └── 仓储接口 (Repository Interfaces)
│
├── 数据层 (Data Layer)
│   ├── 本地数据源 (Local Data Source - Room)
│   ├── 远程数据源 (Remote Data Source - API Clients)
│   ├── 仓储实现 (Repository Implementations)
│   └── 数据转换器 (Data Mappers)
│
└── 基础设施层 (Infrastructure Layer)
    ├── 消息监控服务 (Message Monitor Service)
    ├── AI服务 (AI Service)
    ├── 文件处理 (File Handling)
    └── 权限管理 (Permission Manager)
```

### 5.2 核心模块详述

#### 5.2.1 消息监控模块
- **功能**：监控指定应用的新消息通知
- **技术实现**：NotificationListenerService + AccessibilityService
- **职责**：
  1. 监听通知栏消息
  2. 提取消息内容和来源
  3. 触发回复生成流程
  4. 显示浮动回复窗口

#### 5.2.2 AI回复生成模块
- **功能**：根据消息内容生成回复建议
- **工作流程**：
  1. 关键词匹配 → 知识库规则优先
  2. 无匹配 → AI生成回复
  3. 应用用户风格 → 风格调整
  4. 返回优化后的回复
- **算法**：
  - 关键词匹配算法（Trie树优化）
  - 风格向量计算（基于历史回复）
  - 回复质量评分

#### 5.2.3 知识库管理模块
- **功能**：关键词规则的管理和维护
- **特性**：
  - 规则导入/导出
  - 批量操作
  - 冲突检测（重复关键词提示）
  - 使用统计（规则使用频率）

#### 5.2.4 模型管理模块
- **功能**：大模型配置和切换
- **特性**：
  - API密钥安全存储（Android Keystore）
  - 模型性能监控
  - 费用统计和预警
  - 模型健康检查

#### 5.2.5 风格学习模块
- **功能**：学习和模仿用户回复风格
- **算法**：
  1. 收集用户发送的回复作为训练数据
  2. 文本特征提取（情感、句式、词汇）
  3. 生成风格向量
  4. 应用到AI生成过程

#### 5.2.6 浮动窗口模块
- **功能**：显示回复建议窗口
- **技术**：WindowManager + Compose
- **特性**：
  - 可拖动、可调整大小
  - 自适应键盘高度
  - 多应用切换支持

## 6. API接口设计

### 6.1 内部API（模块间通信）

#### 6.1.1 MessageMonitor API
```kotlin
interface MessageMonitor {
    suspend fun startMonitoring(apps: List<String>)
    suspend fun stopMonitoring()
    fun addMessageListener(listener: MessageListener)
    fun removeMessageListener(listener: MessageListener)
}

interface MessageListener {
    fun onNewMessage(appPackage: String, message: String, extras: Bundle?)
}
```

#### 6.1.2 ReplyGenerator API
```kotlin
interface ReplyGenerator {
    suspend fun generateReply(
        message: String,
        context: ReplyContext
    ): ReplyResult
    
    suspend fun learnFromUserReply(
        originalMessage: String,
        userReply: String,
        context: ReplyContext
    )
}

data class ReplyContext(
    val appPackage: String,
    val scenarioId: String? = null,
    val userId: String
)

data class ReplyResult(
    val reply: String,
    val source: ReplySource, // RULE_MATCH, AI_GENERATED
    val confidence: Float,
    val ruleId: Long? = null,
    val modelId: Long? = null
)
```

#### 6.1.3 AIService API
```kotlin
interface AIService {
    suspend fun generateCompletion(
        prompt: String,
        config: GenerationConfig
    ): String
    
    suspend fun analyzeTextStyle(text: String): TextStyleAnalysis
    
    suspend fun adjustStyle(
        text: String,
        styleProfile: UserStyleProfile
    ): String
}

data class GenerationConfig(
    val modelId: Long,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1000,
    val styleAdjustment: Boolean = true
)
```

### 6.2 外部API集成

#### 6.2.1 OpenAI API
- **端点**：`https://api.openai.com/v1/chat/completions`
- **认证**：Bearer Token (API Key)
- **请求体**：
```json
{
  "model": "gpt-3.5-turbo",
  "messages": [
    {"role": "system", "content": "你是专业的客服助手..."},
    {"role": "user", "content": "用户消息"}
  ],
  "temperature": 0.7,
  "max_tokens": 1000
}
```

#### 6.2.2 知识库导入API
- **格式支持**：CSV, JSON, Excel
- **CSV格式示例**：
```
keyword,match_type,reply_template,category,priority
"价格",CONTAINS,"当前价格是{price}元，详情请咨询客服",售前,1
"发货",EXACT,"您的订单将在24小时内发货",售后,2
```

## 7. 架构图

### 7.1 系统架构图
```
┌─────────────────────────────────────────────────────────┐
│                    Android 客户端                          │
├─────────────────────────────────────────────────────────┤
│   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐  │
│   │   UI层       │   │  业务逻辑层   │   │  数据层      │  │
│   │  (Compose)  │◄─►│ (Use Cases) │◄─►│ (Repository)│  │
│   └─────────────┘   └─────────────┘   └──────┬──────┘  │
│                                               │         │
│   ┌─────────────┐   ┌─────────────┐   ┌──────▼──────┐  │
│   │  浮动窗口     │   │  服务层      │   │  数据库      │  │
│   │ (WindowMgr) │   │ (Services)  │   │   (Room)   │  │
│   └─────────────┘   └─────────────┘   └─────────────┘  │
└─────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────┐
│                    外部服务                                │
├─────────────────────────────────────────────────────────┤
│   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐  │
│   │  OpenAI API │   │ Claude API  │   │  其他AI服务  │  │
│   └─────────────┘   └─────────────┘   └─────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 7.2 数据流图
```
用户操作/系统事件 → UI层 → ViewModel → UseCase → Repository
     ↓                                            ↓
   响应更新                                      数据源
     ↓                                            ↓
   UI更新                             本地DB ←→ 远程API
                                      (Room)    (AI服务)
```

### 7.3 组件依赖图
```
App (Hilt Component)
├── MainActivity
├── AppNavigator
├── ViewModelFactory
│
├── Presentation Module
│   ├── UI Components
│   ├── ViewModels
│   └── Navigation
│
├── Domain Module
│   ├── Use Cases
│   └── Repository Interfaces
│
├── Data Module
│   ├── Local Data Source (Room)
│   ├── Remote Data Source (Retrofit)
│   └── Repository Implementations
│
└── Infrastructure Module
    ├── MessageMonitorService
    ├── AIService
    ├── FileManager
    └── PermissionManager
```

### 7.4 安全架构
1. **数据加密**：
   - API密钥：Android Keystore加密存储
   - 敏感数据：Room数据库加密
   - 网络通信：TLS 1.3

2. **权限控制**：
   - 运行时权限申请（通知、悬浮窗）
   - 最小权限原则
   - 权限使用说明

3. **隐私保护**：
   - 用户数据本地存储优先
   - AI请求去标识化处理
   - 数据导出加密

## 8. 项目里程碑

### 8.1 Phase 1：基础框架 (2周)
- 项目初始化和架构搭建
- 基础UI框架和导航
- Room数据库配置
- Hilt依赖注入配置

### 8.2 Phase 2：核心功能 (3周)
- 消息监控服务实现
- 知识库管理功能
- 基础回复生成（规则匹配）
- 浮动窗口实现

### 8.3 Phase 3：AI集成 (2周)
- OpenAI/Claude API集成
- AI回复生成功能
- 模型管理界面
- 基本风格学习

### 8.4 Phase 4：高级功能 (2周)
- 高级风格学习算法
- 批量导入/导出
- 数据统计和分析
- 性能优化和测试

### 8.5 Phase 5：发布准备 (1周)
- 用户体验优化
- 性能测试
- 安全审计
- 商店发布准备

## 9. 非功能性需求

### 9.1 性能要求
- **启动时间**：冷启动 < 2秒
- **回复生成**：AI生成 < 3秒，规则匹配 < 100ms
- **内存占用**：常驻内存 < 100MB
- **电池消耗**：后台监控 < 2%/小时

### 9.2 兼容性要求
- **Android版本**：Android 8.0+ (API 26+)
- **屏幕适配**：支持手机和平板
- **分辨率**：适配多种DPI
- **语言**：中文（简体/繁体）、英文

### 9.3 安全要求
- 数据传输加密
- 本地数据加密
- 权限最小化
- 代码混淆和加固

### 9.4 可维护性
- 模块化设计，低耦合
- 完整单元测试覆盖
- 清晰的文档和注释
- 持续集成/部署流程

## 10. 风险评估和缓解措施

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| 消息监控权限被限制 | 高 | 中 | 提供替代方案，如复制粘贴检测 |
| AI服务API费用超支 | 中 | 高 | 设置使用限制和预警机制 |
| 风格学习准确率低 | 中 | 中 | 提供手动调整功能，增加训练样本 |
| 多应用兼容性问题 | 高 | 高 | 建立应用兼容性测试矩阵 |
| 数据隐私合规风险 | 高 | 低 | 严格遵守隐私政策，数据本地化处理 |

---

*文档版本：1.0*
*创建日期：2026年4月6日*
*更新日期：2026年4月6日*
*作者：架构师团队*