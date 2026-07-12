package com.csbaby.kefu.presentation.screens.knowledge

/**
 * 剪贴板写入抽象。封装 Android `ClipboardManager`,便于:
 * 1. JVM 单元测试 mock(framework 的 `ClipboardManager.setPrimaryClip` 在 mockk 默认配置下无法 mock)
 * 2. 未来切换实现(例如带隐私过滤的剪贴板)
 */
interface ClipboardService {
    /**
     * 把 [text] 写入系统剪贴板,带 [label] 标识来源。
     * 失败时(如 ClipboardManager 不可用)静默返回,不抛异常。
     */
    fun putText(label: String, text: String)
}
