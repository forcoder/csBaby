package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.AIModelConfigDao
import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.repository.AIModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIModelRepositoryImpl @Inject constructor(
    private val aiModelConfigDao: AIModelConfigDao,
    private val syncManager: SyncManager
) : AIModelRepository {

    override fun getAllModels(): Flow<List<AIModelConfig>> {
        return aiModelConfigDao.getAllModels().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getEnabledModels(): Flow<List<AIModelConfig>> {
        return aiModelConfigDao.getEnabledModels().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getDefaultModel(): AIModelConfig? {
        return aiModelConfigDao.getDefaultModel()?.toDomain()
    }

    override suspend fun getModelById(id: Long): AIModelConfig? {
        return aiModelConfigDao.getModelById(id)?.toDomain()
    }

    override suspend fun insertModel(model: AIModelConfig): Long {
        if (model.isDefault) {
            aiModelConfigDao.clearDefaultModel()
        }
        val id = aiModelConfigDao.insertModel(model.toEntity())
        syncManager.triggerSync()
        return id
    }

    override suspend fun updateModel(model: AIModelConfig) {
        if (model.isDefault) {
            aiModelConfigDao.clearDefaultModel()
        }
        aiModelConfigDao.updateModel(model.toEntity())
        syncManager.triggerSync()
    }

    override suspend fun deleteModel(id: Long) {
        aiModelConfigDao.deleteById(id)
        syncManager.triggerSync()
    }

    override suspend fun setDefaultModel(id: Long) {
        aiModelConfigDao.clearDefaultModel()
        aiModelConfigDao.setDefaultModel(id)
        syncManager.triggerSync()
    }

    override suspend fun updateLastUsed(id: Long) {
        aiModelConfigDao.updateLastUsed(id, System.currentTimeMillis())
    }
    // 注意：updateLastUsed 高频调用，不触发同步，由兜底 Worker 处理

    override suspend fun addCost(id: Long, cost: Double) {
        aiModelConfigDao.addCost(id, cost)
    }
}
