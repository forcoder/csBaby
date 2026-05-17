package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.AIModelConfigDao
import com.csbaby.kefu.data.remote.CsbabyApiService
import com.csbaby.kefu.data.remote.UserAuthManager
import com.csbaby.kefu.data.remote.dto.toDomain as dtoToDomain
import com.csbaby.kefu.data.remote.dto.toDto as domainToDto
import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.repository.AIModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIModelRepositoryImpl @Inject constructor(
    private val aiModelConfigDao: AIModelConfigDao,
    private val apiService: CsbabyApiService,
    private val userAuthManager: UserAuthManager
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
        return try {
            userAuthManager.ensureAuthenticated()
            val dto = model.domainToDto()
            val response = apiService.createModel(dto)
            val serverId = response.id.toLong()
            val id = aiModelConfigDao.insertModel(model.copy(id = serverId).toEntity())
            Timber.d("Model created on server: id=$serverId")
            id
        } catch (e: Exception) {
            Timber.w(e, "Failed to create model on server, inserting locally")
            aiModelConfigDao.insertModel(model.toEntity())
        }
    }

    override suspend fun updateModel(model: AIModelConfig) {
        try {
            userAuthManager.ensureAuthenticated()
            apiService.updateModel(model.id.toInt(), model.domainToDto())
            Timber.d("Model updated on server: id=${model.id}")
        } catch (e: Exception) {
            Timber.w(e, "Failed to update model on server, updating locally")
        }
        if (model.isDefault) {
            aiModelConfigDao.clearDefaultModel()
        }
        aiModelConfigDao.updateModel(model.toEntity())
    }

    override suspend fun deleteModel(id: Long) {
        try {
            userAuthManager.ensureAuthenticated()
            apiService.deleteModel(id.toInt())
            Timber.d("Model deleted on server: id=$id")
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete model on server, deleting locally")
        }
        aiModelConfigDao.deleteById(id)
    }

    override suspend fun setDefaultModel(id: Long) {
        aiModelConfigDao.clearDefaultModel()
        aiModelConfigDao.setDefaultModel(id)
        // Server-side default model sync is optional; local is sufficient for now
    }

    override suspend fun updateLastUsed(id: Long) {
        aiModelConfigDao.updateLastUsed(id, System.currentTimeMillis())
    }

    override suspend fun addCost(id: Long, cost: Double) {
        aiModelConfigDao.addCost(id, cost)
    }

    /**
     * Sync models from server to local cache.
     * Builds new data first, then atomically replaces local cache.
     */
    suspend fun syncFromServer(): Result<Int> {
        return try {
            userAuthManager.ensureAuthenticated()
            val remoteModels = apiService.getModels()

            // Build new entities first before touching local DB
            val newEntities = remoteModels.map { dto ->
                dto.dtoToDomain().toEntity()
            }

            // Atomic replace
            aiModelConfigDao.deleteAllModels()
            for (entity in newEntities) {
                aiModelConfigDao.insertModel(entity)
            }

            Timber.i("Models synced from server: ${newEntities.size} models")
            Result.success(newEntities.size)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync models from server")
            Result.failure(e)
        }
    }

    suspend fun forceRefresh(): Result<Int> = syncFromServer()
}
