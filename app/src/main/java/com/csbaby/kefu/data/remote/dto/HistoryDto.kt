package com.csbaby.kefu.data.remote.dto

import com.google.gson.annotations.SerializedName

data class HistoryEntryDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("user_id") val userId: String = "",
    @SerializedName("original_message") val originalMessage: String = "",
    @SerializedName("reply_content") val replyContent: String = "",
    @SerializedName("source") val source: String = "ai",
    @SerializedName("model_used") val modelUsed: String = "",
    @SerializedName("confidence") val confidence: Double = 0.0,
    @SerializedName("response_time_ms") val responseTimeMs: Int = 0,
    @SerializedName("platform") val platform: String = "",
    @SerializedName("customer_name") val customerName: String = "",
    @SerializedName("house_name") val houseName: String = "",
    @SerializedName("created_at") val createdAt: String = ""
)

data class HistoryPageResponse(
    @SerializedName("items") val items: List<HistoryEntryDto> = emptyList(),
    @SerializedName("total") val total: Int = 0,
    @SerializedName("limit") val limit: Int = 50,
    @SerializedName("offset") val offset: Int = 0
)
