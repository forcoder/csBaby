package com.csbaby.kefu.data.remote.dto

import com.google.gson.annotations.SerializedName

data class BackupDto(
    @SerializedName("version") val version: Int = 2,
    @SerializedName("user_id") val userId: String = "",
    @SerializedName("rules") val rules: List<RuleDto> = emptyList(),
    @SerializedName("models") val models: List<ModelConfigDto> = emptyList(),
    @SerializedName("history") val history: List<HistoryEntryDto> = emptyList(),
    @SerializedName("feedback") val feedback: List<FeedbackDto> = emptyList(),
    @SerializedName("metrics") val metrics: List<MetricsDto> = emptyList(),
    @SerializedName("blacklist") val blacklist: List<BlacklistDto> = emptyList()
)

data class RestoreRequest(
    @SerializedName("backup") val backup: BackupDto = BackupDto()
)

data class RestoreResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("restored") val restored: RestoredCounts = RestoredCounts()
)

data class RestoredCounts(
    @SerializedName("rules") val rules: Int = 0,
    @SerializedName("models") val models: Int = 0,
    @SerializedName("blacklist") val blacklist: Int = 0
)
