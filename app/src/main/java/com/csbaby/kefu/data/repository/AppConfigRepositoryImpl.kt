package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.local.EntityMapper.toDomain
import com.csbaby.kefu.data.local.EntityMapper.toEntity
import com.csbaby.kefu.data.local.dao.AppConfigDao
import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.domain.model.AppConfig
import com.csbaby.kefu.domain.repository.AppConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppConfigRepositoryImpl @Inject constructor(
    private val appConfigDao: AppConfigDao,
    private val syncManager: SyncManager
) : AppConfigRepository {

    override fun getAllApps(): Flow<List<AppConfig>> {
        return appConfigDao.getAllApps().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getMonitoredApps(): Flow<List<AppConfig>> {
        return appConfigDao.getMonitoredApps().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAppByPackage(packageName: String): AppConfig? {
        return appConfigDao.getAppByPackage(packageName)?.toDomain()
    }

    override suspend fun insertApp(app: AppConfig) {
        appConfigDao.insertApp(app.toEntity())
        syncManager.triggerSync()
    }

    override suspend fun insertApps(apps: List<AppConfig>) {
        appConfigDao.insertApps(apps.map { it.toEntity() })
        syncManager.triggerSync()
    }

    override suspend fun updateApp(app: AppConfig) {
        appConfigDao.updateApp(app.toEntity())
        syncManager.triggerSync()
    }

    override suspend fun updateMonitorStatus(packageName: String, isMonitored: Boolean) {
        appConfigDao.updateMonitorStatus(packageName, isMonitored)
        syncManager.triggerSync()
    }

    override suspend fun deleteApp(packageName: String) {
        appConfigDao.deleteByPackage(packageName)
        syncManager.triggerSync()
    }
}
