package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.SyncCheckpointEntity

@Dao
interface SyncCheckpointDao {
    @Query("SELECT * FROM sync_checkpoints WHERE tenantId = :tenantId")
    suspend fun getCheckpoint(tenantId: String): SyncCheckpointEntity?

    @Query("SELECT * FROM sync_checkpoints WHERE tenantId = :tenantId")
    fun getCheckpointFlow(tenantId: String): kotlinx.coroutines.flow.Flow<SyncCheckpointEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkpoint: SyncCheckpointEntity)

    @Query("UPDATE sync_checkpoints SET lastSyncTime = :timestamp, syncToken = :token, isSyncing = 0, lastError = NULL WHERE tenantId = :tenantId")
    suspend fun updateSyncSuccess(tenantId: String, timestamp: Long, token: String?)

    @Query("UPDATE sync_checkpoints SET isSyncing = :syncing WHERE tenantId = :tenantId")
    suspend fun updateSyncing(tenantId: String, syncing: Boolean)

    @Query("UPDATE sync_checkpoints SET lastError = :error WHERE tenantId = :tenantId")
    suspend fun updateLastError(tenantId: String, error: String?)
}
