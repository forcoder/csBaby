# 客服小秘 (csBaby) - 技术规格说明书

> 基于 Spec-Driven Design (SDD) 方法论
> 版本: 1.5.0
> 更新日期: 2026-05-24

---

## 1. 系统概述

### 1.1 项目背景

客服小秘是一款 Android 智能客服回复辅助工具，通过监控用户指定的即时通讯应用消息，结合 AI 模型和知识库规则，自动生成回复建议，提升客服工作效率。

### 1.2 核心功能

| 功能模块 | 描述 | 优先级 |
|---------|------|--------|
| 消息监控 | 监控指定应用的新消息 | P0 |
| 知识库管理 | 关键词规则库管理 | P0 |
| AI 回复生成 | 调用 AI 模型生成回复 | P0 |
| 悬浮窗显示 | 气泡/面板式回复建议展示 | P0 |
| 云同步 | 多设备数据同步与备份 | P1 |
| OTA 更新 | App 热更新能力 | P1 |

### 1.3 技术栈

| 层级 | 技术选型 |
|------|---------|
| 移动端 | Android / Kotlin / Jetpack Compose |
| 架构 | MVVM + Clean Architecture + Hilt DI |
| 本地存储 | Room Database |
| 网络 | Retrofit + OkHttp + Gson |
| 后端 | Node.js + Express + PostgreSQL |
| 部署 | Render.com |

---

## 2. 架构设计

### 2.1 分层架构

```
┌─────────────────────────────────────────────────────┐
│                  Presentation Layer               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐ │
│  │   Screens   │  │  ViewModels │  │    DTOs     │ │
│  └─────────────┘  └─────────────┘  └─────────────┘ │
├─────────────────────────────────────────────────────┤
│                    Domain Layer                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐ │
│  │   Models    │  │    UseCases │  │ Repositories │ │
│  └─────────────┘  └─────────────┘  └─────────────┘ │
├─────────────────────────────────────────────────────┤
│                     Data Layer                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐ │
│  │    Remote   │  │    Local    │  │    Sync     │ │
│  └─────────────┘  └─────────────┘  └─────────────┘ │
├─────────────────────────────────────────────────────┤
│                 Infrastructure Layer                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐ │
│  │   Window    │  │  Accessibility│  │  Backup    │ │
│  └─────────────┘  └─────────────┘  └─────────────┘ │
└─────────────────────────────────────────────────────┘
```

### 2.2 目录结构

```
app/src/main/java/com/csbaby/kefu/
├── presentation/           # 展示层
│   ├── screens/           # 页面
│   │   ├── home/          # 首页
│   │   ├── knowledge/      # 知识库
│   │   ├── models/        # 模型配置
│   │   └── profile/       # 我的
│   ├── navigation/        # 导航
│   ├── components/        # 通用组件
│   └── MainActivity.kt
├── domain/                # 领域层
│   └── model/             # 领域模型
├── data/                  # 数据层
│   ├── local/             # 本地存储
│   │   ├── dao/           # Room DAOs
│   │   ├── entity/        # 实体
│   │   └── PreferencesManager.kt
│   ├── remote/            # 远程 API
│   │   ├── SyncApiService.kt
│   │   ├── AIClient.kt
│   │   └── ApiResponse.kt
│   ├── model/             # 数据模型
│   └── sync/              # 同步逻辑
│       ├── SyncManager.kt
│       ├── AuthManager.kt
│       └── AuthenticatedSyncClient.kt
├── di/                    # 依赖注入
│   ├── AppModule.kt
│   ├── DatabaseModule.kt
│   ├── NetworkModule.kt
│   └── OtaAndOssModule.kt
├── infrastructure/        # 基础设施
│   ├── window/            # 悬浮窗
│   │   ├── FloatingWindowService.kt
│   │   └── FloatingView.kt
│   ├── accessibility/      # 无障碍服务
│   ├── monitoring/        # 消息监控
│   ├── backup/            # 备份管理
│   └── ota/               # OTA 更新
└── util/                  # 工具类
```

