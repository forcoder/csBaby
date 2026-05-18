package com.csbaby.kefu.data.local.dao

import androidx.room.*
import com.csbaby.kefu.data.local.entity.UserStyleProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserStyleProfileDao {
    @Query("SELECT * FROM user_style_profiles WHERE userId = :userId")
    fun getProfileByUserId(userId: String): Flow<UserStyleProfileEntity?>

    @Query("SELECT * FROM user_style_profiles WHERE userId = :userId")
    suspend fun getProfileByUserIdSync(userId: String): UserStyleProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserStyleProfileEntity)

    @Update
    suspend fun updateProfile(profile: UserStyleProfileEntity)

    @Query("UPDATE user_style_profiles SET formalityLevel = :formality WHERE userId = :userId")
    suspend fun updateFormalityLevel(userId: String, formality: Float)

    @Query("UPDATE user_style_profiles SET enthusiasmLevel = :enthusiasm WHERE userId = :userId")
    suspend fun updateEnthusiasmLevel(userId: String, enthusiasm: Float)

    @Query("UPDATE user_style_profiles SET professionalismLevel = :professionalism WHERE userId = :userId")
    suspend fun updateProfessionalismLevel(userId: String, professionalism: Float)

    @Query("UPDATE user_style_profiles SET learningSamples = learningSamples + 1, lastTrained = :timestamp WHERE userId = :userId")
    suspend fun incrementLearningSamples(userId: String, timestamp: Long)

    @Query("UPDATE user_style_profiles SET accuracyScore = :score WHERE userId = :userId")
    suspend fun updateAccuracyScore(userId: String, score: Float)

    @Delete
    suspend fun deleteProfile(profile: UserStyleProfileEntity)

    // ========== 租户感知查询（同步用） ==========

    @Query("SELECT * FROM user_style_profiles WHERE tenantId = :tenantId")
    fun getProfileByTenantId(tenantId: String): Flow<UserStyleProfileEntity?>

    @Query("SELECT * FROM user_style_profiles WHERE tenantId = :tenantId")
    suspend fun getProfileByTenantIdSync(tenantId: String): UserStyleProfileEntity?

    @Query("UPDATE user_style_profiles SET syncVersion = :version WHERE userId = :userId")
    suspend fun updateSyncVersion(userId: String, version: Long)
}
