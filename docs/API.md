# 客服小秘 - API 接口文档

> 基于 OpenAPI 3.0 规范
> 服务地址: https://csbaby-sync-server.onrender.com

---

## 1. 认证接口

### 1.1 用户注册

```
POST /auth/register
Content-Type: application/json
```

**Request Body:**

| 字段 | 类型 | 必填 | 描述 |
|------|------|------|------|
| email | string | 是 | 邮箱地址 |
| password | string | 是 | 密码 (6位以上) |
| displayName | string | 否 | 显示名称 |

**Response (200):**

```json
{
  "code": 0,
  "message": "注册成功",
  "data": {
    "userId": "da83a815-bb28-46bc-b184-7293a1932f40",
    "tenantId": "67156755-a65d-42c4-8409-4650456405dd",
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresAt": 1779503040806
  }
}
```

**Error Responses:**

| code | message | 说明 |
|------|---------|------|
| 400 | 缺少必填字段 | 参数校验失败 |
| 409 | 该邮箱已被注册 | 邮箱重复 |

---

### 1.2 用户登录

```
POST /auth/login
Content-Type: application/json
```

**Request Body:**

| 字段 | 类型 | 必填 | 描述 |
|------|------|------|------|
| email | string | 是 | 邮箱地址 |
| password | string | 是 | 密码 |

**Response (200):**

```json
{
  "code": 0,
  "message": "登录成功",
  "data": {
    "userId": "da83a815-bb28-46bc-b184-7293a1932f40",
    "tenantId": "67156755-a65d-42c4-8409-4650456405dd",
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresAt": 1779503040806
  }
}
```

**Error Responses:**

| code | message | 说明 |
|------|---------|------|
| 400 | 缺少必填字段 | 参数校验失败 |
| 401 | 邮箱或密码错误 | 认证失败 |

---

### 1.3 刷新 Token

```
POST /auth/refresh
Content-Type: application/json
```

**Request Body:**

| 字段 | 类型 | 必填 | 描述 |
|------|------|------|------|
| refreshToken | string | 是 | 刷新令牌 |

**Response (200):**

```json
{
  "code": 0,
  "message": "刷新成功",
  "data": {
    "userId": "...",
    "tenantId": "...",
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresAt": 1779503040806
  }
}
```

**Error Responses:**

| code | message | 说明 |
|------|---------|------|
| 400 | 缺少 refreshToken | 参数缺失 |
| 401 | 刷新令牌无效或已过期 | Token 失效 |

---

## 2. 同步接口

> **认证要求:** 所有接口需要在 Header 中携带 `Authorization: Bearer {accessToken}`

### 2.1 全量同步

```
GET /sync/all?tenantId={tenantId}
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| tenantId | string (UUID) | 是 | 租户 ID |

**Response (200):**

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "keywordRules": [
      {
        "id": 1,
        "keyword": "您好",
        "matchType": "CONTAINS",
        "replyTemplate": "您好，感谢咨询！",
        "category": "问候",
        "targetType": "ALL",
        "targetNamesJson": "[]",
        "priority": 100,
        "enabled": true,
        "createdAt": 1779499123000,
        "updatedAt": 1779499123000,
        "tenantId": "67156755-...",
        "syncVersion": 1,
        "deleted": false
      }
    ],
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

**使用场景:**
- 首次登录/注册后同步
- 换手机恢复数据
- 用户主动点击"全量同步"

---

### 2.2 增量获取变更

```
GET /sync/changes?tenantId={tenantId}&since={timestamp}
```

**Query Parameters:**

| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| tenantId | string (UUID) | 是 | 租户 ID |
| since | number (timestamp) | 否 | 起始时间戳，默认为 0 |

**Response (200):**

```json
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
    "deletedIds": {
      "keyword_rules": ["123", "456"]
    },
    "serverTime": 1779499458449,
    "hasMore": false,
    "nextCursor": null
  }
}
```

---

### 2.3 推送变更

```
POST /sync/push
Content-Type: application/json
Authorization: Bearer {accessToken}
```

**Request Body:**

```json
{
  "tenantId": "67156755-a65d-42c4-8409-4650456405dd",
  "keywordRules": [...],
  "aiModelConfigs": [...],
  "userStyleProfile": {...},
  "appConfigs": [...],
  "scenarios": [...],
  "replyHistory": [...],
  "messageBlacklist": [...],
  "deletedIds": {},
  "baseVersion": 1
}
```

**Response (200):**

```json
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

