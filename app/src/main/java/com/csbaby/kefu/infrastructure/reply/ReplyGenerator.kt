package com.csbaby.kefu.infrastructure.reply

import android.util.Log
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.domain.model.*

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
    private val preferencesManager: PreferencesManager
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
            onSuccess = { reply ->
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
            You are a professional customer service assistant. Your role is to help generate helpful, accurate, and polite responses to customer inquiries.
            
            Guidelines:
            - Be helpful and solution-oriented
            - Use a professional but friendly tone
            - Keep responses concise and clear
            - Acknowledge the customer's concern before providing a solution
        """.trimIndent()

        // Add style customization if available
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
            Customer message:
            "$message"
            
            App context: ${context.appPackage}
            ${context.scenarioId?.let { "Scenario: $it" } ?: ""}
            
            Please generate an appropriate customer service response.
        """.trimIndent()
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

