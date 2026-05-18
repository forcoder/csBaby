# MEMORY

- 2026-04-06：Android 项目当前以 `com.csbaby.kefu` 作为主编译命名空间；旧的 `com.kefu.xiaomi` 原型源码已移至 `app/_disabled_sources/com/kefu/xiaomi`，默认不再参与构建。
- 2026-04-06：本机可用的 Android 构建环境为 JDK 17（`C:\Users\13880\.jdks\ms-17.0.18`）+ Android SDK（`D:\Android\SDK`），可直接执行 `gradlew.bat assembleDebug` 生成 APK。
- 2026-04-09（更新）：本机实际可用的 Android 构建环境为 JDK 19（`D:\jdk19`，由 `D:\Program Files\Java\jdk-19` 复制而来，含空格的路径在 Windows 上会导致 Gradle 无法识别）+ Android SDK（`D:\Android\SDK`）。构建时应使用 `.\gradlew.bat assembleDebug --no-daemon`；`gradlew.bat` 中已内置 `JAVA_HOME=D:\jdk19` 和 `GRADLE_OPTS=-Xmx4096m`；`gradle.properties` 已去除 UTF-8 BOM（首字节必须是 `111` 即字母 'o'）。编译成功的 APK 位于 `app\build\outputs\apk\debug\app-debug.apk`，版本号 1.1.1，版本码 7，大小约 16 MB。
- 2026-04-06 / 2026-04-07：悬浮回复链路当前采用 `NotificationListenerServiceImpl -> MessageMonitor -> ReplyOrchestrator -> FloatingWindowService -> ChatAutomationAccessibilityService`；应用启动时会自动启动编排器，首启默认勾选微信 `com.tencent.mm`、百居易 `com.myhostex.hostexapp`、美团民宿 `com.meituan.phoenix` 与途家民宿 `com.tujia.hotel`，但当前实际监控集合完全以 DataStore `kefu_settings.selected_apps` 为准，可在首页手动选择和取消，取消后不会再被启动流程或通知/编排层自动补回默认值。悬浮窗使用原生 View 展示客户消息与建议回复；建议回复区现已支持直接编辑，头部操作为更大的收起图标 + 右上角关闭打叉图标，并尝试在点击面板外时自动收起。无障碍自动发送也已同步覆盖这些聊天窗口；`ChatAutomationAccessibilityService` 现会为百居易 / 美团民宿 / 途家维护专门的聊天页画像，结合 `ACTION_SET_TEXT`、`ACTION_PASTE`、viewId/hint/描述关键词、底部节点位置、输入区唤起器、发送按钮与输入框邻近关系、以及当前交互窗口列表来查找输入框与发送按钮，并在失败时记录精简无障碍树日志，专门提升这些自定义聊天界面的兼容性。





- 2026-04-06 / 2026-04-07：当前“大模型配置”和“规则库”都已走本地 Room 持久化：模型存于 `ai_model_configs`，规则存于 `keyword_rules`（场景关联在 `rule_scenario_relation`），数据库名为 `kefu_database`；偏好开关与默认模型 ID 另存于 DataStore `kefu_settings`。首页知识库统计现已通过 `KeywordRuleDao.getRuleCountFlow()` 实时订阅规则数量变化。
- 2026-04-06 / 2026-04-07：知识库页面现已支持通过系统文件选择器直接导入 `.json` / `.csv` / `.xlsx` 规则文件（旧版 `.xls` 会提示先另存为 `.xlsx`），并提供带确认弹窗的一键清空入口；清空时会同步删除 `keyword_rules` 与 `rule_scenario_relation` 中的全部规则/关联，但保留场景配置本身。回复规则新增 `targetType` / `targetNames` 本地持久化字段；当前知识库编辑入口已统一为“适用房源”语义，默认全部房源，也可切换成指定房源并输入多个房源名称，匹配时会结合通知里的会话标题、群聊标记与百居易通知携带的房源/会话标题过滤规则，并把房源命中的专属规则排在最前面；知识库列表卡片内也会直接高亮显示“适用房源”。百居易新消息链路现已增强为优先解析 `Notification.EXTRA_MESSAGES` / `Notification.EXTRA_TEXT_LINES` 中的最新正文；同时通知层、编排层与悬浮窗层都补了百居易专用细粒度日志，房源名提取也优先使用 `conversationTitle`，用于继续排查“来了新消息但没出对应建议回复”的场景。

- 2026-04-07：当前项目没有把运行日志持久化到独立日志文件，主要通过 Android `Logcat` 输出调试信息；关键日志标签包括 `NotificationListener`、`ReplyOrchestrator`、`ReplyGenerator`、`FloatingWindowService`、`ChatAutomationA11y`。




- 2026-04-06 / 2026-04-07：`KnowledgeBaseManager.importFromJson()` / `importFromCsv()` / `importFromExcel()` 现已兼容逐行 JSON（JSON Lines/NDJSON）、CSV、`.xlsx` 和第三方旧规则导出字段（如 `trigger_condition`、`reply_content`、`status`、`applicable_properties`）；导入时“逗号分隔多关键词”会保留在同一条规则里，匹配任一关键词即可返回该规则，不再拆成多条，并会把带 `targetNames` 但未显式声明类型的旧规则默认视为房源规则；若导入数据包含 `applicable_properties`，会优先按“适用房源”映射为 `targetType=PROPERTY` 与 `targetNames`。Excel `.xlsx` 首行若使用“适用房源”或旧导出里的 `category` 列，也会按房源列导入；若使用 `回复内容` 会映射到回复模板，若使用 `规则标题` 则会去掉前缀 `关键字: ` / `关键词: ` 后映射到关键词；如仍需显式规则分类，可改用 `rule_category` / `规则分类`。





- 2026-04-06：悬浮回复现已改为“发送成功后再学习主人风格”：`FloatingWindowService` 会在自动发送成功后调用 `ReplyOrchestrator.recordFinalReply()` 写入真实采用的回复，`StyleLearningEngine` 则结合本地文本特征与最近回复的 AI 深度分析，持续更新正式度/热情度/专业度、长度偏好和常用短语；悬浮窗中的建议回复标题与元信息也会按 `AI` / `规则` 显示对应图标与来源文案。















