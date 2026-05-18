package com.kefu.xiaomi.data.local.dao

import androidx.room.*
import com.kefu.xiaomi.data.local.entity.AIModelConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AIModelConfigDao {
    @Query("SELECT * FROM ai_model_configs")
    fun getAll(): Flow<List<AIModelConfigEntity>>

    @Query("SELECT * FROM ai_model_configs WHERE isEnabled = 1")
    fun getEnabled(): Flow<List<AIModelConfigEntity>>

    @Query("SELECT * FROM ai_model_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): AIModelConfigEntity?

    @Query("SELECT * FROM ai_model_configs WHERE id = :id")
    suspend fun getById(id: Long): AIModelConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: AIModelConfigEntity): Long

    @Update
    suspend fun update(config: AIModelConfigEntity)

    @Delete
    suspend fun delete(config: AIModelConfigEntity)

    @Query("DELETE FROM ai_model_configs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE ai_model_configs SET isDefault = 0 WHERE isDefault = 1")
    suspend fun clearDefault()

    @Transaction
    suspend fun setAsDefault(id: Long) {
        clearDefault()
        getById(id)?.let {
            update(it.copy(isDefault = true))
        }
    }
}
