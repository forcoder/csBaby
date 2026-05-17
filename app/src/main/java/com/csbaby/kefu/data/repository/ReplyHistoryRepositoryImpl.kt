package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.ReplyHistoryDao
import com.csbaby.kefu.data.remote.CsbabyApiService
import com.csbaby.kefu.data.remote.UserAuthManager
import com.csbaby.kefu.data.remote.dto.toDomain as dtoToDomain
import com.csbaby.kefu.data.remote.dto.toDto as domainToDto
import com.csbaby.kefu.domain.model.ReplyHistory
import com.csbaby.kefu.domain.repository.ReplyHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReplyHistoryRepositoryImpl @Inject constructor(
    private val replyHistoryDao: ReplyHistoryDao,
    private val apiService: CsbabyApiService,
    private val userAuthManager: UserAuthManager
) : ReplyHistoryRepository {

    override fun getRecentReplies(limit: Int): Flow<List<ReplyHistory>> {
        return replyHistoryDao.getRecentReplies(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getRepliesByApp(appPackage: String, limit: Int): Flow<List<ReplyHistory>> {
        return replyHistoryDao.getRepliesByApp(appPackage, limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getReplyById(id: Long): ReplyHistory? {
        return replyHistoryDao.getReplyById(id)?.toDomain()
    }

    override suspend fun insertReply(reply: ReplyHistory): Long {
        val localId = replyHistoryDao.insertReply(reply.toEntity())
        // Also record on server (best-effort, don't fail if server is unreachable)
        try {
            userAuthManager.ensureAuthenticated()
            apiService.recordHistory(reply.domainToDto())
            Timber.d("History recorded on server")
        } catch (e: Exception) {
            Timber.w(e, "Failed to record history on server, saved locally")
        }
        return localId
    }

    override suspend fun updateFinalReply(id: Long, finalReply: String) {
        replyHistoryDao.updateFinalReply(id, finalReply)
    }

    override suspend fun deleteReply(id: Long) {
        replyHistoryDao.deleteById(id)
    }

    override suspend fun getStyleAppliedReplies(limit: Int): List<ReplyHistory> {
        return replyHistoryDao.getStyleAppliedReplies(limit).map { it.toDomain() }
    }

    override suspend fun getTotalCount(): Int = replyHistoryDao.getTotalCount()

    override suspend fun getModifiedCount(): Int = replyHistoryDao.getModifiedCount()

    /**
     * Sync history from server (incremental, based on local count).
     */
    suspend fun syncFromServer(): Result<Int> {
        return try {
            userAuthManager.ensureAuthenticated()
            val localCount = replyHistoryDao.getTotalCount()
            val response = apiService.getHistory(limit = 100, offset = localCount)
            var count = 0
            for (dto in response.items) {
                val history = dto.dtoToDomain()
                replyHistoryDao.insertReply(history.toEntity())
                count++
            }
            Timber.i("History synced from server: $count new entries")
            Result.success(count)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync history from server")
            Result.failure(e)
        }
    }

    suspend fun forceRefresh(): Result<Int> = syncFromServer()
}
