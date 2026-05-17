package com.csbaby.kefu.di

import android.content.Context
import com.csbaby.kefu.BuildConfig
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.remote.AIClient
import com.csbaby.kefu.data.remote.AIClientImpl
import com.csbaby.kefu.data.remote.AuthInterceptor
import com.csbaby.kefu.data.remote.CsbabyApiService
import com.csbaby.kefu.data.remote.UserAuthManager
import com.csbaby.kefu.infrastructure.error.ErrorHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CsbabyOkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideAIClient(okHttpClient: OkHttpClient): AIClient {
        return AIClientImpl(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideErrorHandler(@ApplicationContext context: Context): ErrorHandler {
        return ErrorHandler(context)
    }

    @Provides
    @Singleton
    fun providePerformanceMonitor(@ApplicationContext context: Context): com.csbaby.kefu.infrastructure.monitoring.PerformanceMonitor {
        return com.csbaby.kefu.infrastructure.monitoring.PerformanceMonitor(context)
    }

    // ========== csBaby API ==========

    @Provides
    @Singleton
    fun provideAuthInterceptor(preferencesManager: PreferencesManager): AuthInterceptor {
        return AuthInterceptor(preferencesManager)
    }

    @Provides
    @Singleton
    @CsbabyOkHttpClient
    fun provideCsbabyOkHttpClient(
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideCsbabyApiService(
        @CsbabyOkHttpClient csbabyOkHttpClient: OkHttpClient
    ): CsbabyApiService {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(csbabyOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CsbabyApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideUserAuthManager(
        apiService: CsbabyApiService,
        preferencesManager: PreferencesManager,
        authInterceptor: AuthInterceptor
    ): UserAuthManager {
        return UserAuthManager(apiService, preferencesManager, authInterceptor)
    }
}
