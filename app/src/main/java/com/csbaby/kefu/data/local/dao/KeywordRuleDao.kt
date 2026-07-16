package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.KeywordRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordRuleDao {
    @Query("SELECT * FROM keyword_rules WHERE deleted = 0 ORDER BY priority DESC, createdAt DESC")
    fun getAllRules(): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules WHERE enabled = 1 AND deleted = 0 ORDER BY priority DESC")
    fun getEnabledRules(): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules WHERE category = :category AND deleted = 0 ORDER BY priority DESC")
    fun getRulesByCategory(category: String): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules WHERE id = :id AND deleted = 0")
    suspend fun getRuleById(id: Long): KeywordRuleEntity?

    @Query("SELECT * FROM keyword_rules WHERE keyword LIKE '%' || :keyword || '%' AND deleted = 0")
    suspend fun searchByKeyword(keyword: String): List<KeywordRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: KeywordRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<KeywordRuleEntity>)

    @Update
    suspend fun updateRule(rule: KeywordRuleEntity)

    @Delete
    suspend fun deleteRule(rule: KeywordRuleEntity)

    @Query("DELETE FROM keyword_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM keyword_rules")
    suspend fun deleteAllRules()

    @Query("DELETE FROM keyword_rules WHERE tenantId = :tenantId")
    suspend fun deleteRulesByTenant(tenantId: String)

    @Query("SELECT COUNT(*) FROM keyword_rules WHERE deleted = 0")
    suspend fun getRuleCount(): Int

    @Query("SELECT COUNT(*) FROM keyword_rules WHERE deleted = 0")
    fun getRuleCountFlow(): Flow<Int>

    @Query("SELECT DISTINCT category FROM keyword_rules WHERE tenantId = :tenantId AND deleted = 0")
    fun getAllCategoriesByTenant(tenantId: String): Flow<List<String>>

    @Query("SELECT DISTINCT category FROM keyword_rules WHERE deleted = 0")
    fun getAllCategories(): Flow<List<String>>

    // ========== 租户感知查询（同步用） ==========

    @Query("SELECT * FROM keyword_rules WHERE tenantId = :tenantId ORDER BY priority DESC, createdAt DESC")
    fun getRulesByTenant(tenantId: String): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules WHERE tenantId = :tenantId ORDER BY priority DESC, createdAt DESC")
    suspend fun getRulesByTenantSync(tenantId: String): List<KeywordRuleEntity>

    @Query("SELECT * FROM keyword_rules WHERE id = :id")
    suspend fun getById(id: Long): KeywordRuleEntity?

    @Query("UPDATE keyword_rules SET syncVersion = :version WHERE id = :id")
    suspend fun updateSyncVersion(id: Long, version: Long)

    @Query("UPDATE keyword_rules SET deleted = 1, syncVersion = 0 WHERE id = :id")
    suspend fun softDelete(id: Long): Int

    @Query("UPDATE keyword_rules SET deleted = 1, syncVersion = 0 WHERE tenantId = :tenantId")
    suspend fun softDeleteAllByTenant(tenantId: String): Int
}
