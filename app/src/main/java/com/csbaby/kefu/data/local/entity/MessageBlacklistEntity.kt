package com.csbaby.kefu.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 消息黑名单实体
 * 用于过滤不需要监听的消息
 */
@Entity(tableName = "message_blacklist")
data class MessageBlacklistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 黑名单类型：KEYWORD（关键词）、SENDER（发送者）、CONTENT（内容）
    val type: String,
    
    // 黑名单值
    val value: String,
    
    // 描述说明
    val description: String = "",
    
    // 来源包名（可选，用于特定应用）
    val packageName: String? = null,
    
    // 创建时间
    val createdAt: Long = System.currentTimeMillis(),
    
    // 是否启用
    val isEnabled: Boolean = true
) {
    companion object {
        const val TYPE_KEYWORD = "KEYWORD"
        const val TYPE_SENDER = "SENDER"
        const val TYPE_CONTENT = "CONTENT"
    }
}
