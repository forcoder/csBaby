package com.csbaby.kefu.infrastructure.style

import com.csbaby.kefu.domain.model.ReplyHistory
import com.csbaby.kefu.domain.model.UserStyleProfile
import com.csbaby.kefu.domain.repository.ReplyHistoryRepository
import com.csbaby.kefu.domain.repository.UserStyleRepository
import com.csbaby.kefu.infrastructure.ai.AIService
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User style learning engine that analyzes historical replies
 * and builds a style profile for the user.
 */
@Singleton
class StyleLearningEngine @Inject constructor(
    private val replyHistoryRepository: ReplyHistoryRepository,
    private val userStyleRepository: UserStyleRepository,
    private val aiService: AIService
) {
    companion object {
        private const val MIN_SAMPLES_FOR_AI_ANALYSIS = 5
        private const val MAX_LEARNING_SAMPLES = 500
    }

    /**
     * Learn from a new reply and update the user's style profile.
     */
    suspend fun learnFromReply(userId: String, reply: ReplyHistory) {
        val finalReply = reply.finalReply.trim()
        if (finalReply.isBlank()) return

        val currentProfile = userStyleRepository.getProfileSync(userId)
        val styleSignals = analyzeLocalStyleSignals(finalReply)
        val now = System.currentTimeMillis()

        val updatedProfile = if (currentProfile == null) {
            UserStyleProfile(
                userId = userId,
                formalityLevel = styleSignals.formality,
                enthusiasmLevel = styleSignals.enthusiasm,
                professionalismLevel = styleSignals.professionalism,
                wordCountPreference = styleSignals.lengthPreference,
                commonPhrases = styleSignals.commonPhrases,
                learningSamples = 1,
                accuracyScore = calculateAccuracyScore(1),
                createdAt = now,
                lastTrained = now
            )
        } else {
            val newSampleCount = currentProfile.learningSamples + 1
            currentProfile.copy(
                formalityLevel = blendMetric(currentProfile.formalityLevel, styleSignals.formality, currentProfile.learningSamples),
                enthusiasmLevel = blendMetric(currentProfile.enthusiasmLevel, styleSignals.enthusiasm, currentProfile.learningSamples),
                professionalismLevel = blendMetric(currentProfile.professionalismLevel, styleSignals.professionalism, currentProfile.learningSamples),
                wordCountPreference = blendLengthPreference(
                    currentProfile.wordCountPreference,
                    styleSignals.lengthPreference,
                    currentProfile.learningSamples
                ),
                commonPhrases = mergeCommonPhrases(currentProfile.commonPhrases, styleSignals.commonPhrases),
                learningSamples = newSampleCount,
                accuracyScore = calculateAccuracyScore(newSampleCount),
                lastTrained = now
            )
        }

        if (currentProfile == null) {
            userStyleRepository.saveProfile(updatedProfile)
        } else {
            userStyleRepository.updateProfile(updatedProfile)
        }

        if (updatedProfile.learningSamples % 10 == 0) {
            performDeepAnalysis(userId, updatedProfile)
        }
    }

    /**
     * Batch learn from multiple replies.
     */
    suspend fun learnFromBatch(userId: String, replies: List<ReplyHistory>) {
        replies.forEach { reply ->
            learnFromReply(userId, reply)
        }
    }

    /**
     * Perform deep style analysis using AI.
     */
    private suspend fun performDeepAnalysis(userId: String, profile: UserStyleProfile) {
        val recentReplies = replyHistoryRepository.getRecentReplies(MAX_LEARNING_SAMPLES)
            .first()
            .filter { it.finalReply.isNotBlank() }
        if (recentReplies.size < MIN_SAMPLES_FOR_AI_ANALYSIS) return

        val combinedText = recentReplies.take(10).joinToString("\n---\n") { it.finalReply }

        val analysisResult = aiService.analyzeTextStyle(combinedText)
        analysisResult.onSuccess { analysis ->
            val latestProfile = userStyleRepository.getProfileSync(userId) ?: profile
            val weightedSamples = (latestProfile.learningSamples / 2).coerceAtLeast(MIN_SAMPLES_FOR_AI_ANALYSIS)
            val mergedPhrases = mergeCommonPhrases(
                latestProfile.commonPhrases,
                extractCommonPhrases(recentReplies.map { it.finalReply })
            )
            val updatedProfile = latestProfile.copy(
                formalityLevel = blendMetric(latestProfile.formalityLevel, analysis.formality, weightedSamples),
                enthusiasmLevel = blendMetric(latestProfile.enthusiasmLevel, analysis.enthusiasm, weightedSamples),
                professionalismLevel = blendMetric(latestProfile.professionalismLevel, analysis.professionalism, weightedSamples),
                commonPhrases = mergedPhrases,
                accuracyScore = calculateAccuracyScore(latestProfile.learningSamples),
                lastTrained = System.currentTimeMillis()
            )
            userStyleRepository.updateProfile(updatedProfile)
        }
    }

    private fun blendMetric(currentValue: Float, newValue: Float, sampleCount: Int): Float {
        val safeSamples = sampleCount.coerceAtLeast(1)
        return ((currentValue * safeSamples) + newValue) / (safeSamples + 1)
    }

    private fun blendLengthPreference(currentValue: Int, newValue: Int, sampleCount: Int): Int {
        val safeSamples = sampleCount.coerceAtLeast(1)
        return (((currentValue * safeSamples) + newValue) / (safeSamples + 1)).toInt()
    }

    /**
     * Calculate accuracy score based on number of samples.
     */
    private fun calculateAccuracyScore(sampleCount: Int): Float {
        return (sampleCount.toFloat() / (sampleCount + 12)).coerceIn(0f, 0.98f)
    }

    /**
     * Extract common phrases from replies.
     */
    private fun extractCommonPhrases(replies: List<String>): List<String> {
        val phraseCounts = mutableMapOf<String, Int>()

        replies.forEach { reply ->
            reply.split(Regex("[，。！？；：,.!?;:\n]+"))
                .map { it.trim() }
                .filter { it.length in 2..18 }
                .forEach { phrase ->
                    phraseCounts[phrase] = phraseCounts.getOrDefault(phrase, 0) + 1
                }
        }

        return phraseCounts
            .filter { it.value >= 2 }
            .entries
            .sortedByDescending { it.value }
            .take(8)
            .map { it.key }
    }

    private fun mergeCommonPhrases(existing: List<String>, incoming: List<String>): List<String> {
        val weights = linkedMapOf<String, Int>()
        existing.forEachIndexed { index, phrase ->
            val bonus = (existing.size - index).coerceAtLeast(1)
            weights[phrase] = weights.getOrDefault(phrase, 0) + bonus + 1
        }
        incoming.forEach { phrase ->
            weights[phrase] = weights.getOrDefault(phrase, 0) + 2
        }

        return weights.entries
            .sortedByDescending { it.value }
            .map { it.key.trim() }
            .filter { it.length in 2..18 }
            .distinct()
            .take(8)
    }

    private fun analyzeLocalStyleSignals(text: String): LocalStyleSignals {
        val normalizedText = text.trim()
        val visibleLength = normalizedText.filterNot { it.isWhitespace() }.length.coerceAtLeast(1)
        val lowerText = normalizedText.lowercase()

        val formalMarkers = listOf("您", "您好", "请", "感谢", "麻烦", "抱歉", "辛苦", "为您", "核实", "安排")
        val casualMarkers = listOf("哈", "呀", "啦", "哦", "呢", "亲", "宝", "～", "~")
        val warmMarkers = listOf("感谢", "谢谢", "好的", "没问题", "尽快", "马上", "安排", "收到")
        val professionalMarkers = listOf("核实", "确认", "处理", "反馈", "订单", "系统", "平台", "售后", "方案", "安排")
        val emojiMarkers = listOf("😊", "😄", "😁", "🙏", "🌹", "❤️", "👍")

        val formality = (
            0.45f +
                scoreMarkers(normalizedText, formalMarkers, 0.08f) -
                scoreMarkers(normalizedText, casualMarkers, 0.07f)
            ).coerceIn(0.05f, 0.98f)

        val enthusiasm = (
            0.35f +
                scoreMarkers(normalizedText, warmMarkers, 0.08f) +
                scoreMarkers(normalizedText, emojiMarkers, 0.1f) +
                normalizedText.count { it == '!' || it == '！' || it == '~' || it == '～' } * 0.05f
            ).coerceIn(0.05f, 0.98f)

        val professionalism = (
            0.45f +
                scoreMarkers(normalizedText, professionalMarkers, 0.08f) +
                if ("为您" in normalizedText || "麻烦您" in normalizedText) 0.08f else 0f -
                scoreMarkers(lowerText, listOf("哈哈", "hh", "宝子", "亲亲"), 0.09f)
            ).coerceIn(0.05f, 0.98f)

        val commonPhrases = normalizedText
            .split(Regex("[，。！？；：,.!?;:\n]+"))
            .map { it.trim() }
            .filter { it.length in 2..18 }
            .distinct()
            .take(5)

        return LocalStyleSignals(
            formality = formality,
            enthusiasm = enthusiasm,
            professionalism = professionalism,
            lengthPreference = visibleLength.coerceIn(6, 120),
            commonPhrases = commonPhrases
        )
    }

    private fun scoreMarkers(text: String, markers: List<String>, weight: Float): Float {
        val hitCount = markers.count { marker -> text.contains(marker, ignoreCase = true) }
        return hitCount * weight
    }

    private data class LocalStyleSignals(
        val formality: Float,
        val enthusiasm: Float,
        val professionalism: Float,
        val lengthPreference: Int,
        val commonPhrases: List<String>
    )

    /**
     * Get style profile for a user.
     */
    suspend fun getStyleProfile(userId: String): UserStyleProfile? {
        return userStyleRepository.getProfileSync(userId)
    }

    /**
     * Manually update style profile parameters.
     */
    suspend fun updateStyleParameters(
        userId: String,
        formality: Float? = null,
        enthusiasm: Float? = null,
        professionalism: Float? = null
    ) {
        formality?.let { userStyleRepository.updateFormalityLevel(userId, it) }
        enthusiasm?.let { userStyleRepository.updateEnthusiasmLevel(userId, it) }
        professionalism?.let { userStyleRepository.updateProfessionalismLevel(userId, it) }
    }

    /**
     * Apply style profile to generated reply.
     */
    suspend fun applyStyle(text: String, userId: String): Result<String> {
        val profile = userStyleRepository.getProfileSync(userId)
            ?: return Result.success(text)

        return aiService.adjustStyle(text, profile)
    }

    /**
     * Generate a system prompt that incorporates the user's style.
     */
    fun generateStyleSystemPrompt(profile: UserStyleProfile): String {
        val formalityDesc = when {
            profile.formalityLevel < 0.3 -> "casual and conversational"
            profile.formalityLevel < 0.5 -> "semi-formal"
            profile.formalityLevel < 0.7 -> "formal"
            else -> "very formal"
        }

        val enthusiasmDesc = when {
            profile.enthusiasmLevel < 0.3 -> "reserved"
            profile.enthusiasmLevel < 0.5 -> "neutral"
            profile.enthusiasmLevel < 0.7 -> "friendly"
            else -> "warm and enthusiastic"
        }

        val professionalismDesc = when {
            profile.professionalismLevel < 0.3 -> "approachable"
            profile.professionalismLevel < 0.5 -> "knowledgeable"
            profile.professionalismLevel < 0.7 -> "professional"
            else -> "expert"
        }

        val avgLength = profile.wordCountPreference
        val commonPhrases = if (profile.commonPhrases.isNotEmpty()) {
            "\nPreferred expressions: ${profile.commonPhrases.take(5).joinToString(", ")}"
        } else ""
        val styleConfidence = (profile.accuracyScore * 100).toInt().coerceIn(0, 98)

        return """
            You are a customer service assistant mirroring the owner's reply style.
            - Tone: $formalityDesc, $enthusiasmDesc
            - Professionalism: $professionalismDesc
            - Typical response length: around $avgLength Chinese characters
            - Style confidence: $styleConfidence%
            $commonPhrases

            Keep the core meaning accurate, but make the wording feel like it was written by the same person replying in this chat.
        """.trimIndent()
    }
}
