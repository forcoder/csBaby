# 房源推荐功能设计文档

> **创建日期：** 2026-05-27
> **状态：** 待实现
> **作者：** Claude Code + 用户

---

## 1. 概述

### 1.1 目标

为客服助手系统增加**垂直领域房源推荐**能力：当客户咨询涉及房源时，系统自动从本地房源库中匹配相关房源，生成包含房源链接的推荐话术，方便客服直接复制粘贴回复给客户。

### 1.2 核心场景

1. 客户问："有没有三亚带泳池的海景房？"
2. AI 判断为房源咨询 → 提取约束条件（三亚、泳池、海景）
3. 查询本地房源库 → 匹配 Top 3 房源
4. AI 生成推荐话术（含房源名称、价格、链接）
5. 悬浮窗展示：AI 客服回复 + 推荐房源卡片
6. 客服点击"复制链接"→ 粘贴到聊天窗口回复客户

### 1.3 设计原则

- **AI 语义匹配**：用 AI 理解客户意图，精准提取约束条件
- **本地优先**：房源数据存储在本地 Room 数据库，响应快
- **云端同步**：房源数据支持云端同步，多设备共享
- **无缝集成**：推荐结果直接嵌入现有悬浮窗 UI，不改变用户操作流程

---

## 2. 架构设计

### 2.1 整体流程

```
客户消息 → 悬浮窗触发
    ↓
ReplyOrchestrator（现有）
    ↓
ReplyGenerator.generateReply()（现有）
    ↓
AI 生成客服回复
    ↓
PropertyRecommender.recommend()（新增）
    ↓
AI 判断是否涉及房源咨询
    ↓ 是
AI 提取约束条件（位置/价格/设施/人数）
    ↓
结构化查询本地房源数据库
    ↓
AI 生成推荐话术（含房源链接）
    ↓
悬浮窗展示：AI 回复 + 推荐房源卡片
```

### 2.2 新增组件

| 组件 | 职责 | 依赖 |
|------|------|------|
| `Property` 数据实体 | 房源数据模型 | Room |
| `PropertyRepository` | 房源数据访问 | Room DAO |
| `PropertyRecommender` | AI 语义匹配推荐引擎 | AIClient, PropertyRepository |
| `PropertyImportManager` | 批量导入（JSON/CSV/Excel） | PropertyRepository |
| `PropertySyncManager` | 云端同步 | SyncApiService |
| `PropertyScreen` | 房源管理页面（Compose） | PropertyViewModel |
| `PropertyViewModel` | 房源管理状态管理 | PropertyRepository |
| 悬浮窗 UI 扩展 | 推荐房源卡片展示 | FloatingWindowService |

### 2.3 数据流

```
┌─────────────────────────────────────────────────────────┐
│                      悬浮窗 UI                           │
│  ┌─────────────────────────────────────────────────┐    │
│  │  AI 客服回复                                      │    │
│  ├─────────────────────────────────────────────────┤    │
│  │  推荐房源卡片（Top 3）                            │    │
│  │  [复制链接] [查看更多房源 →]                       │    │
│  ├─────────────────────────────────────────────────┤    │
│  │  [复制] [发送] [黑名单]                          │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
                          ↑
                          │ PropertyRecommendation
                          │
┌─────────────────────────────────────────────────────────┐
│              PropertyRecommender（推荐引擎）              │
│  1. AI 判断是否房源咨询                                   │
│  2. AI 提取约束条件                                       │
│  3. PropertyRepository.search() 查询本地                 │
│  4. AI 生成推荐话术                                       │
└─────────────────────────────────────────────────────────┘
                          ↑
                          │ 约束条件
                          │
┌─────────────────────────────────────────────────────────┐
│              PropertyRepository（数据层）                 │
│  - search(location, maxPrice, amenities, guests)        │
│  - insert / update / delete                             │
│  - importFromJson / importFromCsv                       │
└─────────────────────────────────────────────────────────┘
                          ↑
                          │
┌─────────────────────────────────────────────────────────┐
│              KefuDatabase（Room 数据库）                  │
│  - properties 表                                        │
│  - 索引: location, amenities, status, tenantId          │
└─────────────────────────────────────────────────────────┘
```

