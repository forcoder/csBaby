package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.ScenarioDao
import com.csbaby.kefu.domain.model.Scenario
import com.csbaby.kefu.domain.repository.ScenarioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScenarioRepositoryImpl @Inject constructor(
    private val scenarioDao: ScenarioDao
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
        return scenarioDao.insertScenario(scenario.toEntity())
    }

    override suspend fun updateScenario(scenario: Scenario) {
        scenarioDao.updateScenario(scenario.toEntity())
    }

    override suspend fun deleteScenario(id: Long) {
        scenarioDao.deleteScenario(scenarioDao.getScenarioById(id)!!)
    }
}
