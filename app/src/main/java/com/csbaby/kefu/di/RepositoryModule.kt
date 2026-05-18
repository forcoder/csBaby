package com.csbaby.kefu.di

import com.csbaby.kefu.data.repository.*
import com.csbaby.kefu.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAppConfigRepository(
        impl: AppConfigRepositoryImpl
    ): AppConfigRepository

    @Binds
    @Singleton
    abstract fun bindKeywordRuleRepository(
        impl: KeywordRuleRepositoryImpl
    ): KeywordRuleRepository

    @Binds
    @Singleton
    abstract fun bindScenarioRepository(
        impl: ScenarioRepositoryImpl
    ): ScenarioRepository

    @Binds
    @Singleton
    abstract fun bindAIModelRepository(
        impl: AIModelRepositoryImpl
    ): AIModelRepository

    @Binds
    @Singleton
    abstract fun bindUserStyleRepository(
        impl: UserStyleRepositoryImpl
    ): UserStyleRepository

    @Binds
    @Singleton
    abstract fun bindReplyHistoryRepository(
        impl: ReplyHistoryRepositoryImpl
    ): ReplyHistoryRepository
}