### 2.3 核心数据流

```
[消息监控服务]
    ↓ (NotificationListener)
[消息解析]
    ↓
[规则匹配] → [AI 生成] → [回复建议]
    ↓               ↓
[本地存储] ← → [云端同步]
    ↓
[悬浮窗显示]
```

---

## 3. 数据模型规格

### 3.1 核心实体

#### 3.1.1 KeywordRule (关键词规则)

| 字段 | 类型 | 描述 | 示例 |
|------|------|------|------|
| id | Long | 主键 | 1001 |
| keyword | String | 关键词 | "您好" |
| matchType | Enum | 匹配类型 | CONTAINS, EXACT, REGEX |
| replyTemplate | String | 回复模板 | "感谢咨询！" |
| category | String | 分类 | "问候" |
| targetType | Enum | 目标类型 | ALL, WHATSAPP, WECHAT |
| targetNamesJson | String | 目标名称列表 | `["微信","企业微信"]` |
| priority | Int | 优先级 | 100 |
| enabled | Boolean | 是否启用 | true |
| createdAt | Long | 创建时间戳 | 1779499123000 |
| updatedAt | Long | 更新时间戳 | 1779499123000 |
| tenantId | String | 租户 ID | UUID |
| syncVersion | Long | 同步版本号 | 1 |
| deleted | Boolean | 软删除标记 | false |

#### 3.1.2 AIModelConfig (AI 模型配置)

| 字段 | 类型 | 描述 | 示例 |
|------|------|------|------|
| id | Long | 主键 | 1 |
| modelType | String | 模型类型 | "openai", "anthropic" |
| modelName | String | 模型名称 | "gpt-4o-mini" |
| apiKey | String | API 密钥 | "sk-..." (加密存储) |
| apiEndpoint | String | API 地址 | "https://api.openai.com/v1" |
| temperature | Float | 温度参数 | 0.7 |
| maxTokens | Int | 最大 Token 数 | 1000 |
| isDefault | Boolean | 是否默认 | true |
| isEnabled | Boolean | 是否启用 | true |
| monthlyCost | Double | 月度费用 | 0.0 |

#### 3.1.3 UserStyleProfile (用户风格画像)

| 字段 | 类型 | 描述 | 示例 |
|------|------|------|------|
| userId | String | 用户 ID | UUID |
| formalityLevel | Float | 正式度 (0-1) | 0.6 |
| enthusiasmLevel | Float | 热情度 (0-1) | 0.5 |
| professionalismLevel | Float | 专业度 (0-1) | 0.7 |
| wordCountPreference | Int | 字数偏好 | 50 |
| commonPhrases | String | 常用短语 | "好的,收到" |
| avoidPhrases | String | 避免短语 | "呵呵" |
| learningSamples | Int | 学习样本数 | 20 |
| accuracyScore | Float | 准确度评分 | 0.85 |

### 3.2 同步数据模型

```kotlin
data class SyncKeywordRule(
    val id: Long,
    val keyword: String,
    val matchType: String,
    val replyTemplate: String,
    val category: String,
    val targetType: String,
    val targetNamesJson: String,
    val priority: Int,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val tenantId: String,
    val syncVersion: Long,
    val deleted: Boolean
)
```

---

## 4. API 接口规格

### 4.1 后端服务

**基础 URL**: `https://csbaby-sync-server.onrender.com`

### 4.2 认证接口

#### 4.2.1 用户注册

```
POST /auth/register
Content-Type: application/json

Request:
{
  "email": "user@example.com",
  "password": "password123",
  "displayName": "用户昵称"
}

Response:
{
  "code": 0,
  "message": "注册成功",
  "data": {
    "userId": "uuid",
    "tenantId": "uuid",
    "accessToken": "jwt-token",
    "refreshToken": "jwt-token",
    "expiresAt": 1779503040806
  }
}
```

#### 4.2.2 用户登录

