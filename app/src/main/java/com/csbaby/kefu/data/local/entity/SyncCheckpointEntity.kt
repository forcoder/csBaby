package com.csbaby.kefu.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_checkpoints")
data class SyncCheckpointEntity(
    @PrimaryKey
    val tenantId: String,
    val lastSyncTime: Long = 0L,
    val syncToken: String? = null,
    val isSyncing: Boolean = false,
    val lastError: String? = null
)
