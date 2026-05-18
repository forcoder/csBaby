package com.csbaby.kefu.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reply_history")
data class ReplyHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceApp: String,
    val originalMessage: String,
    val generatedReply: String,
    val finalReply: String,
    val ruleMatchedId: Long?,
    val modelUsedId: Long?,
    val styleApplied: Boolean = false,
    val sendTime: Long = System.currentTimeMillis(),
    val modified: Boolean = false,
    val tenantId: String = DEFAULT_TENANT_ID,
    val syncVersion: Long = 0L,
    val deleted: Boolean = false
) {
    companion object {
        const val DEFAULT_TENANT_ID = "default_tenant"
    }
}
