package com.csbaby.kefu.data.model

import com.google.gson.annotations.SerializedName

/**
 * 备份记录（服务端返回的元数据）
 */
data class BackupRecord(
    val id: Int = 0,
    val deviceName: String = "",
    val appVersion: String = "",
    val dataSize: Long = 0,
    val checksum: String = "",
    val createdAt: Long = 0
)

/**
 * 完整备份数据（含实际内容）
 */
data class BackupData(
    val id: Int = 0,
    val deviceName: String = "",
    val appVersion: String = "",
    val data: BackupContent? = null,
    val checksum: String = "",
    val createdAt: Long = 0,
    val dataSize: Long = 0
)

/**
 * 备份内容结构 — 与本地数据库表对应
 */
data class BackupContent(
    val keywordRules: List<Map<String, Any?>> = emptyList(),
    val aiModelConfigs: List<Map<String, Any?>> = emptyList(),
    val userStyleProfile: Map<String, Any?>? = null,
    val appConfigs: List<Map<String, Any?>> = emptyList(),
    val scenarios: List<Map<String, Any?>> = emptyList(),
    val replyHistory: List<Map<String, Any?>> = emptyList(),
    val messageBlacklist: List<Map<String, Any?>> = emptyList()
)

/**
 * 备份上传请求
 */
data class BackupUploadRequest(
    val deviceName: String,
    val appVersion: String,
    val data: BackupContent,
    val checksum: String = ""
)

/**
 * 备份操作结果状态
 */
enum class BackupStatus {
    IDLE,
    EXPORTING,      // 正在导出本地数据
    UPLOADING,      // 正在上传
    DOWNLOADING,    // 正在下载备份
    RESTORING,      // 正在恢复到本地
    SUCCESS,
    FAILED
}
