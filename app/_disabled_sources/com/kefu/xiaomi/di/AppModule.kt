package com.kefu.xiaomi.di

import android.content.Context
import androidx.room.Room
import com.kefu.xiaomi.data.local.AppDatabase
import com.kefu.xiaomi.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "kefu_xiaomi_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideAppConfigDao(database: AppDatabase): AppConfigDao {
        return database.appConfigDao()
    }

    @Provides
    @Singleton
    fun provideKeywordRuleDao(database: AppDatabase): KeywordRuleDao {
        return database.keywordRuleDao()
    }

    @Provides
    @Singleton
    fun provideScenarioDao(database: AppDatabase): ScenarioDao {
        return database.scenarioDao()
    }

    @Provides
    @Singleton
    fun provideAIModelConfigDao(database: AppDatabase): AIModelConfigDao {
        return database.aiModelConfigDao()
    }

    @Provides
    @Singleton
    fun provideUserStyleProfileDao(database: AppDatabase): UserStyleProfileDao {
        return database.userStyleProfileDao()
    }

    @Provides
    @Singleton
    fun provideReplyHistoryDao(database: AppDatabase): ReplyHistoryDao {
        return database.replyHistoryDao()
    }
}
