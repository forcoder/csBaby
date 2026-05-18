package com.csbaby.kefu.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_model_configs")
data class AIModelConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val modelType: String, // OPENAI, CLAUDE, ZHIPU, TONGYI, CUSTOM
    val modelName: String,
    val apiKey: String,
    val apiEndpoint: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1000,
    val isDefault: Boolean = false,
    val isEnabled: Boolean = true,
    val monthlyCost: Double = 0.0,
    val lastUsed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val tenantId: String = DEFAULT_TENANT_ID,
    val syncVersion: Long = 0L,
    val deleted: Boolean = false
) {
    companion object {
        const val DEFAULT_TENANT_ID = "default_tenant"
    }
}
