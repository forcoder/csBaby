package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.ReplyHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplyHistoryDao {
    @Query("SELECT * FROM reply_history ORDER BY sendTime DESC LIMIT :limit")
    fun getRecentReplies(limit: Int = 20): Flow<List<ReplyHistoryEntity>>

    @Query("SELECT * FROM reply_history WHERE sourceApp = :appPackage ORDER BY sendTime DESC LIMIT :limit")
    fun getRepliesByApp(appPackage: String, limit: Int = 20): Flow<List<ReplyHistoryEntity>>

    @Query("SELECT * FROM reply_history WHERE id = :id")
    suspend fun getReplyById(id: Long): ReplyHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReply(reply: ReplyHistoryEntity): Long

    @Update
    suspend fun updateReply(reply: ReplyHistoryEntity)

    @Query("UPDATE reply_history SET finalReply = :finalReply, modified = 1 WHERE id = :id")
    suspend fun updateFinalReply(id: Long, finalReply: String)

    @Delete
    suspend fun deleteReply(reply: ReplyHistoryEntity)

    @Query("DELETE FROM reply_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM reply_history")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM reply_history WHERE modified = 1")
    suspend fun getModifiedCount(): Int

    @Query("SELECT * FROM reply_history WHERE styleApplied = 1 ORDER BY sendTime DESC LIMIT :limit")
    suspend fun getStyleAppliedReplies(limit: Int = 100): List<ReplyHistoryEntity>

    // ========== 租户感知查询（同步用） ==========

    @Query("SELECT * FROM reply_history WHERE tenantId = :tenantId ORDER BY sendTime DESC LIMIT :limit")
    fun getRepliesByTenant(tenantId: String, limit: Int = 20): Flow<List<ReplyHistoryEntity>>

    @Query("SELECT * FROM reply_history WHERE tenantId = :tenantId ORDER BY sendTime DESC")
    suspend fun getRepliesByTenantSync(tenantId: String): List<ReplyHistoryEntity>

    @Query("UPDATE reply_history SET syncVersion = :version WHERE id = :id")
    suspend fun updateSyncVersion(id: Long, version: Long)
}
