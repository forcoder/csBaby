package com.kefu.xiaomi.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kefu.xiaomi.data.local.dao.*
import com.kefu.xiaomi.data.local.entity.*

@Database(
    entities = [
        AppConfigEntity::class,
        KeywordRuleEntity::class,
        ScenarioEntity::class,
        AIModelConfigEntity::class,
        UserStyleProfileEntity::class,
        ReplyHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appConfigDao(): AppConfigDao
    abstract fun keywordRuleDao(): KeywordRuleDao
    abstract fun scenarioDao(): ScenarioDao
    abstract fun aiModelConfigDao(): AIModelConfigDao
    abstract fun userStyleProfileDao(): UserStyleProfileDao
    abstract fun replyHistoryDao(): ReplyHistoryDao
}