```
POST /auth/login
Content-Type: application/json

Request:
{
  "email": "user@example.com",
  "password": "password123"
}

Response:
{
  "code": 0,
  "message": "登录成功",
  "data": {
    "userId": "uuid",
    "tenantId": "uuid",
    "accessToken": "jwt-token",
    "refreshToken": "jwt-token",
    "expiresAt": 1779503040806
  }
}
```

### 4.3 同步接口

#### 4.3.1 全量同步

```
GET /sync/all?tenantId={tenantId}
Authorization: Bearer {accessToken}

Response:
{
  "code": 0,
  "message": "成功",
  "data": {
    "keywordRules": [...],
    "aiModelConfigs": [...],
    "userStyleProfile": {...},
    "appConfigs": [...],
    "scenarios": [...],
    "replyHistory": [...],
    "messageBlacklist": [...],
    "serverTime": 1779499458449
  }
}
```

#### 4.3.2 增量同步

```
GET /sync/changes?tenantId={tenantId}&since={timestamp}
Authorization: Bearer {accessToken}

Response:
{
  "code": 0,
  "message": "成功",
  "data": {
    "keywordRules": [...],
    "aiModelConfigs": [...],
    "userStyleProfile": {...},
    "appConfigs": [...],
    "scenarios": [...],
    "replyHistory": [...],
    "messageBlacklist": [...],
    "deletedIds": {"keyword_rules": ["123"]},
    "serverTime": 1779499458449,
    "hasMore": false,
    "nextCursor": null
  }
}
```

#### 4.3.3 推送变更

```
POST /sync/push
Authorization: Bearer {accessToken}
Content-Type: application/json

Request:
{
  "tenantId": "uuid",
  "keywordRules": [...],
  "aiModelConfigs": [...],
  "userStyleProfile": {...},
  "appConfigs": [...],
  "scenarios": [...],
  "replyHistory": [...],
  "messageBlacklist": [...],
  "deletedIds": {"keyword_rules": ["123"]},
  "baseVersion": 1
}

Response:
{
  "code": 0,
  "message": "成功",
  "data": {
    "accepted": true,
    "conflicts": [],
    "newServerVersion": 2,
    "serverTime": 1779499458449
  }
}
```

### 4.4 备份接口

#### 4.4.1 上传备份

```
POST /api/v1/backup/upload
Authorization: Bearer {accessToken}
Content-Type: application/json

Request:
{
  "device_name": "MI 12",
  "app_version": "1.4.0",
  "data": {
    "keywordRules": [...],
    "aiModelConfigs": [...],
    ...
  },
  "data_size": 102400,
  "checksum": "sha256:..."
}
```

#### 4.4.2 备份列表

```
GET /api/v1/backup/list
Authorization: Bearer {accessToken}

Response:
{
  "code": 0,
  "message": "成功",
  "data": [
    {
      "id": 1,
      "device_name": "MI 12",
      "app_version": "1.4.0",
      "created_at": 1779499123000
    }
  ]
}
```

### 4.5 OTA 接口

#### 4.5.1 版本检查

```
GET /api/v1/ota/check
Content-Type: application/json

Request:
{
  "current_version": 10
}

Response:
{
  "code": 0,
  "message": "成功",
  "data": {
    "has_update": true,
    "version_code": 11,
    "version_name": "1.4.0",
    "download_url": "https://...",
    "is_force_update": false,
    "release_notes": "修复了..."
  }
}
```

---

## 5. 核心功能规格

### 5.1 消息监控服务

#### 5.1.1 NotificationListenerService

- 监听系统通知 (NotificationListenerService)
- 过滤指定应用的通知
- 解析通知内容，提取消息文本
- 触发规则匹配流程

#### 5.1.2 权限要求

```xml
<!-- 通知监听权限 -->
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" />

<!-- 悬浮窗权限 -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- 后台服务权限 -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
```

### 5.2 悬浮窗服务

#### 5.2.1 FloatingWindowService

- 使用 TYPE_APPLICATION_OVERLAY 显示悬浮窗
- 提供气泡模式和面板模式
- 支持拖拽定位
- 自动隐藏和显示

#### 5.2.2 权限申请流程

