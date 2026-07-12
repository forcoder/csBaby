package com.csbaby.kefu.infrastructure.reply

import android.content.Context
import android.util.Log
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.domain.model.ReplyContext
import com.csbaby.kefu.domain.model.ReplyResult
import com.csbaby.kefu.domain.model.ReplySource
import com.csbaby.kefu.infrastructure.knowledge.KnowledgeBaseManager
import com.csbaby.kefu.infrastructure.notification.MessageMonitor
import com.csbaby.kefu.infrastructure.window.FloatingWindowService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reply Orchestrator - Coordinates the entire reply generation workflow.
 * Handles message reception, reply generation, and UI update.
 */
@Singleton
class ReplyOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val replyGenerator: ReplyGenerator,
    private val messageMonitor: MessageMonitor,
    private val preferencesManager: PreferencesManager,
    private val knowledgeBaseManager: KnowledgeBaseManager
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var currentJob: Job? = null
    private var collectorJob: Job? = null
    private var matcherJob: Job? = null
    private var iconObserverJob: Job? = null

    /**
     * Start orchestrating reply generation for incoming messages.
     */
    @Synchronized
    fun start() {
        Log.d(TAG, "ReplyOrchestrator.start() called, collectorJob.active=${collectorJob?.isActive}")

        // 始终启动/重启 iconObserverJob，确保监听 floatingIconEnabled
        iconObserverJob?.cancel()
        iconObserverJob = scope.launch {
            Log.d(TAG, "ReplyOrchestrator: iconObserverJob started, watching floatingIconEnabled")
            preferencesManager.userPreferencesFlow.collect { prefs ->
                Log.d(TAG, "ReplyOrchestrator: floatingIconEnabled changed to ${prefs.floatingIconEnabled}")
                if (prefs.floatingIconEnabled) {
                    // 如果开启了悬浮图标，则显示图标
                    Log.d(TAG, "ReplyOrchestrator: calling FloatingWindowService.showIconOnly")
                    FloatingWindowService.showIconOnly(context)
                } else {
                    // 如果关闭了悬浮图标，则隐藏
                    FloatingWindowService.hide(context)
                }
            }
        }

        try {
            if (collectorJob?.isActive == true) {
                Log.d(TAG, "ReplyOrchestrator: already running, skip starting collectorJob")
                return
            }

            if (matcherJob?.isActive != true) {
                matcherJob = scope.launch {
                    try {
                        Log.d(TAG, "ReplyOrchestrator: initializing knowledge base matcher")
                        knowledgeBaseManager.initializeMatcher()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize knowledge base matcher", e)
                    }
                }
            }

            collectorJob = scope.launch {
                try {
                    Log.d(TAG, "ReplyOrchestrator: messageFlow collection started")
                    messageMonitor.messageFlow.collect { message ->
                        Log.d(TAG, "ReplyOrchestrator: message received from flow: ${message.packageName} / ${previewForLog(message.content)}")
                        handleNewMessage(message)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error collecting messages", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ReplyOrchestrator", e)
        }
    }

    /**
     * Stop orchestrating.
     */
    @Synchronized
    fun stop() {
        currentJob?.cancel()
        collectorJob?.cancel()
        matcherJob?.cancel()
        iconObserverJob?.cancel()
        collectorJob = null
        matcherJob = null
        iconObserverJob = null
    }

    /**
     * Handle a new incoming message.
     */
    private fun handleNewMessage(message: MessageMonitor.MonitoredMessage) {
        try {
            // 过滤占位消息（如"给你发送了新消息"、"向你发送了一条消息"等）
            if (shouldSkipMessage(message)) {
                Log.d(
                    TAG,
                    "Skip placeholder message. title=${previewForLog(message.title)}, conversation=${previewForLog(message.conversationTitle)}, content=${previewForLog(message.content)}, package=${message.packageName}"
                )
                return
            }

            currentJob?.cancel()

            currentJob = scope.launch {
                try {
                val isBaijuyiMessage = isBaijuyiMessage(message)
                if (isBaijuyiMessage) {
                    Log.d(
                        TAG,
                        "Baijuyi message received by ReplyOrchestrator. title=${previewForLog(message.title)}, conversation=${previewForLog(message.conversationTitle)}, content=${previewForLog(message.content)}, timestamp=${message.timestamp}"
                    )
                }

                val preferences = preferencesManager.userPreferencesFlow.first()
                if (!preferences.monitoringEnabled) {
                    if (isBaijuyiMessage) {
                        Log.d(TAG, "Baijuyi message skipped in ReplyOrchestrator: monitoring disabled")
                    }
                    return@launch
                }

                val monitoredApps = preferences.selectedApps
                if (message.packageName !in monitoredApps) {

                    if (isBaijuyiMessage) {
                        Log.d(TAG, "Baijuyi message skipped in ReplyOrchestrator: package not selected")
                    }
                    return@launch
                }

                val propertyName = extractPropertyName(message)
                val replyContext = ReplyContext(
                    appPackage = message.packageName,
                    scenarioId = null,
                    conversationTitle = message.conversationTitle ?: message.title,
                    propertyName = propertyName,
                    isGroupConversation = message.isGroupConversation,
                    userId = preferences.currentUserId
                )

                if (isBaijuyiMessage) {
                    Log.d(
                        TAG,
                        "Baijuyi reply context built. conversation=${previewForLog(replyContext.conversationTitle)}, property=${previewForLog(replyContext.propertyName)}, isGroup=${replyContext.isGroupConversation}, floatingEnabled=${preferences.floatingWindowEnabled}"
                    )
                }

                val result = replyGenerator.generateReply(
                    message = message.content,
                    context = replyContext
                )

                if (isBaijuyiMessage) {
                    Log.d(
                        TAG,
                        "Baijuyi reply generated. source=${result.source}, confidence=${result.confidence}, ruleId=${result.ruleId ?: -1}, modelId=${result.modelId ?: -1}, reply=${previewForLog(result.reply)}"
                    )
                }

                if (preferences.floatingWindowEnabled) {
                    if (isBaijuyiMessage) {
                        Log.d(TAG, "Baijuyi reply forwarding to floating window")
                    }
                    showFloatingWindow(message, result)
                } else if (isBaijuyiMessage) {
                    Log.d(TAG, "Baijuyi reply not shown: floating window disabled")
                }

            } catch (e: CancellationException) {

                Log.d(TAG, "Reply generation cancelled for a newer message")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate reply", e)
            }
        }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleNewMessage", e)
        }
    }

    /**
     * Show floating window with reply suggestion.
     */
    private fun showFloatingWindow(message: MessageMonitor.MonitoredMessage, result: ReplyResult) {
        val displayData = FloatingWindowService.DisplayData(
            originalMessage = message.content,
            suggestedReply = result.reply,
            source = result.source.name,
            confidence = result.confidence,
            targetPackage = message.packageName,
            conversationTitle = (message.conversationTitle ?: message.title).ifBlank { message.appName },
            ruleId = result.ruleId ?: -1L,
            modelId = result.modelId ?: -1L
        )

        if (isBaijuyiMessage(message)) {
            Log.d(
                TAG,
                "Baijuyi floating display prepared. conversation=${previewForLog(displayData.conversationTitle)}, source=${displayData.source}, ruleId=${displayData.ruleId}, modelId=${displayData.modelId}"
            )
        }

        FloatingWindowService.show(context, displayData)
    }


    /**
     * Manually trigger reply generation for a message.
     */
    suspend fun generateReplyForMessage(
        message: String,
        appPackage: String
    ): ReplyResult {
        val preferences = preferencesManager.userPreferencesFlow.first()
        val context = ReplyContext(
            appPackage = appPackage,
            scenarioId = null,
            userId = preferences.currentUserId
        )

        return replyGenerator.generateReply(message, context)
    }

    /**
     * Record user's final reply (after they may have modified it).
     */
    suspend fun recordFinalReply(
        originalMessage: String,
        generatedReply: String,
        finalReply: String,
        appPackage: String,
        source: ReplySource,
        confidence: Float,
        ruleId: Long? = null,
        modelId: Long? = null
    ) {
        val preferences = preferencesManager.userPreferencesFlow.first()
        val context = ReplyContext(
            appPackage = appPackage,
            scenarioId = null,
            userId = preferences.currentUserId
        )

        val result = ReplyResult(
            reply = generatedReply,
            source = source,
            confidence = confidence,
            ruleId = ruleId,
            modelId = modelId
        )

        replyGenerator.recordUserReply(
            originalMessage = originalMessage,
            generatedReply = generatedReply,
            finalReply = finalReply,
            context = context,
            result = result
        )
    }


    /**
     * Generate multiple suggestions for a message.
     */
    suspend fun generateSuggestions(
        message: String,
        appPackage: String,
        count: Int = 3
    ): List<ReplyResult> {
        val preferences = preferencesManager.userPreferencesFlow.first()
        val context = ReplyContext(
            appPackage = appPackage,
            scenarioId = null,
            userId = preferences.currentUserId
        )

        return replyGenerator.generateSuggestions(message, context, count)
    }

    /**
     * Search knowledge base rules by keyword.
     */
    suspend fun searchKnowledgeRules(query: String): List<FloatingWindowService.KnowledgeRuleItem> {
        Log.d(TAG, "Searching knowledge rules for: $query")
        
        return try {
            val rules = knowledgeBaseManager.searchRulesByKeyword(query, limit = 10)
            rules.map { rule ->
                FloatingWindowService.KnowledgeRuleItem(
                    keyword = rule.keyword,
                    replyTemplate = rule.replyTemplate,
                    matchType = rule.matchType.name,
                    targetNames = rule.targetNames,
                    category = rule.category,
                    enabled = rule.enabled,
                    priority = rule.priority,
                    syncVersion = rule.syncVersion,
                    deleted = rule.deleted,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search knowledge rules", e)
            emptyList()
        }
    }

    /**
     * 获取所有知识库规则（用于搜索联想）
     */
    suspend fun getAllKnowledgeRules(): List<FloatingWindowService.KnowledgeRuleItem> {
        return try {
            knowledgeBaseManager.getAllRules()
                .first()
                .filter { it.enabled }
                .map { rule ->
                    FloatingWindowService.KnowledgeRuleItem(
                        keyword = rule.keyword,
                        replyTemplate = rule.replyTemplate,
                        matchType = rule.matchType.name,
                        targetNames = rule.targetNames,
                        category = rule.category,
                        enabled = rule.enabled,
                        priority = rule.priority,
                        syncVersion = rule.syncVersion,
                        deleted = rule.deleted,
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "获取所有知识库规则失败", e)
            emptyList()
        }
    }

    private fun extractPropertyName(message: MessageMonitor.MonitoredMessage): String? {
        if (message.packageName != PreferencesManager.BAIJUYI_PACKAGE) {
            return null
        }

        val propertyName = listOf(message.conversationTitle, message.title)
            .map { it?.trim().orEmpty() }
            .firstOrNull { it.isNotBlank() }

        Log.d(
            TAG,
            "Baijuyi property resolved. property=${previewForLog(propertyName)}, conversation=${previewForLog(message.conversationTitle)}, title=${previewForLog(message.title)}"
        )

        return propertyName
    }

    private fun shouldSkipMessage(message: MessageMonitor.MonitoredMessage): Boolean {
        // 常见的占位通知文本模式（使用精确匹配，避免误判真实消息）
        val placeholderPatterns = listOf(
            "给你发送了新消息",
            "给你发送了新消息\\.\\.\\.",
            "向你发送了一条消息",
            "发来一条消息",
            "发来新消息",
            "新消息来了",
            "\\[图片\\]",
            "\\[表情\\]",
            "\\[语音\\]",
            "\\[视频\\]",
            "\\[文件\\]"
        )

        val content = message.content.trim()
        val title = message.title.trim()
        val conversation = message.conversationTitle?.trim().orEmpty()

        // 如果内容是空或太短（小于2个字）
        if (content.length < 2) {
            return true
        }

        // 检查是否是占位模式（完全匹配）
        for (pattern in placeholderPatterns) {
            if (content == pattern || title == pattern || conversation == pattern) {
                return true
            }
        }

        // 检查是否包含 [图片]、[表情] 等媒体占位符
        // 这些通常单独出现，是通知特有的占位符
        if (content.matches(Regex("^\\[图片\\]$")) ||
            content.matches(Regex("^\\[表情\\]$")) ||
            content.matches(Regex("^\\[语音\\]$")) ||
            content.matches(Regex("^\\[视频\\]$")) ||
            content.matches(Regex("^\\[文件\\]$"))) {
            return true
        }

        // 只检查 "发送了新消息" 这类完全匹配的固定字符串占位符
        // 不再用 contains 检查，避免误杀真实消息
        val exactPlaceholderMessages = listOf(
            "给你发送了新消息",
            "向你发送了一条消息"
        )
        for (placeholder in exactPlaceholderMessages) {
            if (content == placeholder || title == placeholder) {
                return true
            }
        }

        return false
    }

    private fun isBaijuyiMessage(message: MessageMonitor.MonitoredMessage): Boolean {
        return message.packageName == PreferencesManager.BAIJUYI_PACKAGE
    }

    private fun previewForLog(value: String?): String {
        val sanitized = value.orEmpty()
            .replace("\n", "\\n")
            .trim()
        return if (sanitized.length <= 120) sanitized else sanitized.take(117) + "..."
    }

    companion object {

        private const val TAG = "ReplyOrchestrator"
    }
}


