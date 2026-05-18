package com.csbaby.kefu.data.remote

import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface AIClient {
    suspend fun generateCompletion(
        config: AIModelConfig,
        messages: List<Message>,
        temperature: Float,
        maxTokens: Int
    ): Result<String>

    suspend fun testConnection(config: AIModelConfig): Result<Boolean>

    data class Message(
        val role: String, // "system", "user", "assistant"
        val content: String
    )
}

@Singleton
class AIClientImpl @Inject constructor(
    private val okHttpClient: OkHttpClient
) : AIClient {

    override suspend fun generateCompletion(
        config: AIModelConfig,
        messages: List<AIClient.Message>,
        temperature: Float,
        maxTokens: Int
    ): Result<String> {
        return try {
            val (requestBody, headers) = buildRequest(config, messages, temperature, maxTokens)
            val requestBuilder = Request.Builder()
                .url(config.apiEndpoint)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")

            // Add model-specific headers
            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            val request = requestBuilder.build()
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return Result.failure(Exception("Empty response"))

            Timber.d("AI Response: $responseBody")

            if (response.isSuccessful) {
                val reply = parseResponse(responseBody, config.modelType)
                Result.success(reply)
            } else {
                Result.failure(Exception("API Error: ${response.code} - $responseBody"))
            }
        } catch (e: Exception) {
            Timber.e(e, "AI request failed")
            Result.failure(e)
        }
    }

    override suspend fun testConnection(config: AIModelConfig): Result<Boolean> {
        return try {
            val testMessages = listOf(
                AIClient.Message("user", "Hello")
            )
            val result = generateCompletion(config, testMessages, 0.7f, 50)
            result.map { it.isNotEmpty() }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildRequest(
        config: AIModelConfig,
        messages: List<AIClient.Message>,
        temperature: Float,
        maxTokens: Int
    ): Pair<okhttp3.RequestBody, Map<String, String>> {
        return when (config.modelType) {
            ModelType.OPENAI -> buildOpenAIRequest(config, messages, temperature, maxTokens)
            ModelType.CLAUDE -> buildClaudeRequest(config, messages, maxTokens)
            ModelType.ZHIPU -> buildZhipuRequest(config, messages, temperature, maxTokens)
            ModelType.TONGYI -> buildTongyiRequest(config, messages, temperature, maxTokens)
            ModelType.CUSTOM -> buildCustomRequest(config, messages, temperature, maxTokens)
        }
    }

    private fun buildOpenAIRequest(
        config: AIModelConfig,
        messages: List<AIClient.Message>,
        temperature: Float,
        maxTokens: Int
    ): Pair<okhttp3.RequestBody, Map<String, String>> {
        val json = JSONObject()
        json.put("model", getModelName(config))
        json.put("temperature", temperature)
        json.put("max_tokens", maxTokens)

        val messagesArray = JSONArray()
        messages.forEach { message ->
            val msgObj = JSONObject()
            msgObj.put("role", message.role)
            msgObj.put("content", message.content)
            messagesArray.put(msgObj)
        }
        json.put("messages", messagesArray)

        val headers = mapOf("Authorization" to "Bearer ${config.apiKey}")
        return Pair(json.toString().toRequestBody("application/json".toMediaType()), headers)
    }

    private fun buildClaudeRequest(
        config: AIModelConfig,
        messages: List<AIClient.Message>,
        maxTokens: Int
    ): Pair<okhttp3.RequestBody, Map<String, String>> {
        val json = JSONObject()
        json.put("model", getModelName(config))
        json.put("max_tokens", maxTokens)

        // Claude API uses "messages" array with system prompt separate
        val messagesArray = JSONArray()
        var systemContent: String? = null

        messages.forEach { message ->
            when (message.role) {
                "system" -> systemContent = message.content
                else -> {
                    val msgObj = JSONObject()
                    msgObj.put("role", message.role)
                    msgObj.put("content", message.content)
                    messagesArray.put(msgObj)
                }
            }
        }
        json.put("messages", messagesArray)

        if (systemContent != null) {
            json.put("system", systemContent)
        }

        val headers = mapOf(
            "x-api-key" to config.apiKey,
            "anthropic-version" to "2023-06-01",
            "anthropic-dangerous-direct-browser-access" to "true"
        )
        return Pair(json.toString().toRequestBody("application/json".toMediaType()), headers)
    }

    private fun buildZhipuRequest(
        config: AIModelConfig,
        messages: List<AIClient.Message>,
        temperature: Float,
        maxTokens: Int
    ): Pair<okhttp3.RequestBody, Map<String, String>> {
        val json = JSONObject()
        json.put("model", getModelName(config))
        json.put("temperature", temperature)
        json.put("max_tokens", maxTokens)

        val messagesArray = JSONArray()
        messages.forEach { message ->
            val msgObj = JSONObject()
            msgObj.put("role", message.role)
            msgObj.put("content", message.content)
            messagesArray.put(msgObj)
        }
        json.put("messages", messagesArray)

        val headers = mapOf("Authorization" to "Bearer ${config.apiKey}")
        return Pair(json.toString().toRequestBody("application/json".toMediaType()), headers)
    }

    private fun buildTongyiRequest(
        config: AIModelConfig,
        messages: List<AIClient.Message>,
        temperature: Float,
        maxTokens: Int
    ): Pair<okhttp3.RequestBody, Map<String, String>> {
        val json = JSONObject()
        json.put("model", getModelName(config))
        json.put("temperature", temperature)
        json.put("max_tokens", maxTokens)

        val messagesArray = JSONArray()
        messages.forEach { message ->
            val msgObj = JSONObject()
            msgObj.put("role", message.role)
            msgObj.put("content", message.content)
            messagesArray.put(msgObj)
        }
        json.put("messages", messagesArray)

        val headers = mapOf("Authorization" to "Bearer ${config.apiKey}")
        return Pair(json.toString().toRequestBody("application/json".toMediaType()), headers)
    }

    private fun buildCustomRequest(
        config: AIModelConfig,
        messages: List<AIClient.Message>,
        temperature: Float,
        maxTokens: Int
    ): Pair<okhttp3.RequestBody, Map<String, String>> {
        // For custom models, try to use OpenAI format as default
        return buildOpenAIRequest(config, messages, temperature, maxTokens)
    }

    private fun getModelName(config: AIModelConfig): String {
        // If modelName is set in config, use it (for CUSTOM type or overrides)
        if (config.modelName.isNotBlank()) {
            return config.modelName
        }
        return when (config.modelType) {
            ModelType.OPENAI -> "gpt-3.5-turbo"
            ModelType.CLAUDE -> "claude-3-haiku-20240307"
            ModelType.ZHIPU -> "glm-4"
            ModelType.TONGYI -> "qwen-turbo"
            ModelType.CUSTOM -> "gpt-3.5-turbo"
        }
    }

    private fun parseResponse(responseBody: String, modelType: ModelType): String {
        return try {
            val json = JSONObject(responseBody)
            when (modelType) {
                ModelType.OPENAI, ModelType.ZHIPU, ModelType.TONGYI -> {
                    // Standard chat completion format (OpenAI-compatible)
                    parseOpenAIResponse(json)
                }
                ModelType.CLAUDE -> {
                    // Claude has different response format
                    parseClaudeResponse(json)
                }
                ModelType.CUSTOM -> {
                    // Try OpenAI format first, then fallback
                    try {
                        parseOpenAIResponse(json)
                    } catch (e: Exception) {
                        parseClaudeResponse(json)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse response")
            extractContentFromResponse(responseBody)
        }
    }

    private fun parseOpenAIResponse(json: JSONObject): String {
        val choices = json.getJSONArray("choices")
        if (choices.length() > 0) {
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            return message.getString("content")
        } else {
            throw Exception("No choices in response")
        }
    }

    private fun parseClaudeResponse(json: JSONObject): String {
        // Claude response format: {"content": [{"type": "text", "text": "..."}]}
        val content = json.optJSONArray("content")
        if (content != null && content.length() > 0) {
            val firstContent = content.getJSONObject(0)
            return firstContent.getString("text")
        }
        throw Exception("No content in Claude response")
    }

    private fun extractContentFromResponse(responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            when {
                json.has("response") -> json.getString("response")
                json.has("text") -> json.getString("text")
                json.has("output") -> json.getString("output")
                json.has("content") -> {
                    val content = json.get("content")
                    if (content is String) content
                    else if (content is JSONArray) {
                        val arr = content as JSONArray
                        if (arr.length() > 0) {
                            val first = arr.getJSONObject(0)
                            if (first.has("text")) first.getString("text")
                            else first.toString()
                        } else responseBody
                    } else responseBody
                }
                json.has("choices") -> {
                    val choices = json.getJSONArray("choices")
                    if (choices.length() > 0) {
                        val first = choices.getJSONObject(0)
                        if (first.has("message")) first.getJSONObject("message").getString("content")
                        else if (first.has("content")) first.getString("content")
                        else first.toString()
                    } else responseBody
                }
                else -> responseBody
            }
        } catch (e: Exception) {
            responseBody
        }
    }
}