---

## 3. 数据模型

### 3.1 Property 实体

```kotlin
@Entity(
    tableName = "properties",
    indices = [
        Index(value = ["tenantId"]),
        Index(value = ["status"]),
        Index(value = ["location"]),
        Index(value = ["remoteId"])
    ]
)
data class PropertyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: String? = null,        // 云端房源ID（API同步用）
    val name: String,                    // 房源名称
    val price: Int,                      // 每晚价格（元）
    val location: String,                // 省·市·区域
    val roomType: String,                // 几室几厅
    val maxGuests: Int,                  // 可住人数
    val amenities: String,               // 设施标签（逗号分隔）
    val description: String,             // 房源简介
    val link: String,                    // 可复制的房源URL
    val imageUrl: String?,               // 封面图URL
    val status: String,                  // AVAILABLE / FULL
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val tenantId: String = "",
    val syncVersion: Long = 0L,          // 同步版本号
    val deleted: Boolean = false,        // 软删除标记
    val syncStatus: String = "SYNCED"    // SYNCED / PENDING / CONFLICT
)
```

### 3.2 领域模型

```kotlin
data class Property(
    val id: Long = 0,
    val remoteId: String? = null,
    val name: String,
    val price: Int,
    val location: String,
    val roomType: String,
    val maxGuests: Int,
    val amenities: List<String>,        // 解析后的设施列表
    val description: String,
    val link: String,
    val imageUrl: String?,
    val status: PropertyStatus,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val tenantId: String = "",
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

enum class PropertyStatus { AVAILABLE, FULL }
enum class SyncStatus { SYNCED, PENDING, CONFLICT }
```

### 3.3 推荐结果模型

```kotlin
data class PropertyRecommendation(
    val shouldRecommend: Boolean,          // 是否需要推荐
    val reasoning: String,                 // AI判断理由（调试用）
    val suggestedReply: String,            // 推荐话术（可插入AI回复末尾）
    val properties: List<Property>,        // 推荐房源列表（Top 3）
    val constraints: SearchConstraints     // 提取的约束条件（调试用）
)

data class SearchConstraints(
    val location: String?,
    val maxPrice: Int?,
    val amenities: List<String>,
    val guests: Int?
)
```

### 3.4 数据库迁移

```kotlin
// MIGRATION_5_6
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS properties (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "remoteId TEXT, " +
                "name TEXT NOT NULL, " +
                "price INTEGER NOT NULL, " +
                "location TEXT NOT NULL, " +
                "roomType TEXT NOT NULL, " +
                "maxGuests INTEGER NOT NULL, " +
                "amenities TEXT NOT NULL, " +
                "description TEXT NOT NULL, " +
                "link TEXT NOT NULL, " +
                "imageUrl TEXT, " +
                "status TEXT NOT NULL DEFAULT 'AVAILABLE', " +
                "createdAt INTEGER NOT NULL, " +
                "updatedAt INTEGER NOT NULL, " +
                "tenantId TEXT NOT NULL DEFAULT '', " +
                "syncVersion INTEGER NOT NULL DEFAULT 0, " +
                "deleted INTEGER NOT NULL DEFAULT 0, " +
                "syncStatus TEXT NOT NULL DEFAULT 'SYNCED'" +
            ")"
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_properties_tenantId ON properties(tenantId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_properties_status ON properties(status)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_properties_location ON properties(location)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_properties_remoteId ON properties(remoteId)")
    }
}
```

**数据库版本：** 5 → 6

---

## 4. 推荐引擎设计

### 4.1 PropertyRecommender

```kotlin
@Singleton
class PropertyRecommender @Inject constructor(
    private val aiClient: AIClient,
    private val propertyRepository: PropertyRepository
) {
    /**
     * 主推荐方法
     * @param customerMessage 客户原始消息
     * @param aiReply AI 生成的客服回复
     * @param userId 当前用户ID
     * @return 推荐结果（shouldRecommend=false 表示不需要推荐）
     */
    suspend fun recommend(
        customerMessage: String,
        aiReply: String,
        userId: String
    ): PropertyRecommendation

    /**
     * AI 判断是否涉及房源咨询 + 提取约束条件（合并为一次 API 调用）
     */
    private suspend fun analyzeIntent(
        customerMessage: String
    ): Pair<Boolean, SearchConstraints>

    /**
     * 结构化查询本地房源
     */
    private suspend fun searchProperties(
        constraints: SearchConstraints,
        tenantId: String
    ): List<Property>

    /**
     * AI 生成推荐话术
     */
    private suspend fun generateRecommendationReply(
        properties: List<Property>,
        constraints: SearchConstraints
    ): String
}
```

