package com.csbaby.kefu.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.csbaby.kefu.data.local.dao.*
import com.csbaby.kefu.data.local.entity.*

@Database(
    entities = [
        AppConfigEntity::class,
        KeywordRuleEntity::class,
        ScenarioEntity::class,
        RuleScenarioCrossRef::class,
        AIModelConfigEntity::class,
        UserStyleProfileEntity::class,
        ReplyHistoryEntity::class,
        SyncCheckpointEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class KefuDatabase : RoomDatabase() {
    abstract fun appConfigDao(): AppConfigDao
    abstract fun keywordRuleDao(): KeywordRuleDao
    abstract fun scenarioDao(): ScenarioDao
    abstract fun aiModelConfigDao(): AIModelConfigDao
    abstract fun userStyleProfileDao(): UserStyleProfileDao
    abstract fun replyHistoryDao(): ReplyHistoryDao
    abstract fun syncCheckpointDao(): SyncCheckpointDao

    companion object {
        const val DATABASE_NAME = "kefu_database"
    }
}
