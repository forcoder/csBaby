package com.kefu.xiaomi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_configs")
data class AppConfigEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val iconUri: String?,
    val isMonitored: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis()
)