### 4.2 AI Prompt 设计

**意图分析 Prompt：**
```
你是一位民宿客服助手。判断以下客户消息是否涉及房源咨询、房源推荐或房源预订。

客户消息："{customerMessage}"

请分析：
1. 客户是否在询问房源信息？（YES/NO）
2. 提取客户的房源需求约束条件（JSON格式）：
   - location: 期望位置（如"三亚"、"丽江"），无则返回null
   - maxPrice: 最高预算（元/晚），无则返回null
   - amenities: 期望设施列表（如["泳池","海景","WiFi"]），无则返回空数组
   - guests: 入住人数，无则返回null

只返回JSON，格式如下：
{"isPropertyQuery": true/false, "constraints": {...}}
```

**推荐话术 Prompt：**
```
你是一位民宿客服助手。根据以下房源数据，生成简洁、专业的中文推荐话术。

客户需求：{constraints}
推荐房源：
{properties_json}

要求：
1. 使用中文，语气专业友好
2. 每个房源包含：名称、价格、位置、链接
3. 不超过150字
4. 格式示例：
   为您推荐以下房源：
   1. 【海景别墅】¥880/晚 | 三亚·海棠湾 | 🔗 https://example.com/123
   2. 【温馨民宿】¥260/晚 | 丽江·束河 | 🔗 https://example.com/456

只输出推荐话术，不要额外解释。
```

### 4.3 本地查询逻辑

```kotlin
// PropertyRepository.search()
suspend fun search(
    location: String? = null,
    maxPrice: Int? = null,
    amenities: List<String> = emptyList(),
    guests: Int? = null,
    tenantId: String,
    limit: Int = 10
): List<Property> {
    // 构建动态 SQL 查询
    // location: LIKE '%{location}%'
    // maxPrice: price <= maxPrice
    // maxGuests: maxGuests >= guests
    // amenities: 每个设施用 LIKE '%{amenity}%' 匹配
    // status: = 'AVAILABLE'
    // deleted: = 0
}
```

---

## 5. 悬浮窗 UI 设计

### 5.1 布局修改

在现有 `FloatingWindowService` 中扩展，增加推荐房源卡片区域：

```
┌─────────────────────────────────┐
│  客户消息预览（现有）             │
├─────────────────────────────────┤
│  AI 建议回复（可滚动，现有）       │
├─────────────────────────────────┤  ← 新增分隔线
│  🏠 推荐房源                     │  ← 新增标题
│  ┌───────────────────────────┐  │
│  │ 1. 海景别墅 ¥880/晚       │  │
│  │    三亚·海棠湾 | 3室2厅    │  │
│  │    [🔗 复制链接]           │  │
│  ├───────────────────────────┤  │
│  │ 2. 温馨民宿 ¥260/晚       │  │
│  │    丽江·束河 | 1室1厅      │  │
│  │    [🔗 复制链接]           │  │
│  ├───────────────────────────┤  │
│  │ 3. 山景木屋 ¥450/晚       │  │
│  │    大理·苍山 | 2室1厅      │  │
│  │    [🔗 复制链接]           │  │
│  └───────────────────────────┘  │
│  [查看更多房源 →]                │
├─────────────────────────────────┤
│  [复制] [发送] [黑名单]（现有）   │
└─────────────────────────────────┘
```

### 5.2 交互行为

| 操作 | 行为 |
|------|------|
| 点击"复制链接" | 将房源 URL 复制到系统剪贴板，Toast 提示"链接已复制" |
| 点击"查看更多房源" | 启动 PropertyScreen Activity |
| 非房源咨询 | 推荐区域隐藏，布局不变 |
| 推荐区域高度 | 最大 dp(160)，超出可滚动 |

### 5.3 集成方式

