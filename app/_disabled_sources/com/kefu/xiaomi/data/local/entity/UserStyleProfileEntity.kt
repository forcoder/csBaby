package com.kefu.xiaomi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_style_profiles")
data class UserStyleProfileEntity(
    @PrimaryKey val userId: String,
    val formalityLevel: Float = 0.5f,
    val enthusiasmLevel: Float = 0.5f,
    val professionalismLevel: Float = 0.5f,
    val wordCountPreference: Int = 50,
    val commonPhrases: List<String> = emptyList(),
    val avoidPhrases: List<String> = emptyList(),
    val learningSamples: Int = 0,
    val accuracyScore: Float = 0.0f,
    val lastTrained: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
