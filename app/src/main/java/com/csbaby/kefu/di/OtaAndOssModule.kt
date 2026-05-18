package com.csbaby.kefu.di

import android.content.Context
import androidx.work.WorkerParameters
import com.csbaby.kefu.data.remote.OtaApiService
import com.csbaby.kefu.data.remote.OssOtaApiService
import com.csbaby.kefu.data.repository.OtaRepository
import com.csbaby.kefu.data.repository.OtaRepositoryImpl
import com.csbaby.kefu.infrastructure.ota.OtaManager
import com.csbaby.kefu.infrastructure.ota.OtaScheduler
import com.csbaby.kefu.infrastructure.ota.OtaUpdateWorker
import com.csbaby.kefu.infrastructure.oss.AliyunOssManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * OTA和OSS统一模块
 * 包含OTA更新和阿里云OSS版本管理的依赖注入
 */
@Module
@InstallIn(SingletonComponent::class)
object OtaAndOssModule {
    
    // ========== OTA功能 ==========
    
    /**
     * 提供OTA API服务
     */
    @Provides
    @Singleton
    fun provideOtaApiService(retrofit: Retrofit): OtaApiService {
        return retrofit.create(OtaApiService::class.java)
    }
    
    /**
     * 提供OSS OTA API服务
     */
    @Provides
    @Singleton
    fun provideOssOtaApiService(retrofit: Retrofit): OssOtaApiService {
        return retrofit.create(OssOtaApiService::class.java)
    }
    
    /**
     * 提供OTA仓库
     */
    @Provides
    @Singleton
    fun provideOtaRepository(
        @ApplicationContext context: Context,
        apiService: OtaApiService
    ): OtaRepository {
        return OtaRepositoryImpl(context, apiService)
    }
    
    /**
     * 提供OTA管理器
     */
    @Provides
    @Singleton
    fun provideOtaManager(
        @ApplicationContext context: Context,
        repository: OtaRepository
    ): OtaManager {
        return OtaManager(context, repository)
    }
    
    /**
     * 提供OTA调度器
     */
    @Provides
    @Singleton
    fun provideOtaScheduler(
        @ApplicationContext context: Context
    ): OtaScheduler {
        return OtaScheduler(context)
    }
    
    /**
     * 提供OTA Worker工厂
     * WorkManager使用AssistedInject，这里提供工厂
     */
    @Provides
    @Singleton
    fun provideOtaUpdateWorkerFactory(
        repository: OtaRepository
    ): OtaUpdateWorker.Factory {
        return object : OtaUpdateWorker.Factory {
            override fun create(
                context: Context,
                params: WorkerParameters
            ): OtaUpdateWorker {
                return OtaUpdateWorker(context, params, repository)
            }
        }
    }
    
    // ========== 阿里云OSS功能 ==========
    
    /**
     * 提供阿里云OSS管理器
     */
    @Provides
    @Singleton
    fun provideAliyunOssManager(
        @ApplicationContext context: Context
    ): AliyunOssManager {
        return AliyunOssManager(context)
    }
    
    /**
     * 提供直接OSS版本检查器
     */
    @Provides
    @Singleton
    fun provideDirectOssVersionChecker(
        ossManager: AliyunOssManager
    ): com.csbaby.kefu.data.remote.DirectOssVersionChecker {
        return com.csbaby.kefu.data.remote.DirectOssVersionChecker(ossManager)
    }
    
    /**
     * 提供OSS OTA仓库
     */
    @Provides
    @Singleton
    fun provideOssOtaRepository(
        ossManager: AliyunOssManager,
        directChecker: com.csbaby.kefu.data.remote.DirectOssVersionChecker
    ): com.csbaby.kefu.data.remote.OssOtaRepository {
        return com.csbaby.kefu.data.remote.OssOtaRepository(ossManager, directChecker)
    }
}