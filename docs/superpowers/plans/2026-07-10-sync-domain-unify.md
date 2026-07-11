# Sync Refactor Plan: 统一到 api.agentai0.com

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把客户端所有调用从 `sync.agentai0.com` 重定向到 `api.agentai0.com`,删除 ECS 上 csbaby-sync 容器,清理客户端 sync.agentai0.com 残留引用

**Architecture:** nginx 配置 (`api.agentai0.com` vhost) 已经将 `^/auth`、`^/sync`、`^/api/v1/backup` 反代到 8085 Flask sync 服务,所以网络层只需要让客户端 **所有 API 调用都走 api.agentai0.com** 一个域名。客户端 BuildConfig 改成单一 `API_BASE_URL` (全部走主 API 域名), SyncApiService / AuthApiService 共享同一个 Retrofit 实例。

**Tech Stack:** Android (Kotlin + Retrofit + Hilt + Hilt-Android-KSP), Gradle BuildConfig, Flask sync (已运行在 ECS 8085)

## Global Constraints

- ECS 121.43.55.151:2222 root SSH 可达,docker ps 已确认 csbaby-sync 在 8085 端口
- nginx `/etc/nginx/conf.d/api.agentai0.com.conf` 已经包含 `^/auth` / `^/sync` / `^/api/v1/backup` 反代到 8085 (无需改 nginx)
- 保留 sync 业务逻辑 (SyncManager / SyncWorker / AuthenticatedSyncClient / SyncApiService 代码都不删, 仅去掉 sync.client.agentai0.com 域名引用)
- 单 release signature (release keystore + v1.4 keystore) 一致, 任何 release apk install -r 可覆盖
- 新版本 v1.5.1 (code 22)

---

## Task 1: 客户端 BuildConfig 合并:同步 URL 统一为 api.agentai0.com

**Files:**
- Modify: `app/build.gradle.kts:30-32` — 删除 `SYNC_BASE_URL` buildConfigField
- Modify: `app/build.gradle.kts:14` — 注释更新 (已合并)

**Interfaces:**
- Consumes: 上游无
- Produces: 客户端 Gradle config 改变 + BuildConfig 编译结果

- [ ] **Step 1: 编辑 app/build.gradle.kts,删除 SYNC_BASE_URL 行**

```kotlin
    defaultConfig {
        applicationId = "com.csbaby.kefu"
        minSdk = 26
        targetSdk = 34
        versionCode = project.property("APP_VERSION_CODE").toString().toInt()
        versionName = project.property("APP_VERSION_NAME").toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 唯一 API 入口: api.agentai0.com, nginx 已经反代 auth + sync + backup 路径到 8085 Flask
        buildConfigField("String", "API_BASE_URL", "\"http://api.agentai0.com/\"")
    }
```

- [ ] **Step 2: gradle.properties 升版本号**

```bash
sed -i "s|APP_VERSION_CODE=.*|APP_VERSION_CODE=22|" gradle.properties
sed -i "s|APP_VERSION_NAME=.*|APP_VERSION_NAME=1.5.1|" gradle.properties
sed -i "s|1.5.0 - .*|1.5.1 - 同步 URL 全部统一到 api.agentai0.com|" gradle.properties
```

- [ ] **Step 3: 编译验证**

