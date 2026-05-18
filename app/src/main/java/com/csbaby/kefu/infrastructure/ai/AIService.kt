package com.csbaby.kefu.infrastructure.ai

import com.csbaby.kefu.data.remote.AIClient
import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
import com.csbaby.kefu.domain.model.UserStyleProfile
import com.csbaby.kefu.domain.repository.AIModelRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Service for handling AI model interactions.
 */
@Singleton
class AIService @Inject constructor(
    private val aiClient: AIClient,
    private val aiModelRepository: AIModelRepository
) {
    /**
     * Generate completion using the default model.
     */
    suspend fun generateCompletion(
        prompt: String,
        systemPrompt: String? = null,
        temperature: Float? = null,
        maxTokens: Int? = null
    ): Result<String> {
        val defaultModel = aiModelRepository.getDefaultModel()
            ?: return Result.failure(Exception("No default model configured"))

        return generateCompletionWithModel(
            modelId = defaultModel.id,
            prompt = prompt,
            systemPrompt = systemPrompt,
            temperature = temperature,
            maxTokens = maxTokens
        )
    }

    /**
     * Generate completion with a specific model.
     */
    suspend fun generateCompletionWithModel(
        modelId: Long,
        prompt: String,
        systemPrompt: String? = null,
        temperature: Float? = null,
        maxTokens: Int? = null
    ): Result<String> {
        val model = aiModelRepository.getModelById(modelId)
            ?: return Result.failure(Exception("Model not found"))

        val messages = buildMessages(prompt, systemPrompt)
        val result = aiClient.generateCompletion(
            config = model,
            messages = messages,
            temperature = temperature ?: model.temperature,
            maxTokens = maxTokens ?: model.maxTokens
        )

        // Update usage statistics on success
        result.onSuccess {
            // Estimate cost (simplified)
            val estimatedCost = estimateCost(prompt.length + it.length, model)
            aiModelRepository.addCost(modelId, estimatedCost)
            aiModelRepository.updateLastUsed(modelId)
        }

        return result
    }

    /**
     * Test connection to a model.
     */
    suspend fun testModelConnection(modelId: Long): Result<Boolean> {
        val model = aiModelRepository.getModelById(modelId)
            ?: return Result.failure(Exception("Model not found"))

        return aiClient.testConnection(model)
    }

    /**
     * Test connection with config directly.
     */
    suspend fun testConnection(config: AIModelConfig): Result<Boolean> {
        return aiClient.testConnection(config)
    }

    /**
     * Analyze text style using AI.
     */
    suspend fun analyzeTextStyle(text: String): Result<TextStyleAnalysis> {
        val analysisPrompt = """
            Analyze the writing style of the following text and provide metrics:
            1. Formality level (0-1, where 0 is very casual and 1 is very formal)
            2. Enthusiasm level (0-1, where 0 is neutral and 1 is very enthusiastic)
            3. Professionalism level (0-1, where 0 is casual and 1 is very professional)
            4. Average word count per sentence
            
            Text to analyze:
            "$text"
            
            Provide your analysis in JSON format:
            {"formality": 0.0-1.0, "enthusiasm": 0.0-1.0, "professionalism": 0.0-1.0, "avgWordsPerSentence": number}
        """.trimIndent()

        return generateCompletion(analysisPrompt, systemPrompt = "You are a text style analysis assistant.").mapCatching { response ->
            parseStyleAnalysis(response)
        }
    }

    /**
     * Adjust text to match a style profile.
     */
    suspend fun adjustStyle(
        text: String,
        styleProfile: UserStyleProfile
    ): Result<String> {
        val adjustmentPrompt = """
            Rewrite the following text to match the specified style:
            
            Original text:
            "$text"
            
            Target style:
            - Formality: ${(styleProfile.formalityLevel * 100).toInt()}%
            - Enthusiasm: ${(styleProfile.enthusiasmLevel * 100).toInt()}%
            - Professionalism: ${(styleProfile.professionalismLevel * 100).toInt()}%
            
            Keep the meaning and key information intact, only adjust the writing style.
        """.trimIndent()

        val systemPrompt = buildStyleSystemPrompt(styleProfile)

        return generateCompletion(adjustmentPrompt, systemPrompt = systemPrompt)
    }

    private fun buildMessages(prompt: String, systemPrompt: String?): List<AIClient.Message> {
        val messages = mutableListOf<AIClient.Message>()

        if (!systemPrompt.isNullOrBlank()) {
            messages.add(AIClient.Message("system", systemPrompt))
        }

        messages.add(AIClient.Message("user", prompt))

        return messages
    }

    private fun buildStyleSystemPrompt(profile: UserStyleProfile): String {
        val formalityDesc = when {
            profile.formalityLevel < 0.3 -> "very casual, conversational"
            profile.formalityLevel < 0.5 -> "somewhat casual"
            profile.formalityLevel < 0.7 -> "somewhat formal"
            else -> "formal, professional"
        }

        val enthusiasmDesc = when {
            profile.enthusiasmLevel < 0.3 -> "reserved, neutral"
            profile.enthusiasmLevel < 0.5 -> "calm"
            profile.enthusiasmLevel < 0.7 -> "friendly"
            else -> "enthusiastic, warm"
        }

        val professionalismDesc = when {
            profile.professionalismLevel < 0.3 -> "friendly and approachable"
            profile.professionalismLevel < 0.5 -> "knowledgeable"
            profile.professionalismLevel < 0.7 -> "professional"
            else -> "expert-level professional"
        }

        return "You are writing as a customer service representative who is $formalityDesc, $enthusiasmDesc, and $professionalismDesc."
    }

    private fun estimateCost(totalTokens: Int, model: AIModelConfig): Double {
        // Simplified cost estimation
        val costPer1kTokens = when (model.modelType) {
            ModelType.OPENAI -> 0.002 // GPT-3.5-turbo
            ModelType.CLAUDE -> 0.001 // Claude Haiku
            ModelType.ZHIPU -> 0.001 // Zhipu
            ModelType.TONGYI -> 0.001 // Qwen
            ModelType.CUSTOM -> 0.0
        }
        return totalTokens / 1000.0 * costPer1kTokens
    }


    private fun parseStyleAnalysis(response: String): TextStyleAnalysis {
        return try {
            val json = response.trim().let {
                // Remove markdown code blocks if present
                if (it.startsWith("```")) {
                    it.substringAfter("```json").substringBefore("```").trim()
                } else {
                    it
                }
            }
            val obj = org.json.JSONObject(json)
            TextStyleAnalysis(
                formality = obj.getDouble("formality").toFloat().coerceIn(0f, 1f),
                enthusiasm = obj.getDouble("enthusiasm").toFloat().coerceIn(0f, 1f),
                professionalism = obj.getDouble("professionalism").toFloat().coerceIn(0f, 1f),
                avgWordsPerSentence = obj.getDouble("avgWordsPerSentence").toFloat()
            )
        } catch (e: Exception) {
            // Return default analysis on parse failure
            TextStyleAnalysis(
                formality = 0.5f,
                enthusiasm = 0.5f,
                professionalism = 0.5f,
                avgWordsPerSentence = 15f
            )
        }
    }

    data class TextStyleAnalysis(
        val formality: Float,
        val enthusiasm: Float,
        val professionalism: Float,
        val avgWordsPerSentence: Float
    )
}
