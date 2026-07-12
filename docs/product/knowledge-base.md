# csBaby 知识库模块需求文档

> 模块名:Knowledge Base
> 路径:Android `presentation/screens/knowledge/...` + `infrastructure/knowledge/...`
> 维护人:qiaozhou / Claude Code
> 文档必须领先代码。改 UI 行为前,先在本文件登记,再编码。改完代码再回头验证文档语义。

---

## 1. 模块范围

知识库管理界面 + 关键词匹配规则 CRUD。客服在 csBaby 内维护一组
"关键词 → 回复模板"的规则,匹配后用于 AI 回复 / 一键发送场景。

## 2. 关键约定

| 主题 | 行为 | 为什么 |
|------|------|--------|
| 数据存储 | 仅使用 RDS,严禁新增 SQLite / Supabase | 用户已多次明确,所有云端 + 本地(只读镜像)统一在 RDS |
| 同步 | 本地写入后 2s debounce 触发云端推送 | `SyncManager.triggerSync()` |
| 删除 | 软删除(标记 `deleted=1`),不上传硬删 | 服务端可恢复 |
| 唯一性 | `keyword` 在同一租户内可重复(业务允许,不使用 unique index) | 同关键词在不同商品下复用模板 |

## 3. UI 行为清单

每个按钮 / 入口必须在本节登记:
- 触发动作
- 输入 / 输出
- 用户期望反馈(snackbar 文案、跳转、loading 等)

### 3.1 规则列表(`KnowledgeScreen`)

| 按钮 | 触发动作 | 数据流 | UI 反馈 |
|------|----------|--------|---------|
| 编辑(铅笔图标) | `onEdit` | 打开 `RuleEditDialog` 预填该条 → 用户保存 → `viewModel.saveRule(rule)` | dialog 关闭 + 列表更新 |
| 删除(垃圾桶图标) | `onDelete` | `viewModel.deleteRule(id)` → 软删除 → triggerSync | snackbar "规则已删除" / "删除失败: ..." |
| **复制(ContentCopy 图标)** | **`onCopyToClipboard`** | **`viewModel.copyRuleToClipboard(id)` → 读源规则 → 写入 Android `ClipboardManager` → 弹 noticeMessage** | **snackbar "已复制到剪贴板: {keyword}"** |
| Switch(启用) | `onToggle` | `viewModel.toggleRule(id, enabled)` | Switch 状态切换 |

> ⚠️ 历史踩坑:本模块之前同时存在两种"复制"理解:
> ① 在数据库里克隆一条新规则(原始 commit 519aa085 的 `duplicateRule`)
> ② 把规则内容写入系统剪贴板供粘贴使用(**正确版本**,用户 2026-07-11 多次强调)
>
> **永远按 ② 实现**。若出现 ① 必须在 PR 中删除。详细见 §5。

### 3.2 剪贴板内容格式

```
{keyword}
回复：{replyTemplate}
```

- `ClipData.newPlainText(label, text)`,label 固定为 `"csBaby 规则"`
- 不带"【】"等装饰,不带双引号,直接两行
- 若 `replyTemplate` 含换行,保留原换行(`replyTemplate` 字符串整段保留)
- 若 `keyword`/`replyTemplate` 为空,仍执行复制行为(用户主动触发,空内容也可粘出)

### 3.2 导入 / 导出

> 2026-07-11 需求:知识库规则要支持 **导入** 功能,把导出的文件重新导入到 app 中。
> 代码已有完整实现:`KnowledgeScreen.importLauncher`(FileUpload IconButton)
> + `KnowledgeViewModel.importRules(uri)` + `KnowledgeBaseManager.importFromJson/CSV/xlsx`。
> 本节登记行为,防止后续误删 / 重写。

| 触发 | 入口 | 支持格式 | 不支持格式 |
|------|------|----------|------------|
| 导入 | TopAppBar 的 **FileUpload 图标** (`Icons.Default.FileUpload`) | `.json` / `.csv` / `.xlsx`(Office Open XML) | `.xls`(旧版二进制 Excel) |

实现要点:

