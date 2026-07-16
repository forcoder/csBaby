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

    // ========== 认证（仅保留 Token 刷新，登录/注册已移至 AuthApiService）==========

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): AuthResult

    // ========== 全量同步（首次登录 / 换手机恢复） ==========

    @GET("sync/all")
    suspend fun getAllData(@Query("tenantId") tenantId: String): SyncAllData

    // ========== 增量同步 ==========

    @GET("sync/changes")
    suspend fun getChanges(
        @Query("tenantId") tenantId: String,
        @Query("since") since: Long
    ): SyncChanges

    @POST("sync/push")
    suspend fun pushChanges(@Body request: PushChangesRequest): PushChangesResult

    // ========== 冲突解决 ==========

    @POST("sync/resolve")
    suspend fun resolveConflict(@Body request: ConflictResolveRequest): ConflictResolveResult

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
    val keywordRules: List<SyncKeywordRule> = emptyList(),
    val aiModelConfigs: List<SyncAIModelConfig> = emptyList(),
    val userStyleProfile: SyncUserStyleProfile? = null,
    val appConfigs: List<SyncAppConfig> = emptyList(),
    val scenarios: List<SyncScenario> = emptyList(),
    val replyHistory: List<SyncReplyHistory> = emptyList(),
    val messageBlacklist: List<SyncMessageBlacklist> = emptyList(),
    val serverTime: Long = 0L
)

data class SyncChanges(
    val keywordRules: List<SyncKeywordRule> = emptyList(),
    val aiModelConfigs: List<SyncAIModelConfig> = emptyList(),
    val userStyleProfile: SyncUserStyleProfile? = null,
    val appConfigs: List<SyncAppConfig> = emptyList(),
    val scenarios: List<SyncScenario> = emptyList(),
    val replyHistory: List<SyncReplyHistory> = emptyList(),
    val messageBlacklist: List<SyncMessageBlacklist> = emptyList(),
    val deletedIds: Map<String, List<String>> = emptyMap(),
    val serverTime: Long = 0L,
    val hasMore: Boolean = false,
    val nextCursor: String? = null
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
    @SerializedName("deletedBusinessKeys")
    val deletedBusinessKeys: Map<String, List<Map<String, String>>> = emptyMap(),
    val baseVersion: Long
)

/**
 * 已删除规则的业务键载荷——用于服务端按业务键匹配删除。
 * 不同 entityType 使用不同字段：keyword_rules → keyword+category；ai_model_configs → modelName；
 * scenarios → name；message_blacklist → value；reply_history → originalMessage。
 */
data class DeletedBusinessKey(
    val id: String = "",
    val keyword: String = "",
    val category: String = "",
    val modelName: String = "",
    val name: String = "",
    val value: String = "",
    val originalMessage: String = ""
) {
    fun toMap(): Map<String, String> = mapOf(
        "id" to id,
        "keyword" to keyword,
        "category" to category,
        "modelName" to modelName,
        "name" to name,
        "value" to value,
        "originalMessage" to originalMessage
    ).filterValues { it.isNotEmpty() }
}

data class PushChangesResult(
    val accepted: Boolean,
    val conflicts: List<SyncConflict> = emptyList(),
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
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("keyword") val keyword: String = "",
    @SerializedName(value = "matchType", alternate = ["match_type"]) val matchType: String = "",
    @SerializedName(value = "replyTemplate", alternate = ["reply_template"]) val replyTemplate: String = "",
    @SerializedName("category") val category: String = "",
    @SerializedName(value = "targetType", alternate = ["target_type"]) val targetType: String = "",
    @SerializedName(value = "targetNamesJson", alternate = ["target_names_json"]) val targetNamesJson: String = "",
    @SerializedName("priority") val priority: Int = 0,
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName(value = "createdAt", alternate = ["created_at"]) val createdAt: Long = 0L,
    @SerializedName(value = "updatedAt", alternate = ["updated_at"]) val updatedAt: Long = 0L,
    @SerializedName(value = "tenantId", alternate = ["tenant_id"]) val tenantId: String = "",
    @SerializedName(value = "syncVersion", alternate = ["sync_version"]) val syncVersion: Long = 0L,
    @SerializedName("deleted") val deleted: Boolean = false
)

data class SyncAIModelConfig(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName(value = "modelType", alternate = ["model_type"]) val modelType: String = "",
    @SerializedName(value = "modelName", alternate = ["model_name"]) val modelName: String = "",
    @SerializedName(value = "apiKey", alternate = ["api_key"]) val apiKey: String = "",
    @SerializedName(value = "baseUrl", alternate = ["base_url"]) val apiEndpoint: String = "",
    @SerializedName("temperature") val temperature: Float = 0.7f,
    @SerializedName(value = "maxTokens", alternate = ["max_tokens"]) val maxTokens: Int = 1000,
    @SerializedName(value = "isDefault", alternate = ["is_default"]) val isDefault: Boolean = false,
    @SerializedName(value = "isEnabled", alternate = ["is_enabled"]) val isEnabled: Boolean = true,
    @SerializedName(value = "monthlyCost", alternate = ["monthly_cost"]) val monthlyCost: Double = 0.0,
    @SerializedName(value = "lastUsed", alternate = ["last_used"]) val lastUsed: Long = 0L,
    @SerializedName(value = "createdAt", alternate = ["created_at"]) val createdAt: Long = 0L,
    @SerializedName(value = "tenantId", alternate = ["tenant_id"]) val tenantId: String = "",
    @SerializedName(value = "syncVersion", alternate = ["sync_version"]) val syncVersion: Long = 0L,
    @SerializedName("deleted") val deleted: Boolean = false
)

