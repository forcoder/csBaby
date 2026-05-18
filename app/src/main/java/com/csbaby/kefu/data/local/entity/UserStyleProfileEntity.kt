package com.csbaby.kefu.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_style_profiles")
data class UserStyleProfileEntity(
    @PrimaryKey
    val userId: String,
    val formalityLevel: Float = 0.5f,
    val enthusiasmLevel: Float = 0.5f,
    val professionalismLevel: Float = 0.5f,
    val wordCountPreference: Int = 50,
    val commonPhrases: String = "", // JSON array string
    val avoidPhrases: String = "", // JSON array string
    val learningSamples: Int = 0,
    val accuracyScore: Float = 0.0f,
    val lastTrained: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val tenantId: String = DEFAULT_TENANT_ID,
    val syncVersion: Long = 0L,
    val deleted: Boolean = false
) {
    companion object {
        const val DEFAULT_TENANT_ID = "default_tenant"
    }
}
