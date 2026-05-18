package com.csbaby.kefu.domain.repository

import com.csbaby.kefu.data.local.dao.MessageBlacklistDao
import com.csbaby.kefu.data.local.entity.MessageBlacklistEntity
import com.csbaby.kefu.data.remote.CsbabyApiService
import com.csbaby.kefu.data.remote.UserAuthManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageBlacklistRepository @Inject constructor(
    private val blacklistDao: MessageBlacklistDao,
    private val apiService: CsbabyApiService,
    private val userAuthManager: UserAuthManager
) {
    fun getAllEnabled(): Flow<List<MessageBlacklistEntity>> {
        return blacklistDao.getAllEnabledFlow()
    }

    fun getAll(): Flow<List<MessageBlacklistEntity>> {
        return blacklistDao.getAllFlow()
    }

    suspend fun addToBlacklist(
        type: String,
        value: String,
        description: String = "",
        packageName: String? = null
    ): Long {
        val id = blacklistDao.insert(
            MessageBlacklistEntity(
                type = type,
                value = value,
                description = description,
                packageName = packageName
            )
        )
        // Note: Blacklist admin endpoints are admin-only, not device endpoints.
        // For now, blacklist stays local-only unless admin manages it.
        return id
    }

    suspend fun removeFromBlacklist(id: Long) {
        blacklistDao.deleteById(id)
    }

    suspend fun updateBlacklist(blacklist: MessageBlacklistEntity) {
        blacklistDao.update(blacklist)
    }

    suspend fun toggleBlacklist(id: Long, isEnabled: Boolean) {
        val blacklist = blacklistDao.getAllFlow().first().find { it.id == id }
        blacklist?.let {
            blacklistDao.update(it.copy(isEnabled = isEnabled))
        }
    }

    suspend fun clearAll() {
        blacklistDao.deleteAll()
    }

    suspend fun importBlacklist(items: List<MessageBlacklistEntity>) {
        blacklistDao.insertAll(items)
    }

    suspend fun isBlacklisted(value: String): Boolean {
        return blacklistDao.isBlacklisted(value)
    }

    suspend fun shouldFilterMessage(
        content: String,
        sender: String? = null,
        packageName: String? = null
    ): Boolean {
        val blacklists = blacklistDao.getAllEnabledFlow().first()

        for (blacklist in blacklists) {
            if (blacklist.packageName != null && blacklist.packageName != packageName) {
                continue
            }

            when (blacklist.type) {
                MessageBlacklistEntity.TYPE_KEYWORD -> {
                    if (content.contains(blacklist.value, ignoreCase = true)) {
                        return true
                    }
                }
                MessageBlacklistEntity.TYPE_SENDER -> {
                    if (sender != null && sender.contains(blacklist.value, ignoreCase = true)) {
                        return true
                    }
                }
                MessageBlacklistEntity.TYPE_CONTENT -> {
                    if (content.trim() == blacklist.value.trim()) {
                        return true
                    }
                }
            }
        }

        return false
    }
}
