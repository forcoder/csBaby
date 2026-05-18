package com.kefu.xiaomi.data.local.dao

import androidx.room.*
import com.kefu.xiaomi.data.local.entity.KeywordRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordRuleDao {
    @Query("SELECT * FROM keyword_rules")
    fun getAll(): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules WHERE enabled = 1")
    fun getEnabled(): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules WHERE category = :category")
    fun getByCategory(category: String): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules WHERE id = :id")
    suspend fun getById(id: Long): KeywordRuleEntity?

    @Query("SELECT * FROM keyword_rules WHERE keyword LIKE '%' || :keyword || '%'")
    suspend fun search(keyword: String): List<KeywordRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: KeywordRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<KeywordRuleEntity>)

    @Update
    suspend fun update(rule: KeywordRuleEntity)

    @Delete
    suspend fun delete(rule: KeywordRuleEntity)

    @Query("DELETE FROM keyword_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