---

## 3. 备份接口

### 3.1 上传备份

```
POST /api/v1/backup/upload
Content-Type: application/json
Authorization: Bearer {accessToken}
```

**Request Body:**

```json
{
  "device_name": "MI 12",
  "app_version": "1.4.0",
  "data": {
    "keywordRules": [...],
    "aiModelConfigs": [...]
  },
  "data_size": 102400,
  "checksum": "sha256:abc123..."
}
```

**Response (200):**

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "id": 1,
    "tenant_id": "67156755-...",
    "device_name": "MI 12",
    "app_version": "1.4.0",
    "created_at": 1779499123000
  }
}
```

---

### 3.2 备份列表

```
GET /api/v1/backup/list
Authorization: Bearer {accessToken}
```

**Response (200):**

```json
{
  "code": 0,
  "message": "成功",
  "data": [
    {
      "id": 2,
      "tenant_id": "67156755-...",
      "device_name": "MI 12",
      "app_version": "1.4.0",
      "data_size": 102400,
      "created_at": 1779499200000
    },
    {
      "id": 1,
      "tenant_id": "67156755-...",
      "device_name": "Samsung S24",
      "app_version": "1.3.0",
      "data_size": 98304,
      "created_at": 1779499123000
    }
  ]
}
```

---

### 3.3 下载备份

```
GET /api/v1/backup/download/{id}
Authorization: Bearer {accessToken}
```

**Response (200):**

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "id": 1,
    "tenant_id": "67156755-...",
    "device_name": "MI 12",
    "app_version": "1.4.0",
    "data": {
      "keywordRules": [...],
      "aiModelConfigs": [...]
    },
    "created_at": 1779499123000
  }
}
```

---

### 3.4 删除备份

```
DELETE /api/v1/backup/{id}
Authorization: Bearer {accessToken}
```

**Response (200):**

```json
{
  "code": 0,
  "message": "成功"
}
```

---

## 4. OTA 接口

### 4.1 版本检查

```
GET /api/v1/ota/check
Content-Type: application/json
```

**Request Body:**

```json
{
  "current_version": 10
}
```

**Response (200):**

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "has_update": true,
    "version_code": 11,
    "version_name": "1.4.0",
    "download_url": "https://example.com/csbaby-v140.apk",
    "file_size": 17208322,
    "md5": "abc123...",
    "is_force_update": false,
    "release_notes": "1. 新增云同步功能\n2. 修复已知问题",
    "release_date": 1779499200000
  }
}
```

---

## 5. 通用响应格式

### 5.1 成功响应

```json
{
  "code": 0,
  "message": "成功",
  "data": { ... }
}
```

### 5.2 错误响应

```json
{
  "code": 400,
  "message": "错误描述"
}
```

### 5.3 错误码说明

| code | 含义 |
|------|------|
| 0 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 / Token 失效 |
| 403 | 禁止访问 |
| 404 | 资源不存在 |
| 409 | 资源冲突 |
| 500 | 服务器内部错误 |

---

## 6. 认证流程

### 6.1 完整认证流程

```
1. 用户输入邮箱密码
2. 调用 POST /auth/login
3. 获得 accessToken 和 refreshToken
4. 存储 tokens
5. 后续请求携带 Authorization: Bearer {accessToken}
6. accessToken 过期时调用 POST /auth/refresh
7. 使用 refreshToken 获取新的 tokens
```

### 6.2 Token 有效期

| Token 类型 | 有效期 |
|-----------|--------|
| accessToken | 24 小时 |
| refreshToken | 30 天 |

---

## 7. SDK 使用示例

### 7.1 Android Kotlin

```kotlin
// 创建 Retrofit 实例
val retrofit = Retrofit.Builder()
    .baseUrl("https://csbaby-sync-server.onrender.com/")
    .client(okHttpClient)
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val apiService = retrofit.create(SyncApiService::class.java)

// 登录
val loginRequest = LoginRequest(email, password)
val response = apiService.login(loginRequest)
val authResult = response.body()?.data

// 保存 Token
authManager.saveAuthState(authResult)

// 调用同步 API
val syncResponse = apiService.getAllData(tenantId)
```

---

*本文档自动生成于 2026-05-23*