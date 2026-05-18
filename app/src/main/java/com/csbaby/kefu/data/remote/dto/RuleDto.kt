package com.csbaby.kefu.data.remote.dto

import com.google.gson.annotations.SerializedName

data class RuleDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("user_id") val userId: String = "",
    @SerializedName("keyword") val keyword: String = "",
    @SerializedName("match_type") val matchType: String = "CONTAINS",
    @SerializedName("reply_template") val replyTemplate: String = "",
    @SerializedName("category") val category: String = "",
    @SerializedName("target_type") val targetType: String = "ALL",
    @SerializedName("target_names") val targetNames: String = "[]",
    @SerializedName("priority") val priority: Int = 0,
    @SerializedName("enabled") val enabled: Int = 1,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = ""
)

data class RuleBatchRequest(
    @SerializedName("rules") val rules: List<RuleDto> = emptyList(),
    @SerializedName("mode") val mode: String = "append"
)

data class BatchImportResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("imported") val imported: Int = 0,
    @SerializedName("total") val total: Int = 0
)
