package com.kefu.xiaomi.data.local.dao

import androidx.room.*
import com.kefu.xiaomi.data.local.entity.ScenarioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScenarioDao {
    @Query("SELECT * FROM scenarios")
    fun getAll(): Flow<List<ScenarioEntity>>

    @Query("SELECT * FROM scenarios WHERE id = :id")
    suspend fun getById(id: Long): ScenarioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scenario: ScenarioEntity): Long

    @Update
    suspend fun update(scenario: ScenarioEntity)

    @Delete
    suspend fun delete(scenario: ScenarioEntity)

    @Query("DELETE FROM scenarios WHERE id = :id")
    suspend fun deleteById(id: Long)
}
