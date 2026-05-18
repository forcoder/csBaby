package com.kefu.xiaomi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kefu.xiaomi.data.model.MatchType

@Entity(tableName = "keyword_rules")
data class KeywordRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val matchType: MatchType,
    val replyTemplate: String,
    val category: String,
    val applicableScenarios: List<String> = emptyList(),
    val priority: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