data class SyncUserStyleProfile(
    @SerializedName(value = "userId", alternate = ["user_id"]) val userId: String = "",
    @SerializedName(value = "formalityLevel", alternate = ["formality_level"]) val formalityLevel: Float = 0.5f,
    @SerializedName(value = "enthusiasmLevel", alternate = ["enthusiasm_level"]) val enthusiasmLevel: Float = 0.5f,
    @SerializedName(value = "professionalismLevel", alternate = ["professionalism_level"]) val professionalismLevel: Float = 0.5f,
    @SerializedName(value = "wordCountPreference", alternate = ["word_count_preference"]) val wordCountPreference: Int = 50,
    @SerializedName(value = "commonPhrases", alternate = ["common_phrases"]) val commonPhrases: String = "",
    @SerializedName(value = "avoidPhrases", alternate = ["avoid_phrases"]) val avoidPhrases: String = "",
    @SerializedName(value = "learningSamples", alternate = ["learning_samples"]) val learningSamples: Int = 0,
    @SerializedName(value = "accuracyScore", alternate = ["accuracy_score"]) val accuracyScore: Float = 0f,
    @SerializedName(value = "lastTrained", alternate = ["last_trained"]) val lastTrained: Long = 0L,
    @SerializedName(value = "createdAt", alternate = ["created_at"]) val createdAt: Long = 0L,
    @SerializedName(value = "tenantId", alternate = ["tenant_id"]) val tenantId: String = "",
    @SerializedName(value = "syncVersion", alternate = ["sync_version"]) val syncVersion: Long = 0L,
    @SerializedName("deleted") val deleted: Boolean = false
)

data class SyncAppConfig(
    @SerializedName(value = "packageName", alternate = ["package_name"]) val packageName: String = "",
    @SerializedName(value = "appName", alternate = ["app_name"]) val appName: String = "",
    @SerializedName(value = "iconUri", alternate = ["icon_uri"]) val iconUri: String? = null,
    @SerializedName(value = "isMonitored", alternate = ["is_monitored"]) val isMonitored: Boolean = false,
    @SerializedName(value = "createdAt", alternate = ["created_at"]) val createdAt: Long = 0L,
    @SerializedName(value = "lastUsed", alternate = ["last_used"]) val lastUsed: Long = 0L,
    @SerializedName(value = "tenantId", alternate = ["tenant_id"]) val tenantId: String = "",
    @SerializedName(value = "syncVersion", alternate = ["sync_version"]) val syncVersion: Long = 0L,
    @SerializedName("deleted") val deleted: Boolean = false
)

data class SyncScenario(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("name") val name: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName(value = "targetId", alternate = ["target_id"]) val targetId: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName(value = "createdAt", alternate = ["created_at"]) val createdAt: Long = 0L,
    @SerializedName(value = "tenantId", alternate = ["tenant_id"]) val tenantId: String = "",
    @SerializedName(value = "syncVersion", alternate = ["sync_version"]) val syncVersion: Long = 0L,
    @SerializedName("deleted") val deleted: Boolean = false
)

data class SyncReplyHistory(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName(value = "sourceApp", alternate = ["source_app"]) val sourceApp: String = "",
    @SerializedName(value = "originalMessage", alternate = ["original_message"]) val originalMessage: String = "",
    @SerializedName(value = "generatedReply", alternate = ["generated_reply"]) val generatedReply: String = "",
    @SerializedName(value = "finalReply", alternate = ["final_reply"]) val finalReply: String = "",
    @SerializedName(value = "ruleMatchedId", alternate = ["rule_matched_id"]) val ruleMatchedId: Long? = null,
    @SerializedName(value = "modelUsedId", alternate = ["model_used_id"]) val modelUsedId: Long? = null,
    @SerializedName(value = "styleApplied", alternate = ["style_applied"]) val styleApplied: Boolean = false,
    @SerializedName(value = "sendTime", alternate = ["send_time"]) val sendTime: Long = 0L,
    @SerializedName("modified") val modified: Boolean = false,
    @SerializedName(value = "tenantId", alternate = ["tenant_id"]) val tenantId: String = "",
    @SerializedName(value = "syncVersion", alternate = ["sync_version"]) val syncVersion: Long = 0L,
    @SerializedName("deleted") val deleted: Boolean = false
)

data class SyncMessageBlacklist(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("type") val type: String = "",
    @SerializedName("value") val value: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName(value = "packageName", alternate = ["package_name"]) val packageName: String? = null,
    @SerializedName(value = "createdAt", alternate = ["created_at"]) val createdAt: Long = 0L,
    @SerializedName(value = "isEnabled", alternate = ["is_enabled"]) val isEnabled: Boolean = true,
    @SerializedName(value = "tenantId", alternate = ["tenant_id"]) val tenantId: String = "",
    @SerializedName(value = "syncVersion", alternate = ["sync_version"]) val syncVersion: Long = 0L,
    @SerializedName("deleted") val deleted: Boolean = false
)