```
1. 检查 SYSTEM_ALERT_WINDOW 权限
2. 如果没有 → 跳转设置页面申请
3. 用户返回 → 验证权限是否已授予
4. 权限授予 → 启动悬浮服务
```

### 5.3 无障碍服务

#### 5.3.1 AutoSendAccessibilityService

- 监控前台应用变化
- 检测聊天输入框
- 自动填入回复文本
- 模拟点击发送按钮

#### 5.3.2 支持的应用

| 应用 | 包名 | 输入框标识 | 发送按钮标识 |
|------|------|-----------|-------------|
| 微信 | com.tencent.mm | 略 |
| 企业微信 | com.tencent.wework |
| 百居易 | com.baijuke |
| 美团民宿 | com.meituan.destination |
| 途家民宿 | com.tujia.daily |

### 5.4 云同步机制

#### 5.4.1 同步策略

```
首次同步: 全量拉取 (getAllData)
日常同步: 增量拉取 (getChanges)
变更推送: 增量推送 (pushChanges)
冲突解决: 服务端优先 (SERVER_WINS)
```

#### 5.4.2 同步时机

- 用户登录成功后自动触发
- 应用启动时检查同步点
- 手动触发同步按钮
- 退出应用前自动保存

#### 5.4.3 离线支持

- 本地优先读取
- 变更队列暂存
- 网络恢复后自动同步

---

## 6. 数据库规格

### 6.1 PostgreSQL 表结构

