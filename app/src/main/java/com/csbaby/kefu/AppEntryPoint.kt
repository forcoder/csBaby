package com.csbaby.kefu

import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.infrastructure.ota.OtaScheduler
import com.csbaby.kefu.infrastructure.reply.ReplyOrchestrator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun replyOrchestrator(): ReplyOrchestrator
    fun otaScheduler(): OtaScheduler
    fun syncManager(): SyncManager
    fun preferencesManager(): PreferencesManager
}
