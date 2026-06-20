package com.csbaby.kefu.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "keyword_rules",
    indices = [Index(value = ["tenantId", "remoteId"], unique = true, name = "index_keyword_rules_tenantId_remoteId")]
)
data class KeywordRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keyword: String,
    val matchType: String, // EXACT, CONTAINS, REGEX
    val replyTemplate: String,
    val category: String,
    val targetType: String = "ALL",
    val targetNamesJson: String = "[]",
    val priority: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val tenantId: String = DEFAULT_TENANT_ID,  // 保留默认值用于旧数据兼容
    val syncVersion: Long = 0L,
    val deleted: Boolean = false,
    /**
     * 服务端唯一 id: "${tenantId}_${localId}" 形式.
     * 用于跨租户同步避免 id 冲突.
     * pull 时通过 ON CONFLICT (tenantId, remoteId) upsert, push 时作为服务端 pkey.
     * 本地 Room 主键仍是 Long autogenerate (UI 兼容).
     */
    val remoteId: String? = null
) {
    companion object {
        const val DEFAULT_TENANT_ID = "default_tenant"
    }
}