在 `ReplyGenerator.generateReply()` 返回 `ReplyResult` 后，调用 `PropertyRecommender.recommend()`，将推荐结果附加到 `ReplyResult` 中：

```kotlin
// ReplyResult 扩展
data class ReplyResult(
    val reply: String,
    val source: ReplySource,
    val confidence: Float,
    val ruleId: Long? = null,
    val modelId: Long? = null,
    val propertyRecommendation: PropertyRecommendation? = null  // 新增
)
```

---

## 6. 房源管理页面

### 6.1 PropertyScreen 功能

| 功能 | 说明 |
|------|------|
| 房源列表 | 卡片式展示：封面图、名称、价格、位置、状态标签 |
| 搜索 | 按名称、位置搜索 |
| 筛选 | 按价格范围、设施标签、状态筛选 |
| 新增/编辑 | 表单：名称、价格、位置、房型、人数、设施、描述、链接、图片URL、状态 |
| 删除 | 软删除（标记 deleted=1） |
| 批量导入 | JSON / CSV / Excel 文件导入 |
| 批量导出 | 导出为 JSON / CSV |
| 同步 | 手动触发云端同步，显示同步状态 |

### 6.2 导入功能

**支持格式：**
- JSON：`[{"name": "...", "price": 880, ...}, ...]`
- CSV：`name,price,location,roomType,maxGuests,amenities,description,link,imageUrl,status`
- Excel：同上列映射

**字段映射配置：**
- 支持自定义字段映射（外部字段名 → 内部字段名）
- 预设映射模板（美团、途家、Airbnb 等）

**导入流程：**
1. 选择文件 → 解析数据
2. 显示预览（前 5 条）
3. 配置字段映射（如有需要）
4. 确认导入 → 批量写入数据库
5. 重复检测：按名称 + 位置去重（跳过或覆盖）

---

## 7. 云端同步设计

### 7.1 同步策略

- 复用现有 `SyncManager` + `SyncApiService` 基础设施
- 房源作为新的同步资源类型加入同步队列
- 支持：手动同步 / 自动同步（定时）

### 7.2 同步字段映射

| 本地字段 | 云端字段 | 说明 |
|----------|----------|------|
| remoteId | id | 云端唯一标识 |
| name | name | 房源名称 |
| price | price | 价格 |
| location | location | 位置 |
| roomType | room_type | 房型 |
| maxGuests | max_guests | 可住人数 |
| amenities | amenities | 设施（逗号分隔） |
| description | description | 描述 |
| link | link | 链接 |
| imageUrl | image_url | 图片URL |
| status | status | 状态（AVAILABLE/FULL，与本地格式一致） |
| syncVersion | sync_version | 同步版本 |
| deleted | deleted | 软删除 |

### 7.3 冲突解决

- 默认策略：云端优先（云端更新覆盖本地）
- 冲突标记：`syncStatus = CONFLICT`，用户手动选择保留版本

---

## 8. 文件清单

### 8.1 新增文件

| 文件 | 说明 |
|------|------|
| `data/local/entity/PropertyEntity.kt` | Room 实体 |
| `data/local/dao/PropertyDao.kt` | Room DAO |
| `data/repository/PropertyRepositoryImpl.kt` | 仓库实现 |
| `domain/model/Property.kt` | 领域模型 |
| `domain/model/PropertyRecommendation.kt` | 推荐结果模型 |
| `domain/repository/PropertyRepository.kt` | 仓库接口 |
| `infrastructure/property/PropertyRecommender.kt` | 推荐引擎 |
| `infrastructure/property/PropertyImportManager.kt` | 批量导入 |
| `infrastructure/property/PropertySyncManager.kt` | 云端同步 |
| `presentation/screens/property/PropertyScreen.kt` | 房源管理页面 |
| `presentation/screens/property/PropertyViewModel.kt` | 房源管理 ViewModel |

### 8.2 修改文件

| 文件 | 修改内容 |
|------|----------|
| `data/local/KefuDatabase.kt` | 版本 5→6，添加 PropertyEntity |
| `di/DatabaseModule.kt` | 添加 PropertyDao 依赖注入 |
| `di/RepositoryModule.kt` | 添加 PropertyRepository 依赖注入 |
| `infrastructure/reply/ReplyGenerator.kt` | 集成 PropertyRecommender |
| `infrastructure/window/FloatingWindowService.kt` | 添加推荐房源卡片 UI |
| `domain/model/Models.kt` | ReplyResult 增加 propertyRecommendation 字段 |