```sql
-- 用户表
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  display_name TEXT NOT NULL,
  tenant_id UUID NOT NULL,
  created_at BIGINT NOT NULL
);

-- 刷新令牌表
CREATE TABLE refresh_tokens (
  token TEXT PRIMARY KEY,
  user_id UUID NOT NULL,
  tenant_id UUID NOT NULL,
  expires_at BIGINT NOT NULL,
  created_at BIGINT NOT NULL
);

-- 关键词规则表
CREATE TABLE keyword_rules (
  id SERIAL PRIMARY KEY,
  keyword TEXT NOT NULL,
  match_type TEXT NOT NULL DEFAULT 'CONTAINS',
  reply_template TEXT NOT NULL,
  category TEXT NOT NULL DEFAULT '',
  target_type TEXT NOT NULL DEFAULT 'ALL',
  target_names_json TEXT NOT NULL DEFAULT '[]',
  priority INTEGER NOT NULL DEFAULT 0,
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  tenant_id UUID NOT NULL,
  sync_version BIGINT NOT NULL DEFAULT 0,
  deleted INTEGER NOT NULL DEFAULT 0
);

-- AI 模型配置表
CREATE TABLE ai_model_configs (
  id SERIAL PRIMARY KEY,
  model_type TEXT NOT NULL,
  model_name TEXT NOT NULL,
  api_key TEXT NOT NULL DEFAULT '',
  api_endpoint TEXT NOT NULL DEFAULT '',
  temperature REAL NOT NULL DEFAULT 0.7,
  max_tokens INTEGER NOT NULL DEFAULT 1000,
  is_default INTEGER NOT NULL DEFAULT 0,
  is_enabled INTEGER NOT NULL DEFAULT 1,
  monthly_cost REAL NOT NULL DEFAULT 0,
  last_used BIGINT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL,
  tenant_id UUID NOT NULL,
  sync_version BIGINT NOT NULL DEFAULT 0,
  deleted INTEGER NOT NULL DEFAULT 0
);

-- 用户风格画像表
CREATE TABLE user_style_profiles (
  user_id UUID PRIMARY KEY,
  formality_level REAL NOT NULL DEFAULT 0.5,
  enthusiasm_level REAL NOT NULL DEFAULT 0.5,
  professionalism_level REAL NOT NULL DEFAULT 0.5,
  word_count_preference INTEGER NOT NULL DEFAULT 50,
  common_phrases TEXT NOT NULL DEFAULT '',
  avoid_phrases TEXT NOT NULL DEFAULT '',
  learning_samples INTEGER NOT NULL DEFAULT 0,
  accuracy_score REAL NOT NULL DEFAULT 0,
  last_trained BIGINT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL,
  tenant_id UUID NOT NULL,
  sync_version BIGINT NOT NULL DEFAULT 0,
  deleted INTEGER NOT NULL DEFAULT 0
);

-- 应用配置表
CREATE TABLE app_configs (
  package_name TEXT PRIMARY KEY,
  app_name TEXT NOT NULL,
  icon_uri TEXT,
  is_monitored INTEGER NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL,
  last_used BIGINT NOT NULL DEFAULT 0,
  tenant_id UUID NOT NULL,
  sync_version BIGINT NOT NULL DEFAULT 0,
  deleted INTEGER NOT NULL DEFAULT 0
);

-- 场景表
CREATE TABLE scenarios (
  id SERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  type TEXT NOT NULL DEFAULT 'ALL_PROPERTIES',
  target_id TEXT,
  description TEXT,
  created_at BIGINT NOT NULL,
  tenant_id UUID NOT NULL,
  sync_version BIGINT NOT NULL DEFAULT 0,
  deleted INTEGER NOT NULL DEFAULT 0
);

-- 回复历史表
CREATE TABLE reply_history (
  id SERIAL PRIMARY KEY,
  source_app TEXT NOT NULL,
  original_message TEXT NOT NULL,
  generated_reply TEXT NOT NULL,
  final_reply TEXT NOT NULL,
  rule_matched_id INTEGER,
  model_used_id INTEGER,
  style_applied INTEGER NOT NULL DEFAULT 0,
  send_time BIGINT NOT NULL,
  modified INTEGER NOT NULL DEFAULT 0,
  tenant_id UUID NOT NULL,
  sync_version BIGINT NOT NULL DEFAULT 0,
  deleted INTEGER NOT NULL DEFAULT 0
);

-- 同步检查点表
CREATE TABLE sync_checkpoints (
  tenant_id UUID PRIMARY KEY,
  last_sync_time BIGINT NOT NULL DEFAULT 0,
  sync_token TEXT,
  is_syncing INTEGER NOT NULL DEFAULT 0,
  last_error TEXT
);

-- 消息黑名单表
CREATE TABLE message_blacklist (
  id SERIAL PRIMARY KEY,
  type TEXT NOT NULL,
  value TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  package_name TEXT,
  created_at BIGINT NOT NULL,
  is_enabled INTEGER NOT NULL DEFAULT 1,
  tenant_id UUID NOT NULL,
  sync_version BIGINT NOT NULL DEFAULT 0,
  deleted INTEGER NOT NULL DEFAULT 0
);

-- OTA 版本管理表
CREATE TABLE ota_versions (
  version_code INTEGER PRIMARY KEY,
  version_name TEXT NOT NULL,
  download_url TEXT NOT NULL,
  file_size INTEGER NOT NULL DEFAULT 0,
  md5 TEXT NOT NULL DEFAULT '',
  release_notes TEXT NOT NULL DEFAULT '',
  channel TEXT NOT NULL DEFAULT 'default',
  is_force_update INTEGER NOT NULL DEFAULT 0,
  min_required_version INTEGER NOT NULL DEFAULT 1,
  is_published INTEGER NOT NULL DEFAULT 1,
  release_date BIGINT,
  created_at BIGINT NOT NULL
);

-- 数据备份记录表
CREATE TABLE backup_records (
  id SERIAL PRIMARY KEY,
  tenant_id UUID NOT NULL,
  device_name TEXT NOT NULL DEFAULT '',
  app_version TEXT NOT NULL DEFAULT '',
  data_json TEXT NOT NULL,
  data_size INTEGER NOT NULL DEFAULT 0,
  checksum TEXT NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL
);

-- 索引
CREATE INDEX idx_kr_tenant ON keyword_rules(tenant_id);
CREATE INDEX idx_am_tenant ON ai_model_configs(tenant_id);
CREATE INDEX idx_rh_tenant ON reply_history(tenant_id);
CREATE INDEX idx_mb_tenant ON message_blacklist(tenant_id);
CREATE INDEX idx_backup_tenant ON backup_records(tenant_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
```

