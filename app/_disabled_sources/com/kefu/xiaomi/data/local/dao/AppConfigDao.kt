package com.kefu.xiaomi.data.local.dao

import androidx.room.*
import com.kefu.xiaomi.data.local.entity.AppConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppConfigDao {
    @Query("SELECT * FROM app_configs")
    fun getAll(): Flow<List<AppConfigEntity>>

    @Query("SELECT * FROM app_configs WHERE isMonitored = 1")
    fun getMonitored(): Flow<List<AppConfigEntity>>

    @Query("SELECT * FROM app_configs WHERE packageName = :packageName")
    suspend fun getByPackageName(packageName: String): AppConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(appConfig: AppConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(appConfigs: List<AppConfigEntity>)

    @Update
    suspend fun update(appConfig: AppConfigEntity)

    @Delete
    suspend fun delete(appConfig: AppConfigEntity)

    @Query("DELETE FROM app_configs WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)
}
