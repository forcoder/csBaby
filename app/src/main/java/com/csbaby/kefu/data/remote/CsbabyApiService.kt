package com.csbaby.kefu.data.remote

import com.csbaby.kefu.data.remote.dto.*
import retrofit2.http.*

/**
 * csBaby 后端 API 服务接口
 * 对应 app.py 中所有数据相关端点
 */
interface CsbabyApiService {

    // ========== 认证 ==========

    @POST("api/auth/register")
    suspend fun register(@Body req: RegisterRequest): RegisterResponse

    @POST("api/auth/user/register")
    suspend fun userRegister(@Body req: UserRegisterRequest): AuthResponse

    @POST("api/auth/user/login")
    suspend fun userLogin(@Body req: UserLoginRequest): AuthResponse

    @POST("api/auth/user/change_password")
    suspend fun changePassword(@Body req: ChangePasswordRequest): ChangePasswordResponse

    @POST("api/auth/heartbeat")
    suspend fun heartbeat()

    // ========== 知识库规则 ==========

    @GET("api/rules")
    suspend fun getRules(): List<RuleDto>

    @POST("api/rules")
    suspend fun createRule(@Body rule: RuleDto): RuleDto

    @GET("api/rules/{id}")
    suspend fun getRule(@Path("id") id: Int): RuleDto

    @PUT("api/rules/{id}")
    suspend fun updateRule(@Path("id") id: Int, @Body rule: RuleDto): RuleDto

    @DELETE("api/rules/{id}")
    suspend fun deleteRule(@Path("id") id: Int)

    @POST("api/rules/batch")
    suspend fun batchImportRules(@Body req: RuleBatchRequest): BatchImportResponse

    // ========== 模型配置 ==========

    @GET("api/models")
    suspend fun getModels(): List<ModelConfigDto>

    @POST("api/models")
    suspend fun createModel(@Body model: ModelConfigDto): ModelConfigDto

    @GET("api/models/{id}")
    suspend fun getModel(@Path("id") id: Int): ModelConfigDto

    @PUT("api/models/{id}")
    suspend fun updateModel(@Path("id") id: Int, @Body model: ModelConfigDto): ModelConfigDto

    @DELETE("api/models/{id}")
    suspend fun deleteModel(@Path("id") id: Int)

    @POST("api/models/{id}/test")
    suspend fun testModel(@Path("id") id: Int): ModelTestResponse

    // ========== AI 生成 ==========

    @POST("api/ai/generate")
    suspend fun generateReply(@Body req: GenerateRequest): GenerateResponse

    @POST("api/ai/chat")
    suspend fun chat(@Body req: ChatRequest): ChatResponse

    // ========== 历史记录 ==========

    @GET("api/history")
    suspend fun getHistory(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): HistoryPageResponse

    @POST("api/history")
    suspend fun recordHistory(@Body entry: HistoryEntryDto): HistoryEntryDto

    // ========== 反馈 ==========

    @GET("api/feedback")
    suspend fun getFeedback(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): List<FeedbackDto>

    @POST("api/feedback")
    suspend fun submitFeedback(@Body feedback: FeedbackDto): FeedbackDto

    // ========== 优化指标 ==========

    @GET("api/optimize/metrics")
    suspend fun getMetrics(@Query("days") days: Int = 7): List<MetricsDto>

    @POST("api/optimize/analyze")
    suspend fun analyze(): AnalysisResponse

    // ========== 备份 ==========

    @GET("api/backup")
    suspend fun exportBackup(): BackupDto

    @POST("api/backup/restore")
    suspend fun restoreBackup(@Body req: RestoreRequest): RestoreResponse
}
