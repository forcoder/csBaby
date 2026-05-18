package com.csbaby.kefu.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scenarios")
data class ScenarioEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String, // ALL_PROPERTIES, SPECIFIC_PROPERTY, PRODUCT
    val targetId: String?,
    val description: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val tenantId: String = DEFAULT_TENANT_ID,
    val syncVersion: Long = 0L,
    val deleted: Boolean = false
) {
    companion object {
        const val DEFAULT_TENANT_ID = "default_tenant"
    }
}
