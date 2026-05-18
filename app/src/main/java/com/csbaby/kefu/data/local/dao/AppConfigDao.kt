package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.AppConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppConfigDao {
    @Query("SELECT * FROM app_configs ORDER BY lastUsed DESC")
    fun getAllApps(): Flow<List<AppConfigEntity>>

    @Query("SELECT * FROM app_configs WHERE isMonitored = 1")
    fun getMonitoredApps(): Flow<List<AppConfigEntity>>

    @Query("SELECT * FROM app_configs WHERE packageName = :packageName")
    suspend fun getAppByPackage(packageName: String): AppConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: AppConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<AppConfigEntity>)

    @Update
    suspend fun updateApp(app: AppConfigEntity)

    @Query("UPDATE app_configs SET isMonitored = :isMonitored WHERE packageName = :packageName")
    suspend fun updateMonitorStatus(packageName: String, isMonitored: Boolean)

    @Query("UPDATE app_configs SET lastUsed = :timestamp WHERE packageName = :packageName")
    suspend fun updateLastUsed(packageName: String, timestamp: Long)

    @Delete
    suspend fun deleteApp(app: AppConfigEntity)

    @Query("DELETE FROM app_configs WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)

    // ========== 租户感知查询（同步用） ==========

    @Query("SELECT * FROM app_configs WHERE tenantId = :tenantId ORDER BY lastUsed DESC")
    fun getAppsByTenant(tenantId: String): Flow<List<AppConfigEntity>>

    @Query("SELECT * FROM app_configs WHERE tenantId = :tenantId ORDER BY lastUsed DESC")
    suspend fun getAppsByTenantSync(tenantId: String): List<AppConfigEntity>

    @Query("UPDATE app_configs SET syncVersion = :version WHERE packageName = :packageName")
    suspend fun updateSyncVersion(packageName: String, version: Long)
}
