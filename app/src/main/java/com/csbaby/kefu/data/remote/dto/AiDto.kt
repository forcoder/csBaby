package com.csbaby.kefu.data.remote.dto

import com.google.gson.annotations.SerializedName

data class GenerateRequest(
    @SerializedName("message") val message: String = "",
    @SerializedName("context") val context: Map<String, String> = emptyMap(),
    @SerializedName("style") val style: Map<String, Any> = emptyMap()
)

data class GenerateResponse(
    @SerializedName("reply") val reply: String = "",
    @SerializedName("source") val source: String = "",
    @SerializedName("rule_id") val ruleId: Int? = null,
    @SerializedName("model_used") val modelUsed: String = "",
    @SerializedName("confidence") val confidence: Double = 0.0,
    @SerializedName("response_time_ms") val responseTimeMs: Int = 0,
    @SerializedName("tokens_used") val tokensUsed: Int = 0
)

data class ChatRequest(
    @SerializedName("messages") val messages: List<ChatMessageDto> = emptyList()
)

data class ChatMessageDto(
    @SerializedName("role") val role: String = "",
    @SerializedName("content") val content: String = ""
)

data class ChatResponse(
    @SerializedName("reply") val reply: String = "",
    @SerializedName("model_used") val modelUsed: String = "",
    @SerializedName("tokens_used") val tokensUsed: Int = 0,
    @SerializedName("response_time_ms") val responseTimeMs: Int = 0
)
