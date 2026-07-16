package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.KeywordRuleDao
import com.csbaby.kefu.data.local.dao.ScenarioDao
import com.csbaby.kefu.data.local.entity.KeywordRuleEntity
import com.csbaby.kefu.data.local.entity.RuleScenarioCrossRef
import com.csbaby.kefu.data.sync.AuthManager
import com.csbaby.kefu.data.sync.SyncManager
import androidx.room.Transaction
import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.domain.repository.KeywordRuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeywordRuleRepositoryImpl @Inject constructor(
    private val keywordRuleDao: KeywordRuleDao,
    private val scenarioDao: ScenarioDao,
    private val syncManager: SyncManager,
    private val authManager: AuthManager
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
        val tenantId = authManager.currentTenantId()
            ?: throw IllegalStateException("未登录，无法创建知识库规则")
        val entityWithTenant = rule.toEntity().copy(
            tenantId = tenantId,
            // 新数据 syncVersion 设为 0，让服务器分配新 ID
            syncVersion = 0L
        )
        val id = keywordRuleDao.insertRule(entityWithTenant)
        if (rule.applicableScenarios.isNotEmpty()) {
            rule.applicableScenarios.forEach { scenarioId ->
                scenarioDao.insertRuleScenarioRelation(RuleScenarioCrossRef(id, scenarioId, tenantId))
            }
        }
        syncManager.triggerSync()
        return id
    }

    override suspend fun updateRule(rule: KeywordRule) {
        val tenantId = authManager.currentTenantId()
            ?: throw IllegalStateException("未登录，无法更新知识库规则")
        keywordRuleDao.updateRule(rule.toEntity().copy(tenantId = tenantId, syncVersion = 0L))
        scenarioDao.deleteRelationsForRule(rule.id)
        rule.applicableScenarios.forEach { scenarioId ->
            scenarioDao.insertRuleScenarioRelation(RuleScenarioCrossRef(rule.id, scenarioId, tenantId))
        }
        syncManager.triggerSync()
    }

    @Transaction
    override suspend fun deleteRule(id: Long): Result<Unit> = runCatching {
        val rule = keywordRuleDao.getById(id)
        if (rule == null) {
            throw Exception("规则不存在 (id=$id)")
        }
        scenarioDao.deleteRelationsForRule(id)
        val affectedRows = keywordRuleDao.softDelete(id)
        if (affectedRows == 0) {
            throw Exception("删除失败：未找到匹配的规则")
        }
        syncManager.triggerSync(rule.tenantId)
    }

    override suspend fun deleteAllRules() {
        scenarioDao.deleteAllRelations()
        keywordRuleDao.deleteAllRules()
    }

    override suspend fun getRuleCount(): Int = keywordRuleDao.getRuleCount()

    override fun getRuleCountFlow(): Flow<Int> = keywordRuleDao.getRuleCountFlow()

    override suspend fun getScenariosForRule(ruleId: Long): List<Long> {
        return scenarioDao.getScenarioIdsForRule(ruleId)
    }

    override suspend fun updateRuleScenarios(ruleId: Long, scenarioIds: List<Long>) {
        val tenantId = authManager.currentTenantId()
            ?: throw IllegalStateException("未登录，无法更新规则场景")
        scenarioDao.deleteRelationsForRule(ruleId)
        scenarioIds.forEach { scenarioId ->
            scenarioDao.insertRuleScenarioRelation(RuleScenarioCrossRef(ruleId, scenarioId, tenantId))
        }
        syncManager.triggerSync()
    }
}
