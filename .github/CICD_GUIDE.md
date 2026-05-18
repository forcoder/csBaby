# GitHub Actions CI/CD 配置指南

## 工作流概览

| 文件 | 触发条件 | 功能 |
|------|---------|------|
| `ci.yml` | Push/PR 到 main/develop/feature/** | Lint 检查 + 单元测试 |
| `build-debug.yml` | Push 到 main/develop；手动触发 | 编译 Debug APK + 版本递增 |
| `release.yml` | 打 tag `v*.*.*`；手动触发 | 编译 Release APK + 上传 shz.al + 创建 GitHub Release |

---

## 快速开始

### 1. 配置 GitHub Secrets

进入仓库 **Settings → Secrets and variables → Actions**，添加：

| Secret 名称 | 说明 |
|------------|------|
| `SHZAL_PASSWORD` | shz.al 上传密码（即当前脚本里的 `Abc@0987`） |

> ⚠️ **安全提示**：密码已从脚本中移除，改为从 Secret 读取。请确保不要在代码里硬编码密码。

### 2. 配置 Production 环境（可选，用于审批发布）

进入 **Settings → Environments → New environment**，命名为 `production`，
可添加必须审批的保护规则，防止误发布。

### 3. 触发方式

#### 自动触发
```bash
# 推送到 main 分支时，自动运行 CI + 构建 Debug
git push origin main

# 打 tag 时，自动完整发布流程
git tag v1.1.70
git push origin v1.1.70
```

#### 手动触发
在 GitHub 仓库 → **Actions** 页面选择对应工作流，点击 **Run workflow**。

---

## 工作流详细说明

### `ci.yml` — 代码质量检查

```
触发 → Lint 静态检查 → 单元测试
```

- 使用 `./gradlew lint` 检查代码质量
- 使用 `./gradlew test` 运行单元测试
- 测试报告以 Artifact 形式保存 7 天
- 传入 `-PskipVersionIncrement -PskipUpload` 防止 CI 修改版本号

### `build-debug.yml` — Debug 构建

```
触发 → 读取版本 → 版本递增（修改 gradle.properties）→ 编译 → 保存 APK → 提交版本变更
```

- 版本递增由 GitHub Actions 的 `sed` 命令完成（Linux runner），**不依赖** Gradle 的 `incrementVersion` 任务
- 构建时传入 `-PskipVersionIncrement -PskipUpload`，避免 Gradle 任务再次修改
- APK 以 `csBaby-vX.Y.Z-debug.apk` 命名，保存 14 天
- 版本号变更会自动提交（带 `[skip ci]` 避免循环触发）

### `release.yml` — 正式发布

```
触发 → 构建 Release APK → 上传版本信息到 shz.al → 上传 APK 到 shz.al → 创建 GitHub Release
```

分为 3 个 Job，可独立查看状态：
1. **build-release**：编译 APK，输出版本号和路径
2. **upload-shzal**：上传到 shz.al（需要 `production` 环境审批）
3. **create-github-release**：在 GitHub 创建正式 Release，仅当 tag 触发时执行

---

## 版本管理策略

本项目版本号由 `gradle.properties` 管理：
- `APP_VERSION_CODE`：纯数字，每次构建 +1
- `APP_VERSION_NAME`：语义版本 `x.y.z`，每次构建补丁号 +1

**本地构建**：运行 `build.bat`（Windows），自动递增版本并上传
**CI 构建**：由 GitHub Actions 的 shell 命令递增，Gradle 跳过 `incrementVersion`

---

## 常见问题

### Q: 构建失败报 "SDK not found"？
A: GitHub Actions 的 `ubuntu-latest` 预装了 Android SDK。如果遇到问题，可在 job 中添加：
```yaml
- uses: android-actions/setup-android@v3
```

### Q: 如何跳过版本递增？
A: 手动触发 `build-debug` 工作流时，勾选 "跳过版本号递增" 选项。

### Q: 如何发布 Release？
```bash
# 确保 main 分支是最新的
git checkout main && git pull

# 打标签（版本号与 gradle.properties 保持一致）
git tag v1.1.70 -m "Release v1.1.70"
git push origin v1.1.70
```

### Q: shz.al 密码如何更新？
A: 在 GitHub **Settings → Secrets → SHZAL_PASSWORD** 中修改，无需改代码。
