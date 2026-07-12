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
| **复制(ContentCopy 图标)** | **`onCopyToClipboard`** | **`viewModel.copyRuleToClipboard(id)` → 读源规则 → 只写入 replyTemplate 到 Android `ClipboardManager`** | **snackbar "已复制: {rule.keyword}"** |
| Switch(启用) | `onToggle` | `viewModel.toggleRule(id, enabled)` | Switch 状态切换 |
| **整条规则点击** | **`onClick`** | **打开 `RuleDetailDialog` 只读预览** | **底部 sheet 弹出详情,含关闭按钮** |

> ⚠️ 历史踩坑:本模块之前同时存在两种"复制"理解:
> ① 在数据库里克隆一条新规则(原始 commit 519aa085 的 `duplicateRule`)
> ② 把规则内容写入系统剪贴板供粘贴使用(**正确版本**,用户 2026-07-11 多次强调)
>
> **永远按 ② 实现**。若出现 ① 必须在 PR 中删除。详细见 §5。
>
> ⚠️ 2026-07-11 第二轮纠正:② 的复制内容范围又调整了。
> 之前 v1.5.8 我实现成 `{keyword}\n回复:{template}` 两行 — 错误。
> 真实需求:**只复制 replyTemplate**(用户点了复制按钮是去聊天窗口粘贴"回复详情",关键词是匹配用的、不应该粘贴)。
> 重写为只粘贴 replyTemplate,详情见 §3.2。

### 3.2 剪贴板内容格式

```
{replyTemplate}        ← 只复制 replyTemplate,不带 keyword,不带 "回复：" 等装饰
```

- `ClipData.newPlainText(label, text)`,label 固定为 `"csBaby 规则"`
- **只含 `rule.replyTemplate` 字段**(用户主动触发的就是回复内容)
- 若 `replyTemplate` 含换行,保留原换行(`replyTemplate` 字符串整段保留)
- 若 `replyTemplate` 为空,粘贴结果为空字符串(`""`),不报错

### 3.3 规则详情查看

| 字段 | 展示方式 |
|------|----------|
| `rule.keyword` | 大标题 |
| `rule.matchType` | 副标题(EXACT/CONTAINS/REGEX) |
| `rule.replyTemplate` | 主要内容,完整保留换行,可滚动 |
| `rule.category` | 标签 |
| `rule.targetSummary()` | 显示目标(Property / Contact / Group / 全部) |
| `rule.enabled` / `rule.deleted` / `rule.priority` / `rule.syncVersion` | metadata 一行 |

实现:
- 新增 `RuleDetailDialog(rule, onDismiss)` Composable
- `RuleItem` 整条加 `Modifier.clickable { onClick() }`
- 调用点 `onClick = { detailRule = rule }`,顶层 `if (detailRule != null) RuleDetailDialog(detailRule, onDismiss = { detailRule = null })`

### 3.4 搜索行为

| 输入状态 | 期望结果 |
|----------|----------|
| 空字符串(`""` / 仅空白) | **显示所有规则** |
| 有内容 | 按 `keyword / category / replyTemplate / targetNames` 大小写不敏感子串匹配 |
| 搜索过程中用户**逐字删除恢复为空** | **必须重新显示所有规则**,不能卡在 "搜索无结果" 状态 |

**⚠️ 历史 bug**:用户 2026-07-11 报:输入 `价格` 看到匹配项,然后删除成空字符串,UI 却继续显示空结果。

**根因**:`KnowledgeViewModel.search()` 内部依赖 UI 状态的 `allRules` 列表。如果列表初始为空、或 `_uiState.rules` 已经被覆盖过、`allRules` 没保持同步 → 空查询时恢复不出来。

**修法(代码层 `presentation/screens/knowledge/KnowledgeViewModel.kt:73`)**:

```kotlin
fun search(query: String) {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) {
        // 空查询:重读数据源,而不是依赖本地缓存(避免 stale)
        refreshUiState()
        return
    }
    val results = allRules.filter { /* ... */ }
    _uiState.update { it.copy(rules = results) }
}
```

边界场景:
- 输入 " " (空格) → trim 后 blank → 显示全部(等同空)
- 输入 "价" (未匹配) → 显示空列表(正确,不应在此 bug 范围内)
- 输入 "价格" → 恢复成空 → 显示全部 ✓

### 3.5 清空知识库

入口:TopAppBar 垃圾桶图标(`Icons.Default.Delete`)

