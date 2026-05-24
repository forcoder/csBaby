package com.csbaby.kefu.data.remote

import com.csbaby.kefu.data.model.BackupData
import com.csbaby.kefu.data.model.BackupRecord
import com.csbaby.kefu.data.model.BackupUploadRequest
import com.google.gson.annotations.SerializedName
import retrofit2.http.*

/**
 * 数据同步 API 接口。
 *
 * 后端需要实现以下接口：
 * - 所有接口都需要在 Header 中携带 Authorization: Bearer {token}
 * - 所有响应统一使用 ApiResponse 包装
 *
 * 最简单的后端实现：一个 Spring Boot / Express / FastAPI 服务 + MySQL/PostgreSQL。
 * 也可以用 Supabase / Firebase / Cloudflare Workers 等 BaaS 快速搭建。
 */
interface SyncApiService {

    // ========== 认证 ==========

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<AuthResult>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<AuthResult>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): ApiResponse<AuthResult>

    // ========== 全量同步（首次登录 / 换手机恢复） ==========

    @GET("sync/all")
    suspend fun getAllData(@Query("tenantId") tenantId: String): ApiResponse<SyncAllData>

    // ========== 增量同步 ==========

    @GET("sync/changes")
    suspend fun getChanges(
        @Query("tenantId") tenantId: String,
        @Query("since") since: Long
    ): ApiResponse<SyncChanges>

    @POST("sync/push")
    suspend fun pushChanges(@Body request: PushChangesRequest): ApiResponse<PushChangesResult>

    // ========== 冲突解决 ==========

    @POST("sync/resolve")
    suspend fun resolveConflict(@Body request: ConflictResolveRequest): ApiResponse<ConflictResolveResult>

    // ========== 数据备份 ==========

    @POST("api/v1/backup/upload")
    suspend fun uploadBackup(@Body request: BackupUploadRequest): ApiResponse<BackupRecord>

    @GET("api/v1/backup/list")
    suspend fun getBackupList(): ApiResponse<List<BackupRecord>>

    @GET("api/v1/backup/download/{id}")
    suspend fun downloadBackup(@Path("id") id: Int): ApiResponse<BackupData>

    @DELETE("api/v1/backup/{id}")
    suspend fun deleteBackup(@Path("id") id: Int): ApiResponse<Unit>
}

// ========== 请求/响应数据模型 ==========

// 登录请求 (邮箱+密码 - 匹配 Node.js Express 后端)
data class LoginRequest(val email: String, val password: String)

// 注册请求
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String = ""
)

// Token刷新请求
data class RefreshTokenRequest(val refreshToken: String)

// 认证结果 (匹配 Node.js Express 后端响应格式)
data class AuthResult(
    val userId: String = "",
    val tenantId: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0L,
    val token: String = "",  // 兼容 token 字段
    val expiresIn: Long = 0L
) {
    fun effectiveAccessToken(): String = accessToken.ifEmpty { token }
}

data class SyncAllData(
    val keywordRules: List<SyncKeywordRule>,
    val aiModelConfigs: List<SyncAIModelConfig>,
    val userStyleProfile: SyncUserStyleProfile?,
    val appConfigs: List<SyncAppConfig>,
    val scenarios: List<SyncScenario>,
    val replyHistory: List<SyncReplyHistory>,
    val messageBlacklist: List<SyncMessageBlacklist>,
    val serverTime: Long
)

data class SyncChanges(
    val keywordRules: List<SyncKeywordRule>,
    val aiModelConfigs: List<SyncAIModelConfig>,
    val userStyleProfile: SyncUserStyleProfile?,
    val appConfigs: List<SyncAppConfig>,
    val scenarios: List<SyncScenario>,
    val replyHistory: List<SyncReplyHistory>,
    val messageBlacklist: List<SyncMessageBlacklist>,
    val deletedIds: Map<String, List<String>>,
    val serverTime: Long,
    val hasMore: Boolean,
    val nextCursor: String?
)

data class PushChangesRequest(
    val tenantId: String,
    val keywordRules: List<SyncKeywordRule>,
    val aiModelConfigs: List<SyncAIModelConfig>,
    val userStyleProfile: SyncUserStyleProfile?,
    val appConfigs: List<SyncAppConfig>,
    val scenarios: List<SyncScenario>,
    val replyHistory: List<SyncReplyHistory>,
    val messageBlacklist: List<SyncMessageBlacklist>,
    val deletedIds: Map<String, List<String>>,
    val baseVersion: Long
)

data class PushChangesResult(
    val accepted: Boolean,
    val conflicts: List<SyncConflict>,
    val newServerVersion: Long,
    val serverTime: Long,
    val stats: SyncStats? = null
)

data class SyncStats(
    val inserted: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0
) {
    fun summary(): String {
        return buildString {
            if (inserted > 0) append("新增 $inserted 条，")
            if (updated > 0) append("更新 $updated 条，")
            if (deleted > 0) append("删除 $deleted 条，")
            if (isEmpty()) append("无变更")
            else deleteCharAt(lastIndexOf('，'))
        }
    }
}