---

## 9. API 设计

### 9.1 PropertyRepository 接口

```kotlin
interface PropertyRepository {
    fun getAllProperties(): Flow<List<Property>>
    fun getEnabledProperties(): Flow<List<Property>>
    suspend fun getPropertyById(id: Long): Property?
    suspend fun insertProperty(property: Property): Long
    suspend fun updateProperty(property: Property)
    suspend fun deleteProperty(id: Long): Result<Unit>
    suspend fun search(
        location: String? = null,
        maxPrice: Int? = null,
        amenities: List<String> = emptyList(),
        guests: Int? = null,
        tenantId: String,
        limit: Int = 10
    ): List<Property>
    suspend fun importFromJson(json: String, tenantId: String): ImportResult
    suspend fun importFromCsv(csv: String, tenantId: String): ImportResult
    suspend fun exportToJson(tenantId: String): String
    suspend fun getPropertiesPendingSync(tenantId: String): List<Property>
    suspend fun updateSyncStatus(id: Long, status: SyncStatus, syncVersion: Long)
}
```

### 9.2 导入结果

```kotlin
data class ImportResult(
    val totalCount: Int,
    val successCount: Int,
    val skipCount: Int,      // 重复跳过
    val failedCount: Int,
    val errors: List<String> // 错误详情（前10条）
)
```

---

## 10. 测试策略

### 10.1 单元测试

| 测试项 | 说明 |
|--------|------|
| PropertyEntity 映射 | Entity ↔ 领域模型转换 |
| PropertyRepository.search() | 各种约束条件的查询逻辑 |
| PropertyImportManager | JSON/CSV 导入解析、去重逻辑 |
| PropertyRecommender | AI 意图分析、推荐话术生成（Mock AI） |

### 10.2 集成测试

| 测试项 | 说明 |
|--------|------|
| 数据库迁移 5→6 | 升级后数据完整性 |
| 导入 → 查询 → 推荐 | 端到端推荐流程 |
| 云端同步 | 房源数据上传/下载 |

### 10.3 E2E 测试

| 测试项 | 说明 |
|--------|------|
| 悬浮窗推荐卡片 | 客户消息 → 悬浮窗 → 推荐房源展示 |
| 复制链接功能 | 点击"复制链接"→ 剪贴板内容验证 |
| 房源管理页面 | 增删改查、搜索筛选、导入导出 |

---

## 11. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| AI 意图判断错误 | 推荐不相关房源 | 置信度阈值过滤，低于阈值不推荐 |
| AI 调用延迟 | 悬浮窗响应慢 | 推荐结果异步加载，先展示 AI 回复 |
| 房源数据量大 | 查询性能下降 | 索引优化，查询结果限制 Top 10 |
| 云端同步冲突 | 数据不一致 | 冲突标记 + 用户手动解决 |
| 导入数据格式错误 | 导入失败 | 预览 + 字段映射 + 错误提示 |

---

## 12. 里程碑

### Phase 1：核心功能（推荐引擎 + 悬浮窗 UI）
- Property 数据模型 + Room 表
- PropertyRepository + PropertyDao
- PropertyRecommender（AI 语义匹配）
- 悬浮窗推荐卡片 UI
- ReplyGenerator 集成

### Phase 2：房源管理
- PropertyScreen + PropertyViewModel
- 搜索/筛选功能
- 新增/编辑表单

### Phase 3：导入导出 + 云端同步
- JSON/CSV/Excel 批量导入
- 云端同步集成
- 同步状态管理

---

## 13. 后续扩展

- **房源图片展示**：悬浮窗卡片显示封面图（需图片加载库）
- **推荐反馈**：记录客服是否采纳推荐，用于优化 AI 推荐质量
- **多平台同步**：支持美团、途家、Airbnb 等多平台房源同步
- **智能定价**：根据历史入住数据推荐最优价格
- **客户画像**：记录客户偏好，个性化推荐
