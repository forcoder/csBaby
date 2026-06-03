package com.csbaby.kefu.infrastructure.reply

import android.util.Log
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.domain.model.*

import com.csbaby.kefu.domain.repository.AIModelRepository
import com.csbaby.kefu.domain.repository.ReplyHistoryRepository
import com.csbaby.kefu.domain.repository.UserStyleRepository
import com.csbaby.kefu.infrastructure.ai.AIService
import com.csbaby.kefu.infrastructure.knowledge.KnowledgeBaseManager
import com.csbaby.kefu.infrastructure.knowledge.KeywordMatcher
import com.csbaby.kefu.infrastructure.style.StyleLearningEngine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reply Generator - Core component that orchestrates reply generation.
 * 
 * Workflow:
 * 1. Keyword matching (Knowledge base priority)
 * 2. If no match → AI generation
 * 3. Apply user style
 * 4. Return optimized reply
 */
@Singleton
class ReplyGenerator @Inject constructor(
    private val knowledgeBaseManager: KnowledgeBaseManager,
    private val aiService: AIService,
    private val styleLearningEngine: StyleLearningEngine,
    private val replyHistoryRepository: ReplyHistoryRepository,
    private val userStyleRepository: UserStyleRepository,
    private val preferencesManager: PreferencesManager,
    private val aiModelRepository: AIModelRepository
) {
    companion object {
        private const val TAG = "ReplyGenerator"
        private const val RULE_MATCH_CONFIDENCE_THRESHOLD = 0.5f
        private const val KNOWLEDGE_BASE_FIRST = true
    }


    /**
     * Generate a reply for the given message.
     */
    suspend fun generateReply(
        message: String,
        context: ReplyContext
    ): ReplyResult {
        if (isBaijuyiContext(context)) {
            Log.d(
                TAG,
                "Baijuyi generateReply start. conversation=${context.conversationTitle.orEmpty()}, property=${context.propertyName.orEmpty()}, message=${previewForLog(message)}"
            )
        }

        // Step 1: Try knowledge base matching first
        if (KNOWLEDGE_BASE_FIRST) {
            val ruleMatchResult = tryKnowledgeBaseMatch(message, context)
            if (ruleMatchResult != null) {
                return ruleMatchResult
            }
        }

        // Step 2: Try AI generation
        val aiResult = tryAIGeneration(message, context)
        if (aiResult != null) {
            return aiResult
        }

        if (isBaijuyiContext(context)) {
            Log.w(TAG, "Baijuyi generateReply fallback to default canned reply")
        }

        // Step 3: Return fallback
        return ReplyResult(
            reply = "感谢您的留言，我们会尽快处理您的问题。",
            source = ReplySource.RULE_MATCH,
            confidence = 0.1f,
            ruleId = null,
            modelId = null
        )
    }


    /**
     * Try to match message against knowledge base rules.
     */
    private suspend fun tryKnowledgeBaseMatch(
        message: String,
        context: ReplyContext
    ): ReplyResult? {
        val matchResult = knowledgeBaseManager.findBestMatch(message, context)
        if (matchResult == null) {
            if (isBaijuyiContext(context)) {
                Log.d(
                    TAG,
                    "Baijuyi knowledge base miss. conversation=${context.conversationTitle.orEmpty()}, property=${context.propertyName.orEmpty()}, message=${previewForLog(message)}"
                )
            }
            return null
        }

        if (isBaijuyiContext(context)) {
            Log.d(
                TAG,
                "Baijuyi knowledge base hit. ruleId=${matchResult.rule.id}, category=${matchResult.rule.category}, targetType=${matchResult.rule.targetType}, targetNames=${matchResult.rule.targetNames.joinToString("|")}, matchedText=${previewForLog(matchResult.matchedText)}, confidence=${matchResult.confidence}, priority=${matchResult.rule.priority}"
            )
        }

        // Generate reply from matched rule
        val reply = knowledgeBaseManager.generateReplyFromRule(matchResult)

        if (isBaijuyiContext(context)) {
            Log.d(TAG, "Baijuyi rule reply generated. ruleId=${matchResult.rule.id}, reply=${previewForLog(reply)}")
        }

        return ReplyResult(
            reply = reply,
            source = ReplySource.RULE_MATCH,
            confidence = matchResult.confidence,
            ruleId = matchResult.rule.id,
            modelId = null
        )
    }


    /**
     * Try to generate reply using AI.
     */
    private suspend fun tryAIGeneration(
        message: String,
        context: ReplyContext
    ): ReplyResult? {
        // Get user style profile for system prompt customization
        val styleProfile = userStyleRepository.getProfileSync(context.userId)
        val preferences = preferencesManager.userPreferencesFlow.first()

        if (isBaijuyiContext(context)) {
            Log.d(
                TAG,
                "Baijuyi fallback to AI. defaultModelId=${preferences.defaultModelId}, styleLearningEnabled=${preferences.styleLearningEnabled}, message=${previewForLog(message)}"
            )
        }

        // Check if default model is configured
        val defaultModel = aiModelRepository.getDefaultModel()
        if (defaultModel == null) {
            if (isBaijuyiContext(context)) {
                Log.w(TAG, "Baijuyi AI generation skipped: no default model configured")
            }
            return null
        }

        // Check if API key is configured
        if (defaultModel.apiKey.isBlank()) {
            if (isBaijuyiContext(context)) {
                Log.w(TAG, "Baijuyi AI generation skipped: API key is empty for model ${defaultModel.modelName}")
            }
            return null
        }

        // Build system prompt
        val systemPrompt = buildSystemPrompt(context, styleProfile)

        // Build user prompt
        val userPrompt = buildUserPrompt(message, context)

        // Generate reply
        val result = aiService.generateCompletion(
            prompt = userPrompt,
            systemPrompt = systemPrompt,
            temperature = 0.7f,
            maxTokens = 500
        )

        return result.fold(
            onSuccess = { rawReply ->
                // 清理 AI 回复，移除推理过程、英文、前缀、格式标记等
                val reply = sanitizeAiReply(rawReply)
                // Apply style adjustment if enabled
                val finalReply = if (preferences.styleLearningEnabled && styleProfile != null) {
                    styleLearningEngine.applyStyle(reply, context.userId).getOrDefault(reply)
                } else {
                    reply
                }

                if (isBaijuyiContext(context)) {
                    Log.d(
                        TAG,
                        "Baijuyi AI reply generated. modelId=${preferences.defaultModelId.takeIf { it > 0 } ?: -1}, styleApplied=${preferences.styleLearningEnabled && styleProfile != null}, reply=${previewForLog(finalReply)}"
                    )
                }

                ReplyResult(
                    reply = finalReply,
                    source = ReplySource.AI_GENERATED,
                    confidence = 0.8f,
                    ruleId = null,
                    modelId = preferences.defaultModelId.takeIf { it > 0 }
                )
            },
            onFailure = { error ->
                if (isBaijuyiContext(context)) {
                    Log.w(TAG, "Baijuyi AI generation failed: ${error.message}")
                }
                null
            }
        )
    }


    /**
     * Build system prompt based on context and user style.
     */
    private fun buildSystemPrompt(context: ReplyContext, styleProfile: UserStyleProfile?): String {
        val basePrompt = """
            你是一位专业的中文客服助手。

            绝对规则（违反任何规则视为失败）：
            - 只输出最终的客服回复文本本身
            - 禁止输出任何英文单词、短语或术语（包括但不限于"OK"、"sure"、"hello"、"thanks"、"AI"、"language model"等）
            - 禁止输出任何解释、说明、分析、推理、思考过程或额外文字
            - 禁止输出任何前缀（如"回复："、"建议您："、"您好："、"好的："、"亲："等）
            - 禁止输出任何 markdown 格式标记（如 **、*、#、-、1. 等）
            - 禁止使用引号（单引号、双引号、反引号）包裹回复内容
            - 禁止输出表情符号或特殊符号
            - 回复必须全部使用简体中文，符合客服身份
            - 语气专业、友好、简洁
            - 回复长度控制在100字以内
            - 直接输出纯文本回复，不要有任何其他内容
        """.trimIndent()

        return if (styleProfile != null) {
            styleLearningEngine.generateStyleSystemPrompt(styleProfile) + "\n\n" + basePrompt
        } else {
            basePrompt
        }
    }

    /**
     * Build user prompt for AI generation.
     */
    private fun buildUserPrompt(message: String, context: ReplyContext): String {
        return """
            客户消息：
            "$message"

            应用上下文：${context.appPackage}
            ${context.scenarioId?.let { "场景：$it" } ?: ""}

            请生成一条简洁、专业、全中文的客服回复。只输出回复文本本身，不要有任何其他内容。
        """.trimIndent()
    }

    /**
     * 清理 AI 回复，移除推理过程、英文、前缀、格式标记等。
     */
    private fun sanitizeAiReply(raw: String): String {
        var reply = raw.trim()
        // 移除 <think>...</think> 或类似推理块
        reply = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE).replace(reply, "")
        reply = Regex("<reasoning>[\\s\\S]*?</reasoning>", RegexOption.IGNORE_CASE).replace(reply, "")
        reply = Regex("\\[思考\\][\\s\\S]*?\\[/思考\\]", RegexOption.IGNORE_CASE).replace(reply, "")
        // 移除 markdown 格式标记
        reply = Regex("[#*_~`>\\-]+").replace(reply, "")
        // 移除前缀
        reply = Regex("^(回复|建议您|您好|好的|亲|答复|回答|解决方案|建议|说明|备注|备注一下|总结|总结一下)[：:：\\s]*", RegexOption.IGNORE_CASE).replace(reply, "")
        // 移除开头的引号
        reply = reply.trimStart('"', '「', '『', '“', '‘', '`')
        reply = reply.trimEnd('"', '」', '』', '”', '’', '`')
        // 移除中英文混合中的英文部分（如果整行都是英文则保留）
        reply = reply.trim()
        return reply
    }

    /**
     * Record a user's reply for learning.
     */
    suspend fun recordUserReply(
        originalMessage: String,
        generatedReply: String,
        finalReply: String,
        context: ReplyContext,
        result: ReplyResult
    ) {
        // Check if user modified the reply
        val modified = generatedReply != finalReply

        // Create history record
        val history = ReplyHistory(
            sourceApp = context.appPackage,
            originalMessage = originalMessage,
            generatedReply = generatedReply,
            finalReply = finalReply,
            ruleMatchedId = result.ruleId,
            modelUsedId = result.modelId,
            styleApplied = result.source == ReplySource.AI_GENERATED,
            sendTime = System.currentTimeMillis(),
            modified = modified
        )

        // Save to history
        replyHistoryRepository.insertReply(history)

        // Learn from the actual reply the user chose to send
        if (finalReply.isNotBlank()) {
            styleLearningEngine.learnFromReply(context.userId, history)
        }
    }


    /**
     * Generate multiple reply suggestions.
     */
    suspend fun generateSuggestions(
        message: String,
        context: ReplyContext,
        count: Int = 3
    ): List<ReplyResult> {
        val suggestions = mutableListOf<ReplyResult>()

        // Get all knowledge base matches
        val allMatches = knowledgeBaseManager.findAllMatches(message, context)

        allMatches.take(count).forEach { match ->
            val reply = knowledgeBaseManager.generateReplyFromRule(match)
            suggestions.add(
                ReplyResult(
                    reply = reply,
                    source = ReplySource.RULE_MATCH,
                    confidence = match.confidence,
                    ruleId = match.rule.id,
                    modelId = null
                )
            )
        }

        // If we need more suggestions, use AI
        if (suggestions.size < count) {
            val aiResult = tryAIGeneration(message, context)
            if (aiResult != null) {
                suggestions.add(aiResult)
            }
        }

        return suggestions
    }

    private fun isBaijuyiContext(context: ReplyContext): Boolean {
        return context.appPackage == PreferencesManager.BAIJUYI_PACKAGE
    }

    private fun previewForLog(value: String?): String {
        val sanitized = value.orEmpty()
            .replace("\n", "\\n")
            .trim()
        return if (sanitized.length <= 120) sanitized else sanitized.take(117) + "..."
    }
}

