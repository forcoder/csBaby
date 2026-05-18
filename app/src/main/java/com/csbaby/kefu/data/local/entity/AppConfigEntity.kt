package com.csbaby.kefu.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_configs")
data class AppConfigEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val iconUri: String?,
    val isMonitored: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis(),
    val tenantId: String = DEFAULT_TENANT_ID,
    val syncVersion: Long = 0L,
    val deleted: Boolean = false
) {
    companion object {
        const val DEFAULT_TENANT_ID = "default_tenant"
    }
}
