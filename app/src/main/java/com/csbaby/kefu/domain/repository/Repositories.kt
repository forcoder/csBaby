package com.csbaby.kefu.domain.repository

import com.csbaby.kefu.domain.model.*
import kotlinx.coroutines.flow.Flow

interface AppConfigRepository {
    fun getAllApps(): Flow<List<AppConfig>>
    fun getMonitoredApps(): Flow<List<AppConfig>>
    suspend fun getAppByPackage(packageName: String): AppConfig?
    suspend fun insertApp(app: AppConfig)
    suspend fun insertApps(apps: List<AppConfig>)
    suspend fun updateApp(app: AppConfig)
    suspend fun updateMonitorStatus(packageName: String, isMonitored: Boolean)
    suspend fun deleteApp(packageName: String)
}

interface KeywordRuleRepository {
    fun getAllRules(): Flow<List<KeywordRule>>
    fun getEnabledRules(): Flow<List<KeywordRule>>
    fun getRulesByCategory(category: String): Flow<List<KeywordRule>>
    fun getAllCategories(): Flow<List<String>>
    suspend fun getRuleById(id: Long): KeywordRule?
    suspend fun searchByKeyword(keyword: String): List<KeywordRule>
    suspend fun insertRule(rule: KeywordRule): Long
    suspend fun updateRule(rule: KeywordRule)
    suspend fun deleteRule(id: Long)
    suspend fun deleteAllRules()
    suspend fun getRuleCount(): Int
    fun getRuleCountFlow(): Flow<Int>

    suspend fun getScenariosForRule(ruleId: Long): List<Long>

    suspend fun updateRuleScenarios(ruleId: Long, scenarioIds: List<Long>)
}

interface ScenarioRepository {
    fun getAllScenarios(): Flow<List<Scenario>>
    suspend fun getScenarioById(id: Long): Scenario?
    suspend fun insertScenario(scenario: Scenario): Long
    suspend fun updateScenario(scenario: Scenario)
    suspend fun deleteScenario(id: Long)
}

interface AIModelRepository {
    fun getAllModels(): Flow<List<AIModelConfig>>
    fun getEnabledModels(): Flow<List<AIModelConfig>>
    suspend fun getDefaultModel(): AIModelConfig?
    suspend fun getModelById(id: Long): AIModelConfig?
    suspend fun insertModel(model: AIModelConfig): Long
    suspend fun updateModel(model: AIModelConfig)
    suspend fun deleteModel(id: Long)
    suspend fun setDefaultModel(id: Long)
    suspend fun updateLastUsed(id: Long)
    suspend fun addCost(id: Long, cost: Double)
}

interface UserStyleRepository {
    fun getProfile(userId: String): Flow<UserStyleProfile?>
    suspend fun getProfileSync(userId: String): UserStyleProfile?
    suspend fun saveProfile(profile: UserStyleProfile)
    suspend fun updateProfile(profile: UserStyleProfile)
    suspend fun updateFormalityLevel(userId: String, formality: Float)
    suspend fun updateEnthusiasmLevel(userId: String, enthusiasm: Float)
    suspend fun updateProfessionalismLevel(userId: String, professionalism: Float)
    suspend fun incrementLearningSamples(userId: String)
    suspend fun updateAccuracyScore(userId: String, score: Float)
}

interface ReplyHistoryRepository {
    fun getRecentReplies(limit: Int = 20): Flow<List<ReplyHistory>>
    fun getRepliesByApp(appPackage: String, limit: Int = 20): Flow<List<ReplyHistory>>
    suspend fun getReplyById(id: Long): ReplyHistory?
    suspend fun insertReply(reply: ReplyHistory): Long
    suspend fun updateFinalReply(id: Long, finalReply: String)
    suspend fun deleteReply(id: Long)
    suspend fun getStyleAppliedReplies(limit: Int = 100): List<ReplyHistory>
    suspend fun getTotalCount(): Int
    suspend fun getModifiedCount(): Int
}
