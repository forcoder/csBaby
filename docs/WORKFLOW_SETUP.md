# GitHub Actions Workflows

## 自动编译配置

由于 GitHub Token 权限限制，workflow 文件需要手动更新到 GitHub。

### 更新步骤：

1. 打开 GitHub 仓库：https://github.com/forcoder/csBaby
2. 点击 **Actions** 标签
3. 如果提示启用 workflow，点击 **Enable workflow**
4. 或者手动创建：
   - 点击 **New workflow**
   - 选择 **set up a workflow yourself**
   - 将 `android-ci.yml` 的内容粘贴进去
   - 点击 **Commit changes**

### 当前 workflow 功能：

- ✅ 代码检查 (lint)
- ✅ 单元测试 (test)
- ✅ Debug APK 构建
- ✅ Release APK 构建（仅 main 分支）
- ✅ PR 触发构建

### 手动触发构建：

1. 打开 GitHub 仓库
2. 点击 **Actions** 标签
3. 选择 **Android CI - 编译最新版安卓客户端**
4. 点击 **Run workflow**
