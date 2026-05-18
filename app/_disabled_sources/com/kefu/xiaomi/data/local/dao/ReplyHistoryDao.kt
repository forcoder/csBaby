package com.kefu.xiaomi.data.local.dao

import androidx.room.*
import com.kefu.xiaomi.data.local.entity.ReplyHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplyHistoryDao {
    @Query("SELECT * FROM reply_history ORDER BY sendTime DESC")
    fun getAll(): Flow<List<ReplyHistoryEntity>>

    @Query("SELECT * FROM reply_history ORDER BY sendTime DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<ReplyHistoryEntity>>

    @Query("SELECT * FROM reply_history WHERE sourceApp = :appPackage ORDER BY sendTime DESC")
    fun getByApp(appPackage: String): Flow<List<ReplyHistoryEntity>>

    @Query("SELECT * FROM reply_history WHERE sendTime >= :startTime ORDER BY sendTime DESC")
    fun getAfter(startTime: Long): Flow<List<ReplyHistoryEntity>>

    @Query("SELECT COUNT(*) FROM reply_history WHERE sendTime >= :startTime")
    suspend fun getCountAfter(startTime: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ReplyHistoryEntity): Long

    @Update
    suspend fun update(history: ReplyHistoryEntity)

    @Delete
    suspend fun delete(history: ReplyHistoryEntity)

    @Query("DELETE FROM reply_history WHERE sendTime < :beforeTime")
    suspend fun deleteOld(beforeTime: Long)

    @Query("DELETE FROM reply_history")
    suspend fun deleteAll()
}
