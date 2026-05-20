package com.csbaby.kefu.di

import android.content.Context
import com.csbaby.kefu.BuildConfig
import com.csbaby.kefu.data.local.dao.*
import com.csbaby.kefu.data.remote.OtaApiService
import com.csbaby.kefu.data.remote.SyncApiService
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
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OtaAndOssModule {

    /**
     * 为 OTA/Backup API 创建独立的 Retrofit 实例，使用同步服务器 base URL。
     * 不能复用 NetworkModule 提供的 Retrofit（其 base URL 是 OpenAI）。
     */
    @Provides
    @Singleton
    fun provideSyncRetrofit(@ApplicationContext context: Context): Retrofit {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.SYNC_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideSyncApiService(retrofit: Retrofit, authManager: AuthManager): SyncApiService {
        val authInterceptor = Interceptor { chain ->
            val token = authManager.currentAuthState?.accessToken
            val request = if (token != null) {
                chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        val authenticatedClient = retrofit.newBuilder()
            .addInterceptor(authInterceptor)
            .build()
        return authenticatedClient.create(SyncApiService::class.java)
    }

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
        syncApiService: SyncApiService,
        keywordRuleDao: KeywordRuleDao,
        aiModelConfigDao: AIModelConfigDao,
        userStyleProfileDao: UserStyleProfileDao,
        appConfigDao: AppConfigDao,
        scenarioDao: ScenarioDao,
        replyHistoryDao: ReplyHistoryDao,
        messageBlacklistDao: MessageBlacklistDao
    ): BackupManager {
        return BackupManager(
            context, authManager, syncApiService,
            keywordRuleDao, aiModelConfigDao, userStyleProfileDao,
            appConfigDao, scenarioDao, replyHistoryDao, messageBlacklistDao
        )
    }
}