- 使用 `ActivityResultContracts.OpenDocument()` + `mimeType` 过滤:`application/json` / `text/csv` / `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- 选择文件后,`KnowledgeViewModel.importRules(uri)` 走 `appContext.contentResolver.openInputStream(uri)`,按 MIME / 扩展名路由到对应 parser:
  - `application/json` 或 `.json` → `importFromJson`
  - `text/csv` 或 `.csv` → `importFromCsv`
  - `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` 或 `.xlsx` → `importFromExcel`
  - `.xls` → `ImportResult(0, 1, "暂不支持旧版 .xls，请另存为 .xlsx 后再导入")`
- 导入过程中 `isImporting=true`,防止用户重复点击,UI 显示 "正在导入规则文件（支持 JSON / CSV / Excel .xlsx）..."
- 完成后弹 `noticeMessage`:
  - 全部失败(成功=0):`导入失败：{错误原因}`
  - 部分成功:`导入完成：成功 N 条，失败 M 条`
  - 全部成功:`已成功导入 N 条规则`
  - 空文件:`{错误信息}` / `没有导入到任何规则`
- 任意成功条数 > 0 → 触发 `triggerAutoSyncForLoggedInUser()`,把新规则推到 RDS
- `importFromCsv` / `importFromExcel` 是 `importTabularRows(...)` 共用的私有实现,字段对应:
  `keyword, match_type, reply_template, category, enabled, target_type, target_names`

边界场景:

- Uri 为空 → 不调用 import,跳过
- ContentResolver 拿不到 InputStream → `ImportResult(0, 1, "无法打开所选文件")`
- 文件二进制损坏 / 解析异常 → `runCatching` 兜底 → `ImportResult(0, 1, exception.message ?: "导入失败")`
- 重复 key / 同 keyword 规则已经存在:当前实现是 **直接插入**,不去重(见 §2 唯一性约定)
- 导入大量数据时:`viewModelScope.launch` 不阻塞 UI 主线程

### 3.3 复制按钮的边界处理

| 场景 | 期望行为 |
|------|----------|
| 源规则不存在(`getRuleById` 返回 null) | 不调 clipboard,不弹任何 noticeMessage(静默) |
| 设备 ClipboardManager 服务不可用(理论上不会发生) | 静默 return,不 crash |
| 用户短时间内连续点同一个规则的复制 | 每次都会覆盖剪贴板,不需要防抖 |
| 复制后未登录 | 不需要触发 sync(剪贴板操作不涉及数据持久化) |

### 3.9 列表回复预览截断 (≤20 字)

| 输入长度 | 输出 |
|----------|------|
| ≤ 20 字 | 原样 |
| > 20 字 | `前 20 字 + "..."` |
| 空串或全空白 | `""`(不显示截断行) |
| 含换行的多行模板 | `take(20)` 字符截断 + `...` |

实现:
- `KnowledgeScreen.previewReply(template: String, limit: Int = 20): String`
- 在 `RuleItem` 的 reply 显示处改用 `previewReply(rule.replyTemplate)`
- 字体 `MaterialTheme.typography.bodySmall`,颜色 `onSurfaceVariant`(稍弱化,不抢视线)
- 最多 1 行 + Ellipsis(防被宽屏多行显示)

注意:
- 字符宽度估算依赖字体:此方案按"字符数"截断,简单可测;中文每字约 ~14dp,英文每字 ~7dp,在主流手机宽度上 20 字大约占屏幕 60-80%,基本单行可容纳
- 不强求"视觉上恰好一行":Compose `maxLines=1` 做兜底截断

### 3.10 浮窗知识库搜索联想列表配色

需求(2026-07-12):用户报"联想列表和背景颜色一样,根本看不清"。

| 元素 | 颜色 | 说明 |
|------|------|------|
| Popup 容器背景 | `#1E293B → #0F172A`(深色渐变,`createSuggestionListBackground()`) | 浮窗暗色风格 |
| ListView 容器背景 | 透明(继承 popup 容器) | 配合 popup |
| 列表项背景 | `#334155`(`R.layout.item_suggestion_keyword`) | 比 popup 容器稍亮,突出每条 |
| 列表项文字 | `#F1F5F9` / 14sp / center_vertical | 高对比,看得清 |

实现:
- 新建 `res/layout/item_suggestion_keyword.xml`(深色背景 + 浅色字)
- `createSuggestionPopup()` 改用 `R.layout.item_suggestion_keyword` 替代系统默认 `android.R.layout.simple_list_item_1`
- 系统 default layout 文字色 `#000000`,在 popup 暗背景上完全糊掉,**永远不能**直接复用

## 4. API/字段约定

- 列表字段:`rule.keyword`、`rule.matchType`、`rule.replyTemplate`、`rule.category`、`rule.targetSummary()`
- `KeywordRule.id == 0` → 新建;> 0 → 更新/复制源
- `KeywordRule.syncVersion` 用途:本地自增,>0 表示已同步到云端,=0 表示待同步

## 5. 变更日志

| 日期 | 变更 | 原因 |
|------|------|------|
| 2026-07-11 | 知识库"复制"按钮语义固化 = 写入剪贴板 | 用户纠正了之前误把 `duplicateRule`(克隆到 DB)重新引入的实现 |
| 2026-07-11 | 知识库"导入"功能入文档(代码已实现,未文档化) | 用户多次强调后续需求先写文档 |
| 2026-06-18 | commit 519aa085 首次实现 `duplicateRule`(克隆到 DB),后被 eb6e050f 删除 | 历史 |
| 2026-04-22 | 知识库 CRUD + RDS 迁移完成 | 历史 |