---

## 7. 用户界面规格

### 7.1 底部导航

| 页面 | 图标 | 描述 |
|------|------|------|
| 首页 | home | 监控状态、最近回复 |
| 知识库 | book | 关键词规则管理 |
| 模型 | robot | AI 模型配置 |
| 我的 | person | 个人中心 |

### 7.2 首页布局

```
┌──────────────────────────────┐
│ [状态指示器] 监控状态: 已开启  │
├──────────────────────────────┤
│ ┌──────────────────────────┐ │
│ │     监控状态卡片         │ │
│ │  - 通知权限: ✓           │ │
│ │  - 悬浮窗权限: ✓         │ │
│ │  - 无障碍服务: ✓         │ │
│ └──────────────────────────┘ │
├──────────────────────────────┤
│ 最近回复                      │
│ ┌──────────────────────────┐ │
│ │ [微信] 你好              │ │
│ │ → 您好，感谢咨询！        │ │
│ └──────────────────────────┘ │
│ ┌──────────────────────────┐ │
│ │ [微信] 多少钱            │ │
│ │ → 我们的服务价格为...     │ │
│ └──────────────────────────┘ │
├──────────────────────────────┤
│ [快捷操作]                   │
│ [同步数据] [备份] [设置]     │
└──────────────────────────────┘
```

### 7.3 悬浮窗样式

#### 气泡模式

```
   ○
  ─┼─  ← 可拖拽
   ▼
```

#### 面板模式

```
┌──────────────────┐
│ 回复建议         │
├──────────────────┤
│ 感谢您的咨询！    │
│ 我们随时为您服务  │
├──────────────────┤
│ [一键发送] [编辑] │
└──────────────────┘
```

---

## 8. 部署规格

### 8.1 CI/CD 流程

```
[Push to main]
    ↓
[GitHub Actions]
    ↓
[Install deps + Lint + Test]
    ↓
[Trigger Render Deploy]
    ↓
[Wait 180s]
    ↓
[Verify Version]
```

### 8.2 环境变量

| 变量 | 描述 | 示例 |
|------|------|------|
| DATABASE_URL | PostgreSQL 连接字符串 | postgresql://... |
| JWT_SECRET | JWT 签名密钥 | your-secret-key |
| PORT | 服务端口 | 8080 |

### 8.3 服务健康检查

```
GET /
Response: {"status":"ok","service":"csbaby-sync-server","version":"1.0.99","ts":1779499123000}
```

---

## 9. 质量标准

### 9.1 测试覆盖率

- **目标**: 单元测试覆盖率 ≥ 85%
- **要求**: 所有新功能必须有对应测试用例
- **执行**: TDD 流程 (先写测试，再实现)

### 9.2 代码规范

- Kotlin: 遵循官方代码风格 (kotlin.code.style=official)
- 禁用 `!!` 操作符，使用 `?.` 和 `?:` 安全调用
- 单文件不超过 500 行
- 单函数不超过 50 行

### 9.3 安全要求

- API 密钥加密存储
- 敏感数据不使用日志输出
- 使用 HTTPS 通信
- JWT Token 有效期控制

---

## 10. 版本历史

| 版本 | 日期 | 变更说明 |
|------|------|---------|
| 1.5.0 | 2026-05-24 | 新增云同步功能设计和测试计划（详见附录） |
| 1.4.0 | 2026-05-23 | 云同步功能完成 |
| 1.3.0 | 2026-05-20 | 悬浮窗服务 |
| 1.2.0 | 2026-05-15 | 知识库管理 |
| 1.1.0 | 2026-05-10 | AI 模型配置 |
| 1.0.0 | 2026-05-01 | 基础框架 |

---

## 附录：详细设计文档摘要

### A.1 产品提案 (proposal.md)

**文档信息**: OpenSpec Proposal v1.0，生成日期: 2026-05-24

#### A.1.1 问题陈述

