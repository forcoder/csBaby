package com.kefu.xiaomi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kefu.xiaomi.data.model.ScenarioType

@Entity(tableName = "scenarios")
data class ScenarioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ScenarioType,
    val targetId: String?,
    val description: String?,
    val createdAt: Long = System.currentTimeMillis()
)
