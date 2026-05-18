package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.UserStyleProfileDao
import com.csbaby.kefu.domain.model.UserStyleProfile
import com.csbaby.kefu.domain.repository.UserStyleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserStyleRepositoryImpl @Inject constructor(
    private val userStyleProfileDao: UserStyleProfileDao
) : UserStyleRepository {

    override fun getProfile(userId: String): Flow<UserStyleProfile?> {
        return userStyleProfileDao.getProfileByUserId(userId).map { it?.toDomain() }
    }

    override suspend fun getProfileSync(userId: String): UserStyleProfile? {
        return userStyleProfileDao.getProfileByUserIdSync(userId)?.toDomain()
    }

    override suspend fun saveProfile(profile: UserStyleProfile) {
        userStyleProfileDao.insertProfile(profile.toEntity())
    }

    override suspend fun updateProfile(profile: UserStyleProfile) {
        userStyleProfileDao.updateProfile(profile.toEntity())
    }

    override suspend fun updateFormalityLevel(userId: String, formality: Float) {
        userStyleProfileDao.updateFormalityLevel(userId, formality.coerceIn(0f, 1f))
    }

    override suspend fun updateEnthusiasmLevel(userId: String, enthusiasm: Float) {
        userStyleProfileDao.updateEnthusiasmLevel(userId, enthusiasm.coerceIn(0f, 1f))
    }

    override suspend fun updateProfessionalismLevel(userId: String, professionalism: Float) {
        userStyleProfileDao.updateProfessionalismLevel(userId, professionalism.coerceIn(0f, 1f))
    }

    override suspend fun incrementLearningSamples(userId: String) {
        userStyleProfileDao.incrementLearningSamples(userId, System.currentTimeMillis())
    }

    override suspend fun updateAccuracyScore(userId: String, score: Float) {
        userStyleProfileDao.updateAccuracyScore(userId, score.coerceIn(0f, 1f))
    }
}