| 触发 | 行为 |
|------|------|
| 点击 | 弹 AlertDialog 确认"将删除当前全部 N 条规则..." |
| 确认 | `viewModel.clearAllRules()` → `runCatching` → 软删除 + triggerSync |
| 取消 | 关闭 dialog,无副作用 |
| 已删除全部(空) | dialog 不可触发(`enabled = totalRuleCount > 0`) |

UI:
- 进行中:`isClearing=true` + 顶部 LinearProgressIndicator + 文字 "正在清空知识库规则..."
- 完成后:`noticeMessage` 弹"已清空知识库,共删除 N 条规则"/"知识库已经是空的"

实现:`KnowledgeViewModel.clearAllRules()`

### 3.6 导入 / 导出

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

### 3.7 复制按钮的边界处理

| 场景 | 期望行为 |
|------|----------|
| 源规则不存在(`getRuleById` 返回 null) | 不调 clipboard,不弹任何 noticeMessage(静默) |
| `replyTemplate` 为空 | 复制空字符串 `""` 到剪贴板,弹 "已复制: {keyword}",不报错 |
| 设备 ClipboardManager 服务不可用(理论上不会发生) | 静默 return,不 crash |
| 用户短时间内连续点同一个规则的复制 | 每次都会覆盖剪贴板,不需要防抖 |
| 复制后未登录 | 不需要触发 sync(剪贴板操作不涉及数据持久化) |

### 3.8 浮窗(FloatingWindowService)知识库搜索复制

需求:每条浮窗知识库搜索结果都有"复制"按钮,**复制的不只是给个提示,而是要真正把回复模板写入系统剪贴板**,用户在聊天窗口粘贴时能拿到完整 replyTemplate。

**核心 API**:`ClipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))` 必须被实际调用,仅 Toast 提示不算成功。

| 场景 | 期望行为 |
|------|----------|
| 正常路径 | 真的写 ClipData 到系统剪贴板 + Toast "已复制回复内容" |
| ClipboardManager 服务不可用 | 不抛 NPE,返回 false + Toast "复制失败,请重试" |
| setPrimaryClip 抛 SecurityException 等 | runCatching 包裹 → 返回 false + 日志 ERROR + Toast 失败 |

实现:
- `FloatingWindowService.copyReplyToClipboard(reply: String): Boolean`
  - runCatching 包整个调用
  - 成功返回 true / 失败返回 false,**不能**让 onClick lambda 静默吞异常
- onClick 根据返回值给准确 Toast:
  - `true` → "已复制回复内容"
  - `false` → "复制失败,请重试"
- 复制内容只含 `rule.replyTemplate`(与 §3.2 / §3.7 一致:**不**拼 keyword / "回复:" 行)
- ClipData label = `"suggested_reply"` (与浮窗主建议回复标识一致)
- 位置:`infrastructure/window/FloatingWindowService.kt:1006-1020`

注意:用户每次报"复制不工作"通常是因为 Toast 显示但实际 setPrimaryClip 没生效 — 这次用 runCatching 显式暴露,失败时给明确反馈。

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
| 2026-07-11 | 复制内容格式第二轮纠正:只复制 replyTemplate (去掉 keyword + "回复:" 行) | 用户第三次明确:点了复制是要粘贴"回复详情",关键词是匹配用的不要粘贴 |
| 2026-07-11 | 搜索框行为修复:输入→清空→必须显示全部 (root cause: search() 依赖 stale `allRules` 缓存) | 用户报"先输价格再清空,显示空" |
| 2026-07-11 | 新增 RuleDetailDialog:点击规则查看详情(只读,含全字段+replayTemplate 完整滚动) | 用户报"规则点击后应该可以查看详情" |
| 2026-07-11 | 知识库"复制"按钮语义固化 = 写入剪贴板 | 用户纠正了之前误把 `duplicateRule`(克隆到 DB)重新引入的实现 |
| 2026-07-12 | 浮窗知识库搜索联想列表配色(深色 popup + 浅色文字,自定义 layout) | 用户报"联想列表和背景颜色一样看不清" |
| 2026-07-12 | 浮窗知识库搜索复制加固:runCatching 异常 + 返回 Boolean + 失败 Toast | 用户报"浮窗知识库搜索的复制也无法正常使用" |
| 2026-07-11 | 知识库"导入"功能入文档(代码已实现,未文档化) | 用户多次强调后续需求先写文档 |
| 2026-06-18 | commit 519aa085 首次实现 `duplicateRule`(克隆到 DB),后被 eb6e050f 删除 | 历史 |
| 2026-04-22 | 知识库 CRUD + RDS 迁移完成 | 历史 |