| 问题 | 影响范围 | 严重程度 |
|------|---------|---------|
| 数据丢失 | 所有用户 | 高 |
| 多设备不同步 | 多设备用户 | 中 |
| 无备份 | 所有用户 | 中 |

#### A.1.2 核心价值

| 价值主张 | 描述 |
|---------|------|
| 数据安全 | 云端备份，数据永不丢失 |
| 多设备同步 | 同一账号，数据实时同步 |
| 无缝迁移 | 换手机一键恢复所有数据 |

#### A.1.3 功能规格

| 功能 | 说明 | 优先级 |
|------|------|--------|
| F1: 用户认证 | 邮箱+密码注册/登录，JWT Token 认证 | P0 |
| F2: 全量同步 | 首次登录自动拉取云端数据 | P0 |
| F3: 增量同步 | 变更即同步（2s debounce） | P0 |
| F4: 冲突处理 | 服务端/客户端优先级策略 | P1 |
| F5: 数据备份 | 全量数据 JSON 上传/下载 | P2 |

#### A.1.4 测试用例（18 个）

| 优先级 | 用例数量 | 通过率要求 |
|--------|---------|-----------|
| P0 | 4 个 | 100% |
| P1 | 8 个 | 100% |
| P2 | 6 个 | 至少 80% |

---

### A.2 技术设计 (design.md)

**文档信息**: OpenSpec Design Document v1.0，生成日期: 2026-05-24

#### A.2.1 设计目标

| 目标 | 指标 |
|------|------|
| 性能 | 全量同步 < 30s，增量同步 < 5s |
| 可用性 | 本地优先，网络恢复后自动同步 |
| 一致性 | 最终一致，冲突自动/手动解决 |
| 安全性 | Token 加密存储，租户数据隔离 |

#### A.2.2 核心组件

| 组件 | 职责 |
|------|------|
| SyncManager | 同步编排：认证、全量/增量同步、冲突处理 |
| AuthManager | Token 生命周期管理 |
| AuthenticatedSyncClient | 带认证的 HTTP 客户端 |
| SyncApiService | Retrofit API 定义 |
| SyncQueue | 离线变更队列 |
| SyncCheckpointDao | 同步检查点存储 |

#### A.2.3 冲突解决策略

| 数据类型 | 策略 | 原因 |
|---------|------|------|
| KeywordRule | SERVER_WINS | 团队共享规则服务端权威 |
| AIModelConfig | SERVER_WINS | API Key 等敏感信息以服务端为准 |
| UserStyleProfile | CLIENT_WINS | 个人风格是用户自己的数据 |
| AppConfig | MERGE | 字段级合并 |
| Scenario | SERVER_WINS | 团队共享配置服务端权威 |
| ReplyHistory | SERVER_WINS | 服务端记录更完整 |
| MessageBlacklist | SERVER_WINS | 团队共享配置服务端权威 |

#### A.2.4 测试策略

| 测试类型 | 测试类 | 测试内容 |
|---------|--------|---------|
| 单元测试 | SyncManagerTest | 全量同步、增量同步、冲突处理 |
| 单元测试 | AuthManagerTest | Token 保存、读取、过期判断 |
| 单元测试 | ConflictResolverTest | 各类型冲突解决策略 |
| 集成测试 | SyncApiServiceTest | Retrofit API 调用（使用 MockEngine） |
| 集成测试 | RoomSyncTest | Room 数据库读写（使用 inMemoryDatabase） |
| E2E 测试 | 18 个用例 | 执行 `云端同步测试用例.md` 中的完整测试套件 |

#### A.2.5 API 接口

| 端点 | 方法 | 说明 |
|------|------|------|
| /auth/register | POST | 用户注册 |
| /auth/login | POST | 用户登录 |
| /auth/refresh | POST | Token 刷新 |
| /sync/all | GET | 全量拉取 |
| /sync/changes | GET | 增量拉取 |
| /sync/push | POST | 增量推送 |
| /sync/resolve | POST | 冲突解决 |

---

*本文档基于 Spec-Driven Design (SDD) 方法论编写*