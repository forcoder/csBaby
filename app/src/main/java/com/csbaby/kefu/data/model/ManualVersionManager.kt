package com.csbaby.kefu.data.model

import com.google.gson.annotations.SerializedName
import java.util.Date

/**
 * 手动版本管理相关模型
 */

/**
 * 手动版本上传请求
 */
data class ManualVersionUploadRequest(
    @SerializedName("versionCode")
    val versionCode: Int,
    
    @SerializedName("versionName")
    val versionName: String,
    
    @SerializedName("appName")
    val appName: String = "kefu",
    
    @SerializedName("releaseNotes")
    val releaseNotes: String = "",
    
    @SerializedName("isForceUpdate")
    val isForceUpdate: Boolean = false,
    
    @SerializedName("minRequiredVersion")
    val minRequiredVersion: Int = 1,
    
    @SerializedName("channel")
    val channel: String = "default",
    
    @SerializedName("uploader")
    val uploader: String = "manual",
    
    @SerializedName("fileMd5")
    val fileMd5: String? = null,
    
    @SerializedName("fileSize")
    val fileSize: Long = 0
)

/**
 * 手动版本上传响应
 */
data class ManualVersionUploadResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("objectKey")
    val objectKey: String? = null,
    
    @SerializedName("downloadUrl")
    val downloadUrl: String? = null,
    
    @SerializedName("signature")
    val signature: String? = null,
    
    @SerializedName("uploadId")
    val uploadId: String? = null,
    
    @SerializedName("error")
    val error: String? = null,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 手动版本操作结果
 */
sealed class ManualVersionResult {
    data class Success(
        val message: String,
        val update: OtaUpdate? = null,
        val objectKey: String? = null,
        val downloadUrl: String? = null
    ) : ManualVersionResult()
    
    data class Error(
        val message: String,
        val code: Int = -1,
        val exception: Throwable? = null
    ) : ManualVersionResult()
    
    data class Progress(
        val progress: Float,      // 0.0 - 1.0
        val currentBytes: Long,
        val totalBytes: Long,
        val status: String
    ) : ManualVersionResult()
}

/**
 * 本地版本缓存信息
 */
data class LocalVersionCache(
    val versionCode: Int,
    val versionName: String,
    val cacheTime: Long = System.currentTimeMillis(),
    val objectKey: String,
    val fileSize: Long,
    val md5: String,
    val localPath: String? = null,
    val downloadUrl: String,
    val isVerified: Boolean = false,
    val lastChecked: Long = System.currentTimeMillis()
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() - cacheTime > 7 * 24 * 60 * 60 * 1000 // 7天过期
}

/**
 * 版本历史记录
 */
data class VersionHistory(
    val id: String,
    val versionCode: Int,
    val versionName: String,
    val action: VersionAction,
    val timestamp: Long,
    val user: String,
    val details: String? = null,
    val success: Boolean = true
)

/**
 * 版本操作类型
 */
enum class VersionAction {
    UPLOAD,          // 上传新版本
    DOWNLOAD,        // 下载版本
    DELETE,          // 删除版本
    SET_FORCE_UPDATE, // 设置强制更新
    VERIFY,          // 验证版本
    PUBLISH,         // 发布版本
    ROLLBACK         // 回滚版本
}

/**
 * 版本比较结果
 */
data class VersionComparison(
    val currentVersion: VersionInfo,
    val latestVersion: VersionInfo,
    val hasUpdate: Boolean,
    val isCompatible: Boolean,
    val changes: List<VersionChange>,
    val recommendation: UpdateRecommendation
)

data class VersionInfo(
    val versionCode: Int,
    val versionName: String,
    val buildDate: Date?,
    val buildNumber: String?,
    val signature: String?,
    val features: List<String>
)

data class VersionChange(
    val type: ChangeType,
    val description: String,
    val impact: ImpactLevel
)

enum class ChangeType {
    FEATURE_ADDED,   // 新增功能
    BUG_FIX,         // 修复Bug
    PERFORMANCE,     // 性能优化
    SECURITY,        // 安全更新
    COMPATIBILITY,   // 兼容性更新
    UI_IMPROVEMENT,  // UI改进
    OTHER           // 其他
}

enum class ImpactLevel {
    LOW,     // 低影响
    MEDIUM,  // 中等影响
    HIGH,    // 高影响
    CRITICAL // 关键影响
}

data class UpdateRecommendation(
    val shouldUpdate: Boolean,
    val urgency: UrgencyLevel,
    val suggestedTime: UpdateTime,
    val notes: String
)

enum class UrgencyLevel {
    OPTIONAL,      // 可选更新
    RECOMMENDED,   // 推荐更新
    IMPORTANT,     // 重要更新
    CRITICAL       // 关键更新
}

enum class UpdateTime {
    NOW,           // 立即更新
    SOON,          // 尽快更新
    CONVENIENT,    // 方便时更新
    SCHEDULED      // 安排时间更新
}

/**
 * 版本统计信息
 */
data class VersionStatistics(
    val versionCode: Int,
    val versionName: String,
    val totalDownloads: Long,
    val activeInstalls: Long,
    val crashRate: Float,
    val userRating: Float,
    val feedbackCount: Int,
    val retentionRate: Float,
    val adoptionRate: Float,
    val lastUpdated: Date
) {
    /**
     * 计算健康评分（0-100）
     */
    val healthScore: Float
        get() {
            var score = 0f
            score += (1 - crashRate) * 30 // 崩溃率占30分
            score += userRating * 20      // 用户评分占20分
            score += retentionRate * 25   // 留存率占25分
            score += adoptionRate * 25    // 采用率占25分
            return score.coerceIn(0f, 100f)
        }
    
    /**
     * 是否健康（评分 >= 70）
     */
    val isHealthy: Boolean
        get() = healthScore >= 70f
}

/**
 * 版本回滚配置
 */
data class VersionRollbackConfig(
    val fromVersion: VersionInfo,
    val toVersion: VersionInfo,
    val reason: String,
    val strategy: RollbackStrategy,
    val scheduleTime: Date? = null,
    val targetUsers: Set<String> = emptySet(),
    val notifyUsers: Boolean = true
)

enum class RollbackStrategy {
    IMMEDIATE_ALL,       // 立即全部回滚
    GRADUAL_PERCENTAGE,  // 逐步百分比回滚
    SCHEDULED_BATCH,     // 计划批次回滚
    USER_OPT_IN          // 用户选择回滚
}