package com.csbaby.kefu.data.remote

import com.csbaby.kefu.data.model.BackupData
import com.csbaby.kefu.data.model.BackupRecord
import com.csbaby.kefu.data.model.BackupUploadRequest
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

// 登录请求 (手机号+密码)
data class LoginRequest(val phone: String, val password: String)

// 注册请求
data class RegisterRequest(
    val phone: String,
    val password: String,
    val name: String = ""
)

// Token刷新请求 (简化版：重新登录即可刷新)
data class RefreshTokenRequest(val refreshToken: String)

// 认证结果
data class AuthResult(
    val userId: String,
    val token: String,
    val expiresIn: Long = 0L
)

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
    val serverTime: Long
)

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
    val id: Long,
    val keyword: String,
    val matchType: String,
    val replyTemplate: String,
    val category: String,
    val targetType: String,
    val targetNamesJson: String,
    val priority: Int,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val tenantId: String,
    val syncVersion: Long,
    val deleted: Boolean
)

data class SyncAIModelConfig(
    val id: Long,
    val modelType: String,
    val modelName: String,
    val apiKey: String,
    val apiEndpoint: String,
    val temperature: Float,
    val maxTokens: Int,
    val isDefault: Boolean,
    val isEnabled: Boolean,
    val monthlyCost: Double,
    val lastUsed: Long,
    val createdAt: Long,
    val tenantId: String,
    val syncVersion: Long,
    val deleted: Boolean
)

data class SyncUserStyleProfile(
    val userId: String,
    val formalityLevel: Float,
    val enthusiasmLevel: Float,
    val professionalismLevel: Float,
    val wordCountPreference: Int,
    val commonPhrases: String,
    val avoidPhrases: String,
    val learningSamples: Int,
    val accuracyScore: Float,
    val lastTrained: Long,
    val createdAt: Long,
    val tenantId: String,
    val syncVersion: Long,
    val deleted: Boolean
)

data class SyncAppConfig(
    val packageName: String,
    val appName: String,
    val iconUri: String?,
    val isMonitored: Boolean,
    val createdAt: Long,
    val lastUsed: Long,
    val tenantId: String,
    val syncVersion: Long,
    val deleted: Boolean
)

data class SyncScenario(
    val id: Long,
    val name: String,
    val type: String,
    val targetId: String?,
    val description: String?,
    val createdAt: Long,
    val tenantId: String,
    val syncVersion: Long,
    val deleted: Boolean
)

data class SyncReplyHistory(
    val id: Long,
    val sourceApp: String,
    val originalMessage: String,
    val generatedReply: String,
    val finalReply: String,
    val ruleMatchedId: Long?,
    val modelUsedId: Long?,
    val styleApplied: Boolean,
    val sendTime: Long,
    val modified: Boolean,
    val tenantId: String,
    val syncVersion: Long,
    val deleted: Boolean
)

data class SyncMessageBlacklist(
    val id: Long,
    val type: String,
    val value: String,
    val description: String,
    val packageName: String?,
    val createdAt: Long,
    val isEnabled: Boolean,
    val tenantId: String,
    val syncVersion: Long,
    val deleted: Boolean
)
