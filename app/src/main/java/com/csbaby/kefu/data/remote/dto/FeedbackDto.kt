package com.csbaby.kefu.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FeedbackDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("user_id") val userId: String = "",
    @SerializedName("reply_history_id") val replyHistoryId: Int? = null,
    @SerializedName("action") val action: String = "",
    @SerializedName("modified_text") val modifiedText: String = "",
    @SerializedName("rating") val rating: Int = 0,
    @SerializedName("comment") val comment: String = "",
    @SerializedName("created_at") val createdAt: String = ""
)

data class MetricsDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("user_id") val userId: String = "",
    @SerializedName("date") val date: String = "",
    @SerializedName("total_generated") val totalGenerated: Int = 0,
    @SerializedName("total_accepted") val totalAccepted: Int = 0,
    @SerializedName("total_modified") val totalModified: Int = 0,
    @SerializedName("total_rejected") val totalRejected: Int = 0
)

data class AnalysisResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("period_days") val periodDays: Int = 0,
    @SerializedName("total_generated") val totalGenerated: Int = 0,
    @SerializedName("total_accepted") val totalAccepted: Int = 0,
    @SerializedName("total_modified") val totalModified: Int = 0,
    @SerializedName("total_rejected") val totalRejected: Int = 0,
    @SerializedName("accept_rate") val acceptRate: Double = 0.0,
    @SerializedName("modify_rate") val modifyRate: Double = 0.0,
    @SerializedName("reject_rate") val rejectRate: Double = 0.0,
    @SerializedName("suggestions") val suggestions: List<String> = emptyList()
)
