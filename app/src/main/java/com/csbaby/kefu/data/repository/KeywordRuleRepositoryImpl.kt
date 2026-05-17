package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.KeywordRuleDao
import com.csbaby.kefu.data.local.dao.ScenarioDao
import com.csbaby.kefu.data.local.entity.RuleScenarioCrossRef
import com.csbaby.kefu.data.remote.CsbabyApiService
import com.csbaby.kefu.data.remote.UserAuthManager
import com.csbaby.kefu.data.remote.dto.toDomain as dtoToDomain
import com.csbaby.kefu.data.remote.dto.toDto as domainToDto
import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.domain.repository.KeywordRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeywordRuleRepositoryImpl @Inject constructor(
    private val keywordRuleDao: KeywordRuleDao,
    private val scenarioDao: ScenarioDao,
    private val apiService: CsbabyApiService,
    private val userAuthManager: UserAuthManager
) : KeywordRuleRepository {

    override fun getAllRules(): Flow<List<KeywordRule>> {
        return keywordRuleDao.getAllRules().map { entities ->
            entities.map { entity ->
                val scenarios = scenarioDao.getScenarioIdsForRule(entity.id)
                entity.toDomain(scenarios)
            }
        }
    }

    override fun getEnabledRules(): Flow<List<KeywordRule>> {
        return keywordRuleDao.getEnabledRules().map { entities ->
            entities.map { entity ->
                val scenarios = scenarioDao.getScenarioIdsForRule(entity.id)
                entity.toDomain(scenarios)
            }
        }
    }

    override fun getRulesByCategory(category: String): Flow<List<KeywordRule>> {
        return keywordRuleDao.getRulesByCategory(category).map { entities ->
            entities.map { entity ->
                val scenarios = scenarioDao.getScenarioIdsForRule(entity.id)
                entity.toDomain(scenarios)
            }
        }
    }

    override fun getAllCategories(): Flow<List<String>> = keywordRuleDao.getAllCategories()

    override suspend fun getRuleById(id: Long): KeywordRule? {
        return keywordRuleDao.getRuleById(id)?.let { entity ->
            val scenarios = scenarioDao.getScenarioIdsForRule(entity.id)
            entity.toDomain(scenarios)
        }
    }

    override suspend fun searchByKeyword(keyword: String): List<KeywordRule> {
        return keywordRuleDao.searchByKeyword(keyword).map { entity ->
            val scenarios = scenarioDao.getScenarioIdsForRule(entity.id)
            entity.toDomain(scenarios)
        }
    }

    override suspend fun insertRule(rule: KeywordRule): Long {
        // Remote-first: try API first
        return try {
            userAuthManager.ensureAuthenticated()
            val dto = rule.domainToDto()
            val response = apiService.createRule(dto)
            val serverId = response.id.toLong()
            // Insert locally with server-assigned ID
            val id = keywordRuleDao.insertRule(rule.copy(id = serverId).toEntity())
            // Update scenario relations
            if (rule.applicableScenarios.isNotEmpty()) {
                rule.applicableScenarios.forEach { scenarioId ->
                    scenarioDao.insertRuleScenarioRelation(RuleScenarioCrossRef(id, scenarioId))
                }
            }
            Timber.d("Rule created on server: id=$serverId")
            id
        } catch (e: Exception) {
            Timber.w(e, "Failed to create rule on server, inserting locally")
            val id = keywordRuleDao.insertRule(rule.toEntity())
            if (rule.applicableScenarios.isNotEmpty()) {
                rule.applicableScenarios.forEach { scenarioId ->
                    scenarioDao.insertRuleScenarioRelation(RuleScenarioCrossRef(id, scenarioId))
                }
            }
            id
        }
    }

    override suspend fun updateRule(rule: KeywordRule) {
        try {
            userAuthManager.ensureAuthenticated()
            apiService.updateRule(rule.id.toInt(), rule.domainToDto())
            Timber.d("Rule updated on server: id=${rule.id}")
        } catch (e: Exception) {
            Timber.w(e, "Failed to update rule on server, updating locally")
        }
        keywordRuleDao.updateRule(rule.toEntity())
        scenarioDao.deleteRelationsForRule(rule.id)
        rule.applicableScenarios.forEach { scenarioId ->
            scenarioDao.insertRuleScenarioRelation(RuleScenarioCrossRef(rule.id, scenarioId))
        }
    }

    override suspend fun deleteRule(id: Long) {
        try {
            userAuthManager.ensureAuthenticated()
            apiService.deleteRule(id.toInt())
            Timber.d("Rule deleted on server: id=$id")
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete rule on server, deleting locally")
        }
        scenarioDao.deleteRelationsForRule(id)
        keywordRuleDao.deleteById(id)
    }

    override suspend fun deleteAllRules() {
        scenarioDao.deleteAllRelations()
        keywordRuleDao.deleteAllRules()
        // Note: server-side bulk delete would need a separate endpoint
    }

    override suspend fun getRuleCount(): Int = keywordRuleDao.getRuleCount()

    override fun getRuleCountFlow(): Flow<Int> = keywordRuleDao.getRuleCountFlow()

    override suspend fun getScenariosForRule(ruleId: Long): List<Long> {
        return scenarioDao.getScenarioIdsForRule(ruleId)
    }

    override suspend fun updateRuleScenarios(ruleId: Long, scenarioIds: List<Long>) {
        scenarioDao.deleteRelationsForRule(ruleId)
        scenarioIds.forEach { scenarioId ->
            scenarioDao.insertRuleScenarioRelation(RuleScenarioCrossRef(ruleId, scenarioId))
        }
    }

    /**
     * Sync rules from server to local cache.
     * Builds new data first, then atomically replaces local cache.
     */
    suspend fun syncFromServer(): Result<Int> {
        return try {
            userAuthManager.ensureAuthenticated()
            val remoteRules = apiService.getRules()

            // Build new entities first before touching local DB
            val newEntities = remoteRules.map { dto ->
                dto.dtoToDomain().toEntity()
            }

            // Atomic replace
            scenarioDao.deleteAllRelations()
            keywordRuleDao.deleteAllRules()
            for (entity in newEntities) {
                keywordRuleDao.insertRule(entity)
            }

            Timber.i("Rules synced from server: ${newEntities.size} rules")
            Result.success(newEntities.size)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync rules from server")
            Result.failure(e)
        }
    }

    /**
     * Force refresh: fetch from server and update local cache.
     */
    suspend fun forceRefresh(): Result<Int> = syncFromServer()
}
