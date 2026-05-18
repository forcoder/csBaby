package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.ReplyHistoryDao
import com.csbaby.kefu.domain.model.ReplyHistory
import com.csbaby.kefu.domain.repository.ReplyHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReplyHistoryRepositoryImpl @Inject constructor(
    private val replyHistoryDao: ReplyHistoryDao
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
        return replyHistoryDao.insertReply(reply.toEntity())
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
}
