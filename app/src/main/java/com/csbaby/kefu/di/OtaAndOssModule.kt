package com.csbaby.kefu.di

import android.content.Context
import com.csbaby.kefu.data.local.dao.*
import com.csbaby.kefu.data.remote.OtaApiService
import com.csbaby.kefu.data.repository.OtaRepository
import com.csbaby.kefu.data.repository.OtaRepositoryImpl
import com.csbaby.kefu.data.sync.AuthManager
import com.csbaby.kefu.infrastructure.backup.BackupManager
import com.csbaby.kefu.infrastructure.ota.OtaManager
import com.csbaby.kefu.infrastructure.ota.OtaScheduler
import com.csbaby.kefu.infrastructure.ota.OtaUpdateWorker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OtaAndOssModule {

    @Provides
    @Singleton
    fun provideOtaApiService(retrofit: Retrofit): OtaApiService {
        return retrofit.create(OtaApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideOtaRepository(
        @ApplicationContext context: Context,
        apiService: OtaApiService
    ): OtaRepository {
        return OtaRepositoryImpl(context, apiService)
    }

    @Provides
    @Singleton
    fun provideOtaManager(
        @ApplicationContext context: Context,
        repository: OtaRepository
    ): OtaManager {
        return OtaManager(context, repository)
    }

    @Provides
    @Singleton
    fun provideOtaScheduler(@ApplicationContext context: Context): OtaScheduler {
        return OtaScheduler(context)
    }

    @Provides
    @Singleton
    fun provideOtaUpdateWorkerFactory(repository: OtaRepository): OtaUpdateWorker.Factory {
        return object : OtaUpdateWorker.Factory {
            override fun create(context: Context, params: androidx.work.WorkerParameters): OtaUpdateWorker {
                return OtaUpdateWorker(context, params, repository)
            }
        }
    }

    @Provides
    @Singleton
    fun provideBackupManager(
        @ApplicationContext context: Context,
        authManager: AuthManager,
        keywordRuleDao: KeywordRuleDao,
        aiModelConfigDao: AIModelConfigDao,
        userStyleProfileDao: UserStyleProfileDao,
        appConfigDao: AppConfigDao,
        scenarioDao: ScenarioDao,
        replyHistoryDao: ReplyHistoryDao,
        messageBlacklistDao: MessageBlacklistDao
    ): BackupManager {
        return BackupManager(
            context, authManager,
            keywordRuleDao, aiModelConfigDao, userStyleProfileDao,
            appConfigDao, scenarioDao, replyHistoryDao, messageBlacklistDao
        )
    }
}
