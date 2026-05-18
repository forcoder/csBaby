package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.MessageBlacklistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageBlacklistDao {
    
    @Query("SELECT * FROM message_blacklist WHERE isEnabled = 1 ORDER BY createdAt DESC")
    fun getAllEnabledFlow(): Flow<List<MessageBlacklistEntity>>
    
    @Query("SELECT * FROM message_blacklist ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<MessageBlacklistEntity>>
    
    @Query("SELECT * FROM message_blacklist WHERE type = :type AND isEnabled = 1 ORDER BY createdAt DESC")
    fun getByTypeFlow(type: String): Flow<List<MessageBlacklistEntity>>
    
    @Query("SELECT * FROM message_blacklist WHERE packageName = :packageName AND isEnabled = 1 ORDER BY createdAt DESC")
    fun getByPackageFlow(packageName: String): Flow<List<MessageBlacklistEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(blacklist: MessageBlacklistEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(blacklists: List<MessageBlacklistEntity>)
    
    @Update
    suspend fun update(blacklist: MessageBlacklistEntity)
    
    @Delete
    suspend fun delete(blacklist: MessageBlacklistEntity)
    
    @Query("DELETE FROM message_blacklist WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM message_blacklist")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM message_blacklist WHERE isEnabled = 1")
    suspend fun getEnabledCount(): Int
    
    @Query("SELECT EXISTS(SELECT 1 FROM message_blacklist WHERE value = :value AND isEnabled = 1)")
    suspend fun isBlacklisted(value: String): Boolean

    @Query("SELECT * FROM message_blacklist ORDER BY createdAt DESC")
    suspend fun getAllList(): List<MessageBlacklistEntity>
}
