package com.kefu.xiaomi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kefu.xiaomi.data.model.ModelType

@Entity(tableName = "ai_model_configs")
data class AIModelConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modelType: ModelType,
    val modelName: String,
    val apiKey: String,
    val apiEndpoint: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1000,
    val isDefault: Boolean = false,
    val isEnabled: Boolean = true,
    val monthlyCost: Double = 0.0,
    val lastUsed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
