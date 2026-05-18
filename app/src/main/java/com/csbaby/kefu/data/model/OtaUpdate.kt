package com.csbaby.kefu.data.model

import com.google.gson.annotations.SerializedName

data class OtaUpdate(
    @SerializedName("versionCode")
    val versionCode: Int,

    @SerializedName("versionName")
    val versionName: String,

    @SerializedName("downloadUrl")
    val downloadUrl: String,

    @SerializedName("fileSize")
    val fileSize: Long,

    @SerializedName("md5")
    val md5: String,

    @SerializedName("releaseNotes")
    val releaseNotes: String,

    @SerializedName("releaseDate")
    val releaseDate: String,

    @SerializedName("isForceUpdate")
    val isForceUpdate: Boolean = false,

    @SerializedName("minRequiredVersion")
    val minRequiredVersion: Int = 1,

    @SerializedName("channel")
    val channel: String? = "default"
) {
    fun needsUpdate(currentVersionCode: Int): Boolean = versionCode > currentVersionCode
}

enum class UpdateStatus {
    IDLE, CHECKING, UPDATE_AVAILABLE, DOWNLOADING, DOWNLOADED, INSTALLING, SUCCESS, FAILED
}

data class DownloadProgress(
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val progress: Float = 0f,
    val speedBytesPerSecond: Long = 0
) {
    val percentage: Int get() = (progress * 100).toInt()
    val isComplete: Boolean get() = progress >= 1f
}
