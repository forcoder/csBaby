package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.ScenarioDao
import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.domain.model.Scenario
import com.csbaby.kefu.domain.repository.ScenarioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScenarioRepositoryImpl @Inject constructor(
    private val scenarioDao: ScenarioDao,
    private val syncManager: SyncManager
) : ScenarioRepository {

    override fun getAllScenarios(): Flow<List<Scenario>> {
        return scenarioDao.getAllScenarios().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getScenarioById(id: Long): Scenario? {
        return scenarioDao.getScenarioById(id)?.toDomain()
    }

    override suspend fun insertScenario(scenario: Scenario): Long {
        val id = scenarioDao.insertScenario(scenario.toEntity())
        syncManager.triggerSync()
        return id
    }

    override suspend fun updateScenario(scenario: Scenario) {
        scenarioDao.updateScenario(scenario.toEntity().copy(syncVersion = 0L))
        syncManager.triggerSync()
    }

    override suspend fun deleteScenario(id: Long) {
        val entity = scenarioDao.getScenarioById(id) ?: return
        scenarioDao.deleteScenario(entity)
        syncManager.triggerSync()
    }
}
