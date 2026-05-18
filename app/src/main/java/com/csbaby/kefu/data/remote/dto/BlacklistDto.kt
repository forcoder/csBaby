package com.csbaby.kefu.data.remote.dto

import com.google.gson.annotations.SerializedName

data class BlacklistDto(
    @SerializedName("id") val id: Long = 0,
    @SerializedName("type") val type: String = "KEYWORD",
    @SerializedName("value") val value: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("package_name") val packageName: String? = null,
    @SerializedName("is_enabled") val isEnabled: Boolean = true,
    @SerializedName("created_at") val createdAt: Long = 0
)
