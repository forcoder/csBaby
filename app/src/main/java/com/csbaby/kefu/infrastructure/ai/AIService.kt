package com.csbaby.kefu.infrastructure.ai

import com.csbaby.kefu.data.remote.AIClient
import com.csbaby.kefu.data.remote.CsbabyApiService
import com.csbaby.kefu.data.remote.UserAuthManager
import com.csbaby.kefu.data.remote.dto.GenerateRequest
import com.csbaby.kefu.data.remote.dto.GenerateResponse
import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
import com.csbaby.kefu.domain.model.UserStyleProfile
import com.csbaby.kefu.domain.repository.AIModelRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Service for handling AI model interactions.
 */
@Singleton
class AIService @Inject constructor(
    private val aiClient: AIClient,
    private val aiModelRepository: AIModelRepository,
    private val apiService: CsbabyApiService,
    private val userAuthManager: UserAuthManager
) {
    
    // AI response cache - LRU with expiry
    private val CACHE_SIZE = 500
    private val CACHE_EXPIRY_MS = 3600000L // 1 hour
    private val responseCache = object : android.util.LruCache<String, CachedResponse>(CACHE_SIZE) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String?,
            oldValue: CachedResponse?,
            newValue: CachedResponse?
        ) {
            if (evicted) Timber.d("AI cache evicted: $key")
        }
    }

    // Retry settings
    private val MAX_RETRIES = 3
    private val RETRY_DELAY_MS = 1000L

    data class CachedResponse(
        val response: String,
        val timestamp: Long
    )

    /**
     * Generate cache key for AI requests
     */
    private fun generateCacheKey(prompt: String, systemPrompt: String?, modelId: Long?): String {
        return "${modelId ?: "default"}:${systemPrompt ?: ""}:$prompt"
    }

    /**
     * Get cached response if available and not expired
     */
    private fun getCachedResponse(key: String): String? {
        val cached = responseCache[key]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_EXPIRY_MS) {
            Timber.d("Using cached AI response")
            return cached.response
        }
        // Remove expired cache
        if (cached != null) {
            responseCache.remove(key)
        }
        return null
    }

    /**
     * Cache AI response
     */
    private fun cacheResponse(key: String, response: String) {
        responseCache.put(key, CachedResponse(response, System.currentTimeMillis()))
    }
    
    /**
     * Retry mechanism for AI requests
     */
    private suspend fun <T> withRetry(block: suspend () -> Result<T>): Result<T> {
        var lastException: Exception? = null
        
        for (attempt in 1..MAX_RETRIES) {
            val result = block()
            if (result.isSuccess) {
                return result
            }
            
            lastException = result.exceptionOrNull() as? Exception
            Timber.w("AI request attempt $attempt failed: ${lastException?.message}")
            
            if (attempt < MAX_RETRIES) {
                val delayMs = RETRY_DELAY_MS * attempt
                Timber.d("Retrying in ${delayMs}ms...")
                delay(delayMs)
            }
        }
        
        return Result.failure(lastException ?: Exception("All retry attempts failed"))
    }
    /**
     * Generate completion using the default model, with automatic fallback to other models if needed.
     */
    suspend fun generateCompletion(
        prompt: String,
        systemPrompt: String? = null,
        temperature: Float? = null,
        maxTokens: Int? = null
    ): Result<String> {
        // Get default model first
        val defaultModel = aiModelRepository.getDefaultModel()
        if (defaultModel != null && defaultModel.isEnabled && !hasReachedUsageLimit(defaultModel)) {
            val result = generateCompletionWithModel(
                modelId = defaultModel.id,
                prompt = prompt,
                systemPrompt = systemPrompt,
                temperature = temperature,
                maxTokens = maxTokens
            )

            if (result.isSuccess) {
                return result
            }
            Timber.w("Default model ${defaultModel.modelName} failed, trying other models")
        }

        // If default model failed or reached limit, try other models
        val allModels: List<AIModelConfig> = aiModelRepository.getAllModels().first()
        val otherModels = allModels.filter { model ->
            model.isEnabled && !hasReachedUsageLimit(model) && model.id != defaultModel?.id
        }.toMutableList()

        if (otherModels.isEmpty()) {
            return Result.failure(Exception("No enabled models available or all reached usage limit"))
        }

        // Sort by last used
        val sortedModels = otherModels.sortedByDescending { model -> model.lastUsed }

        // Try each model in order until one succeeds
        var lastException: Exception? = null
        for (model in sortedModels) {
            val result = generateCompletionWithModel(
                modelId = model.id,
                prompt = prompt,
                systemPrompt = systemPrompt,
                temperature = temperature,
                maxTokens = maxTokens
            )

            if (result.isSuccess) {
                // Set this model as default for future use
                Timber.i("Successfully used model ${model.modelName}, setting as default")
                aiModelRepository.setDefaultModel(model.id)
                return result
            }

            lastException = result.exceptionOrNull() as? Exception
            Timber.w("Model ${model.modelName} failed: ${lastException?.message}")
        }

        return Result.failure(lastException ?: Exception("All models failed or reached usage limit"))
    }

    /**
     * Check if a model has reached its usage limit.
     */
    private fun hasReachedUsageLimit(model: AIModelConfig): Boolean {
        // For now, we'll use a simple check based on monthly cost
        // In a real-world scenario, you would check actual API usage or remaining credits
        val monthlyLimit = 10.0 // $10 monthly limit as an example
        return model.monthlyCost >= monthlyLimit
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

        // Check cache first
        val cacheKey = generateCacheKey(prompt, systemPrompt, modelId)
        getCachedResponse(cacheKey)?.let {
            return Result.success(it)
        }

        val messages = buildMessages(prompt, systemPrompt)
        val result = withRetry {
            aiClient.generateCompletion(
                config = model,
                messages = messages,
                temperature = temperature ?: model.temperature,
                maxTokens = maxTokens ?: model.maxTokens
            )
        }

        // Update usage statistics on success
        result.onSuccess {
            // Cache the response
            cacheResponse(cacheKey, it)
            
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

        return withRetry {
            generateCompletion(analysisPrompt, systemPrompt = "You are a text style analysis assistant.")
        }.mapCatching { response ->
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

        return withRetry {
            generateCompletion(adjustmentPrompt, systemPrompt = systemPrompt)
        }
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


    /**
     * Generate reply via backend API.
     * Backend will try keyword matching first, then AI generation.
     * Falls back to local AIClient if backend is unreachable.
     */
    suspend fun generateViaBackend(
        message: String,
        context: Map<String, String> = emptyMap(),
        style: Map<String, Any> = emptyMap()
    ): Result<String> {
        return try {
            userAuthManager.ensureAuthenticated()
            val request = GenerateRequest(
                message = message,
                context = context,
                style = style
            )
            val response: GenerateResponse = apiService.generateReply(request)
            Timber.d("Backend generated reply: source=${response.source}, confidence=${response.confidence}")
            Result.success(response.reply)
        } catch (e: Exception) {
            Timber.w(e, "Backend AI generation failed, will fall back to local")
            Result.failure(e)
        }
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