```bash
cd "D:/workspace/workbuddy/csBaby"
export JAVA_HOME="/c/Users/13880/.jdks/ms-17.0.18"
timeout 180 ./gradlew compileReleaseKotlin --no-daemon 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL (有 BUILD 失败的引用错误,因为 SyncApiService 还在用 BuildConfig.SYNC_BASE_URL,这是预期,后续 Task 修)

- [ ] **Step 4: commit 暂存 (BuildConfig 改动)**

```bash
git add app/build.gradle.kts gradle.properties
git commit -m "[refactor] build - 删除 SYNC_BASE_URL, 只保留 API_BASE_URL=api.agentai0.com"
```

---

## Task 2: AuthenticatedSyncClient + OtaAndOssModule 改成只用 API_BASE_URL

**Files:**
- Modify: `app/src/main/java/com/csbaby/kefu/data/sync/AuthenticatedSyncClient.kt:48,133` (两处 baseUrl)
- Modify: `app/src/main/java/com/csbaby/kefu/di/OtaAndOssModule.kt:47` (OtaAndOssModule 是 sync 之外另一个用 SYNC_BASE_URL 的地方)
- Modify: `app/src/main/res/xml/network_security_config.xml:8,23` — 删除 sync.agentai0.com 项

**Interfaces:**
- Consumes: 客户端 BuildConfig.API_BASE_URL (Task 1)
- Produces: 单一 Retrofit 实例通过 `api.agentai0.com` 域名

- [ ] **Step 1: 编辑 AuthenticatedSyncClient.kt 把两处 baseUrl 替换**

文件: `app/src/main/java/com/csbaby/kefu/data/sync/AuthenticatedSyncClient.kt`

```kotlin
.baseUrl(BuildConfig.API_BASE_URL)  // 之前是 BuildConfig.SYNC_BASE_URL
```

具体改两处 (line 48 和 line 133),都用 sed 一把改:

```bash
cd "D:/workspace/workbuddy/csBaby"
sed -i 's|BuildConfig.SYNC_BASE_URL|BuildConfig.API_BASE_URL|g' \
  app/src/main/java/com/csbaby/kefu/data/sync/AuthenticatedSyncClient.kt
grep -n "API_BASE_URL\|SYNC_BASE_URL" app/src/main/java/com/csbaby/kefu/data/sync/AuthenticatedSyncClient.kt
```

Expected: 输出只出现 `API_BASE_URL`, 没有 `SYNC_BASE_URL`

- [ ] **Step 2: 编辑 OtaAndOssModule.kt (残留的 SYNC_BASE_URL)**

```bash
cd "D:/workspace/workbuddy/csBaby"
grep -rn "BuildConfig.SYNC_BASE_URL" app/src/main 2>&1
```

如果还有文件引用 SYNC_BASE_URL:

```bash
sed -i 's|BuildConfig.SYNC_BASE_URL|BuildConfig.API_BASE_URL|g' \
  app/src/main/java/com/csbaby/kefu/di/OtaAndOssModule.kt
```

- [ ] **Step 3: network_security_config.xml 删除 sync.agentai0.com 项**

文件: `app/src/main/res/xml/network_security_config.xml`

编辑掉两处 sync.agentai0.com:

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">api.agentai0.com</domain>
        <domain includeSubdomains="true">shz.al</domain>
        <domain includeSubdomains="true">10.0.2.2</domain>
    </domain-config>
</network-security-config>
```

- [ ] **Step 4: 编译验证无残留 SYNC_BASE_URL 引用**

```bash
cd "D:/workspace/workbuddy/csBaby"
export JAVA_HOME="/c/Users/13880/.jdks/ms-17.0.18"
grep -rn "SYNC_BASE_URL\|sync\.agentai0" app/src 2>&1 | head -5
timeout 180 ./gradlew compileReleaseKotlin --no-daemon 2>&1 | tail -10
```

Expected: 没有任何 `SYNC_BASE_URL` 或 `sync.agentai0` 引用. `compileReleaseKotlin` SUCCESSFUL

- [ ] **Step 5: commit**

```bash
cd "D:/workspace/workbuddy/csBaby"
git add app/src/main/java/com/csbaby/kefu/data/sync/AuthenticatedSyncClient.kt \
        app/src/main/java/com/csbaby/kefu/di/OtaAndOssModule.kt \
        app/src/main/res/xml/network_security_config.xml
git commit -m "[refactor] net - sync.agentai0.com 引用全替换为 api.agentai0.com

nginx api.agentai0.com vhost 已经配置 ^/auth / ^/sync / ^/api/v1/backup
反代到 8085 Flask sync, 不需要单独 sync agentai0 域名.

消除后:
- AuthenticatedSyncClient.kt: 2 处 baseUrl 改
- OtaAndOssModule.kt: 1 处
- network_security_config.xml: 删除 sync.agentai0.com 白名单"
```

