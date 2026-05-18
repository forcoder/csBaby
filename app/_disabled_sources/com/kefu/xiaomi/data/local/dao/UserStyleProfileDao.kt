package com.kefu.xiaomi.data.local.dao

import androidx.room.*
import com.kefu.xiaomi.data.local.entity.UserStyleProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserStyleProfileDao {
    @Query("SELECT * FROM user_style_profiles WHERE userId = :userId")
    fun getByUserId(userId: String): Flow<UserStyleProfileEntity?>

    @Query("SELECT * FROM user_style_profiles LIMIT 1")
    suspend fun getFirst(): UserStyleProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: UserStyleProfileEntity)

    @Update
    suspend fun update(profile: UserStyleProfileEntity)

    @Delete
    suspend fun delete(profile: UserStyleProfileEntity)

    @Query("DELETE FROM user_style_profiles WHERE userId = :userId")
    suspend fun deleteByUserId(userId: String)
}
