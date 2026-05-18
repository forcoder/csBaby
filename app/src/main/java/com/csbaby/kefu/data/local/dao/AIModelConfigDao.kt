package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.AIModelConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AIModelConfigDao {
    @Query("SELECT * FROM ai_model_configs ORDER BY isDefault DESC, lastUsed DESC")
    fun getAllModels(): Flow<List<AIModelConfigEntity>>

    @Query("SELECT * FROM ai_model_configs WHERE isEnabled = 1 ORDER BY isDefault DESC, lastUsed DESC")
    fun getEnabledModels(): Flow<List<AIModelConfigEntity>>

    @Query("SELECT * FROM ai_model_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultModel(): AIModelConfigEntity?

    @Query("SELECT * FROM ai_model_configs WHERE id = :id")
    suspend fun getModelById(id: Long): AIModelConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: AIModelConfigEntity): Long

    @Update
    suspend fun updateModel(model: AIModelConfigEntity)

    @Delete
    suspend fun deleteModel(model: AIModelConfigEntity)

    @Query("DELETE FROM ai_model_configs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE ai_model_configs SET isDefault = 0 WHERE isDefault = 1")
    suspend fun clearDefaultModel()

    @Query("UPDATE ai_model_configs SET isDefault = 1 WHERE id = :id")
    suspend fun setDefaultModel(id: Long)

    @Query("UPDATE ai_model_configs SET lastUsed = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: Long, timestamp: Long)

    @Query("UPDATE ai_model_configs SET monthlyCost = monthlyCost + :cost WHERE id = :id")
    suspend fun addCost(id: Long, cost: Double)

    // ========== 租户感知查询（同步用） ==========

    @Query("SELECT * FROM ai_model_configs WHERE tenantId = :tenantId ORDER BY isDefault DESC, lastUsed DESC")
    fun getModelsByTenant(tenantId: String): Flow<List<AIModelConfigEntity>>

    @Query("SELECT * FROM ai_model_configs WHERE tenantId = :tenantId ORDER BY isDefault DESC, lastUsed DESC")
    suspend fun getModelsByTenantSync(tenantId: String): List<AIModelConfigEntity>

    @Query("UPDATE ai_model_configs SET syncVersion = :version WHERE id = :id")
    suspend fun updateSyncVersion(id: Long, version: Long)
}