---

## Task 3: 触发 GitHub Actions 构建 v1.5.1,deploy 到 shz.al

**Files:**
- No file changes. Pure CI ops.

**Interfaces:**
- Consumes: v1.5.1 gradle.properties from Task 1
- Produces: shz.al 上 v1.5.1 release apk

- [ ] **Step 1: push 已 commit 的分支**

```bash
cd "D:/workspace/workbuddy/csBaby"
git push origin fix/ota-workflow-heredoc
```

- [ ] **Step 2: 手动触发 workflow run**

```bash
gh workflow run build-and-ota.yml --ref fix/ota-workflow-heredoc
```

- [ ] **Step 3: 等 workflow 完成 (~3 分钟)**

```bash
sleep 200
gh api repos/forcoder/csBaby/actions/runs/$(gh run list --workflow="Build and OTA Deploy" --limit 1 --json databaseId -q '.[0].databaseId') 2>&1 | python -X utf8 -c "
import json,sys
d=json.load(sys.stdin)
print('conclusion:', d['conclusion'])
"
```

Expected: conclusion=success

- [ ] **Step 4: 验证 shz.al v1.5.1 已发布**

```bash
curl -s https://shz.al/~csBabyLog | python -X utf8 -m json.tool
```

Expected: versionCode=22, versionName=1.5.1, md5 不为空

---

## Task 4: ECS 备份 csbaby-sync 容器相关源码 + 配置

**Files:**
- No files modified. Backup operations only.

**Interfaces:**
- Consumes: 容器内 `/app/controllers/auth_controller.py` 当前内容 (含上次 patch)
- Produces: 备份到 ECS host `/root/backup/`

- [ ] **Step 1: SSH 登录, 创建备份目录, 备份关键文件**

```bash
ssh -p 2222 root@121.43.55.151 "
mkdir -p /root/backup/csbaby-sync-2026-07-10
docker cp csbaby-sync:/app/app.py /root/backup/csbaby-sync-2026-07-10/app.py
docker cp csbaby-sync:/app/controllers/auth_controller.py /root/backup/csbaby-sync-2026-07-10/auth_controller.py
docker cp csbaby-sync:/app/controllers/health_controller.py /root/backup/csbaby-sync-2026-07-10/health_controller.py 2>&1 || true
docker cp csbaby-sync:/app/controllers/sync_controller.py /root/backup/csbaby-sync-2026-07-10/sync_controller.py 2>&1 || true
docker cp csbaby-sync:/app/controllers/backup_controller.py /root/backup/csbaby-sync-2026-07-10/backup_controller.py 2>&1 || true
docker cp csbaby-sync:/app/models/user.py /root/backup/csbaby-sync-2026-07-10/user.py 2>&1 || true
docker cp csbaby-sync:/app/utils/auth.py /root/backup/csbaby-sync-2026-07-10/auth.py 2>&1 || true
ls -la /root/backup/csbaby-sync-2026-07-10/
"
```

Expected: 列出多个 .py 文件, 体积不为 0

- [ ] **Step 2: 验证 nginx api.agentai0.com.vhost 已反代 sync 路径**

```bash
ssh -p 2222 root@121.43.55.151 "grep -A 2 'sync.*8085' /etc/nginx/conf.d/api.agentai0.com.conf | head -20"
```

Expected: 看到 `proxy_pass http://127.0.0.1:8085;` 行, 已经在 nginx 配置里 (无需改)

---

## Task 5: 删 ECS csbaby-sync 容器

**Files:**
- No files modified. Docker ops only.