data class SyncConflict(
    val entityType: String,
    val entityId: String,
    val serverVersion: Any,
    val serverUpdatedAt: Long
)

data class ConflictResolveRequest(
    val tenantId: String,
    val resolutions: List<ConflictResolution>
)

data class ConflictResolution(
    val entityType: String,
    val entityId: String,
    val strategy: String, // "SERVER_WINS" or "CLIENT_WINS" or "MERGE"
    val mergedData: Any? = null
)

data class ConflictResolveResult(
    val resolved: Boolean,
    val serverTime: Long
)

// ========== 同步数据模型（与本地 Entity 字段对应） ==========

data class SyncKeywordRule(
    @SerializedName("id") val id: Long,
    @SerializedName("keyword") val keyword: String,
    @SerializedName("match_type") val matchType: String,
    @SerializedName("reply_template") val replyTemplate: String,
    @SerializedName("category") val category: String,
    @SerializedName("target_type") val targetType: String,
    @SerializedName("target_names") val targetNamesJson: String,
    @SerializedName("priority") val priority: Int,
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") val updatedAt: Long,
    @SerializedName("tenantId") val tenantId: String,
    @SerializedName("syncVersion") val syncVersion: Long,
    @SerializedName("deleted") val deleted: Boolean
)

data class SyncAIModelConfig(
    @SerializedName("id") val id: Long,
    @SerializedName("modelType") val modelType: String,
    @SerializedName("modelName") val modelName: String,
    @SerializedName("apiKey") val apiKey: String,
    @SerializedName("apiEndpoint") val apiEndpoint: String,
    @SerializedName("temperature") val temperature: Float,
    @SerializedName("maxTokens") val maxTokens: Int,
    @SerializedName("isDefault") val isDefault: Boolean,
    @SerializedName("isEnabled") val isEnabled: Boolean,
    @SerializedName("monthlyCost") val monthlyCost: Double,
    @SerializedName("lastUsed") val lastUsed: Long,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("tenantId") val tenantId: String,
    @SerializedName("syncVersion") val syncVersion: Long,
    @SerializedName("deleted") val deleted: Boolean
)

data class SyncUserStyleProfile(
    @SerializedName("userId") val userId: String,
    @SerializedName("formalityLevel") val formalityLevel: Float,
    @SerializedName("enthusiasmLevel") val enthusiasmLevel: Float,
    @SerializedName("professionalismLevel") val professionalismLevel: Float,
    @SerializedName("wordCountPreference") val wordCountPreference: Int,
    @SerializedName("commonPhrases") val commonPhrases: String,
    @SerializedName("avoidPhrases") val avoidPhrases: String,
    @SerializedName("learningSamples") val learningSamples: Int,
    @SerializedName("accuracyScore") val accuracyScore: Float,
    @SerializedName("lastTrained") val lastTrained: Long,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("tenantId") val tenantId: String,
    @SerializedName("syncVersion") val syncVersion: Long,
    @SerializedName("deleted") val deleted: Boolean
)

data class SyncAppConfig(
    @SerializedName("packageName") val packageName: String,
    @SerializedName("appName") val appName: String,
    @SerializedName("iconUri") val iconUri: String?,
    @SerializedName("isMonitored") val isMonitored: Boolean,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("lastUsed") val lastUsed: Long,
    @SerializedName("tenantId") val tenantId: String,
    @SerializedName("syncVersion") val syncVersion: Long,
    @SerializedName("deleted") val deleted: Boolean
)

data class SyncScenario(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("targetId") val targetId: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("tenantId") val tenantId: String,
    @SerializedName("syncVersion") val syncVersion: Long,
    @SerializedName("deleted") val deleted: Boolean
)

data class SyncReplyHistory(
    @SerializedName("id") val id: Long,
    @SerializedName("sourceApp") val sourceApp: String,
    @SerializedName("originalMessage") val originalMessage: String,
    @SerializedName("generatedReply") val generatedReply: String,
    @SerializedName("finalReply") val finalReply: String,
    @SerializedName("ruleMatchedId") val ruleMatchedId: Long?,
    @SerializedName("modelUsedId") val modelUsedId: Long?,
    @SerializedName("styleApplied") val styleApplied: Boolean,
    @SerializedName("sendTime") val sendTime: Long,
    @SerializedName("modified") val modified: Boolean,
    @SerializedName("tenantId") val tenantId: String,
    @SerializedName("syncVersion") val syncVersion: Long,
    @SerializedName("deleted") val deleted: Boolean
)

data class SyncMessageBlacklist(
    @SerializedName("id") val id: Long,
    @SerializedName("type") val type: String,
    @SerializedName("value") val value: String,
    @SerializedName("description") val description: String,
    @SerializedName("packageName") val packageName: String?,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("isEnabled") val isEnabled: Boolean,
    @SerializedName("tenantId") val tenantId: String,
    @SerializedName("syncVersion") val syncVersion: Long,
    @SerializedName("deleted") val deleted: Boolean
)
