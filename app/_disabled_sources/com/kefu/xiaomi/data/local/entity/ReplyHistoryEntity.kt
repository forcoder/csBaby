package com.kefu.xiaomi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kefu.xiaomi.data.model.ReplySource

@Entity(tableName = "reply_history")
data class ReplyHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceApp: String,
    val originalMessage: String,
    val generatedReply: String,
    val finalReply: String,
    val ruleMatchedId: Long?,
    val modelUsedId: Long?,
    val styleApplied: Boolean = false,
    val sendTime: Long = System.currentTimeMillis(),
    val modified: Boolean = false
)