**Interfaces:**
- Consumes: Task 4 备份
- Produces: 8085 端口被释放, csbaby-sync 容器下线

- [ ] **Step 1: 停并删容器**

```bash
ssh -p 2222 root@121.43.55.151 "
docker stop csbaby-sync
docker rm csbaby-sync
"
```

- [ ] **Step 2: 验证容器下线**

```bash
ssh -p 2222 root@121.43.55.151 "docker ps --format '{{.Names}}\t{{.Status}}' | grep csbaby-sync || echo 'csbaby-sync 不在运行列表 (已下线)'"
```

Expected: `csbaby-sync 不在运行列表 (已下线)`

- [ ] **Step 3: 测试 sync 端点 now 走 api.agentai0.com**

```bash
# nginx 上的 /sync 路径现在还有路由映射但上游无人接, 应该 502
curl -s -i http://api.agentai0.com/sync/all -H "Authorization: Bearer test" 2>&1 | head -8
```

Expected: HTTP 502 Bad Gateway (nginx 上游不可达) — 这是预期,容器已删除

- [ ] **Step 4: 测试 api.agentai0.com /auth/login 还能用 (走 nginx fallback)**

```bash
curl -s -i -X POST -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"test123"}' \
  http://api.agentai0.com/api/auth/user/login 2>&1 | head -8
```

Expected: HTTP 401 (凭证错, 但路由被 fall through 到 nginx default — 501/502 都可能, 这个 URL 不指向 8085 因为 8085 容器已下线)

⚠️ **注意**: 删 csbaby-sync 后 nginx `^/auth` / `^/sync` / `^/api/v1/backup` 反代会立即 fail (502), 因为上游 8085 不在了. 这意味着 v1.5.0/v1.5.1 客户端必须用主 API 不再用 sync 路径. 客户端调用 API 的所有 path (`/api/auth/user/login`, `/sync/all`, `/api/v1/backup/upload`) 都会变 502. 这是设计目标, 但**生产前必须让客户端全面切到主 API 域是同一台机** (否则大规模登录失败).

> **重要约束**: 用户需要确认 v1.5.1 客户端覆盖安装足够用户后(>= 90%)再删容器. **实际生产顺序调整: 先做客户端发布, 几天后再删容器**.

---

## Task 6: 客户端全面验证(实际登录 + 同步 + 备份)

**Files:** No file changes.

**Interfaces:** 真实客户端 v1.5.1

- [ ] **Step 1: 用户在手机上验证**

1. OTA 检测到 v1.5.1, 安装(同签名 -r)
2. 重新登录 (用 phone OR email, 任一字段)
3. 进入 Profile → Sync Setting → 触发同步
4. 备份列表 → 下载测试
5. 全程抓 logcat 看是否 200/401 而不是 502

- [ ] **Step 2: SSH 看 nginx access log 确认客户端请求**

```bash
ssh -p 2222 root@121.43.55.151 "tail -30 /var/log/nginx/access.log | grep api.agentai0.com"
```

Expected: 看到客户端 v1.5.1 真实请求, 主要是 `/api/auth/user/login`、`/sync/all`、`/api/v1/backup/*`

---

## Summary Checklist (最后逐项确认)

- [ ] 客户端 gradle.properties v1.5.1
- [ ] app/build.gradle.kts 删除 SYNC_BASE_URL
- [ ] AuthenticatedSyncClient.kt 两处 baseUrl 改
- [ ] OtaAndOssModule.kt baseUrl 改 (如存在)
- [ ] network_security_config.xml 删除 sync.agentai0.com
- [ ] grep `SYNC_BASE_URL|sync\\.agentai0` 0 hits
- [ ] GitHub Actions workflow success
- [ ] shz.al ~csBabyLog 显示 v1.5.1
- [ ] ECS csbaby-sync 容器已删除
- [ ] 用户手机 v1.5.1 安装, 登录 + 同步 + 备份三件套都能用
