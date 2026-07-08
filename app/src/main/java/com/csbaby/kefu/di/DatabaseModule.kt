package com.csbaby.kefu.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.csbaby.kefu.data.local.KefuDatabase
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.local.dao.*

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KefuDatabase {
        return Room.databaseBuilder(
            context,
            KefuDatabase::class.java,
            KefuDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // 首次创建数据库时预置 LongCat 默认模型
                    db.execSQL(
                        "INSERT OR IGNORE INTO ai_model_configs " +
                        "(modelType, modelName, apiKey, apiEndpoint, temperature, maxTokens, " +
                        "isDefault, isEnabled, monthlyCost, lastUsed, createdAt, tenantId, syncVersion, deleted) " +
                        "VALUES ('OPENAI', 'LongCat-2.0', '', 'https://api.longcat.chat/openai/v1', " +
                        "0.7, 1000, 1, 1, 0.0, " +
                        "CAST(strftime('%s','now') AS INTEGER)*1000, CAST(strftime('%s','now') AS INTEGER)*1000, " +
                        "'default_tenant', 0, 0)"
                    )
                }
            })
            .build()
    }


    @Provides
    @Singleton
    fun provideAppConfigDao(database: KefuDatabase): AppConfigDao {
        return database.appConfigDao()
    }

    @Provides
    @Singleton
    fun provideKeywordRuleDao(database: KefuDatabase): KeywordRuleDao {
        return database.keywordRuleDao()
    }

    @Provides
    @Singleton
    fun provideScenarioDao(database: KefuDatabase): ScenarioDao {
        return database.scenarioDao()
    }

    @Provides
    @Singleton
    fun provideAIModelConfigDao(database: KefuDatabase): AIModelConfigDao {
        return database.aiModelConfigDao()
    }

    @Provides
    @Singleton
    fun provideUserStyleProfileDao(database: KefuDatabase): UserStyleProfileDao {
        return database.userStyleProfileDao()
    }

    @Provides
    @Singleton
    fun provideReplyHistoryDao(database: KefuDatabase): ReplyHistoryDao {
        return database.replyHistoryDao()
    }

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideSyncCheckpointDao(database: KefuDatabase): SyncCheckpointDao {
        return database.syncCheckpointDao()
    }

    @Provides
    @Singleton
    fun provideMessageBlacklistDao(database: KefuDatabase): MessageBlacklistDao {
        // 使用 Room.databaseBuilder 生成的实现类中的方法
        return database.messageBlacklistDao()
    }

    // 注意：MessageBlacklistDao 的 @Provides 方法必须放在 provideDatabase 之后
    // 因为 Hilt 需要确保 KefuDatabase 已经初始化

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE keyword_rules ADD COLUMN targetType TEXT NOT NULL DEFAULT 'ALL'"
            )
            database.execSQL(
                "ALTER TABLE keyword_rules ADD COLUMN targetNamesJson TEXT NOT NULL DEFAULT '[]'"
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add tenant_id, sync_version, deleted columns to all tables
            database.execSQL("ALTER TABLE keyword_rules ADD COLUMN tenantId TEXT NOT NULL DEFAULT 'default_tenant'")
            database.execSQL("ALTER TABLE keyword_rules ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE keyword_rules ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")

            database.execSQL("ALTER TABLE app_configs ADD COLUMN tenantId TEXT NOT NULL DEFAULT 'default_tenant'")
            database.execSQL("ALTER TABLE app_configs ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE app_configs ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")

            database.execSQL("ALTER TABLE ai_model_configs ADD COLUMN tenantId TEXT NOT NULL DEFAULT 'default_tenant'")
            database.execSQL("ALTER TABLE ai_model_configs ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE ai_model_configs ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")

            database.execSQL("ALTER TABLE user_style_profiles ADD COLUMN tenantId TEXT NOT NULL DEFAULT 'default_tenant'")
            database.execSQL("ALTER TABLE user_style_profiles ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE user_style_profiles ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")

            database.execSQL("ALTER TABLE reply_history ADD COLUMN tenantId TEXT NOT NULL DEFAULT 'default_tenant'")
            database.execSQL("ALTER TABLE reply_history ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE reply_history ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")

            database.execSQL("ALTER TABLE scenarios ADD COLUMN tenantId TEXT NOT NULL DEFAULT 'default_tenant'")
            database.execSQL("ALTER TABLE scenarios ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE scenarios ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")

            database.execSQL("ALTER TABLE rule_scenario_relation ADD COLUMN tenantId TEXT NOT NULL DEFAULT 'default_tenant'")

            // Create sync_checkpoints table
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_checkpoints (" +
                    "tenantId TEXT NOT NULL PRIMARY KEY, " +
                    "lastSyncTime INTEGER NOT NULL DEFAULT 0, " +
                    "syncToken TEXT, " +
                    "isSyncing INTEGER NOT NULL DEFAULT 0, " +
                    "lastError TEXT)"
            )

            // Create indexes for tenant queries
            database.execSQL("CREATE INDEX IF NOT EXISTS index_keyword_rules_tenantId ON keyword_rules(tenantId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_app_configs_tenantId ON app_configs(tenantId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_ai_model_configs_tenantId ON ai_model_configs(tenantId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_user_style_profiles_tenantId ON user_style_profiles(tenantId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_reply_history_tenantId ON reply_history(tenantId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_scenarios_tenantId ON scenarios(tenantId)")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // message_blacklist 表加入同步字段
            database.execSQL("ALTER TABLE message_blacklist ADD COLUMN tenantId TEXT NOT NULL DEFAULT 'default_tenant'")
            database.execSQL("ALTER TABLE message_blacklist ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE message_blacklist ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_message_blacklist_tenantId ON message_blacklist(tenantId)")
            // 补充 MIGRATION_2_3 中缺失的 user_style_profiles tenantId 索引
            database.execSQL("CREATE INDEX IF NOT EXISTS index_user_style_profiles_tenantId ON user_style_profiles(tenantId)")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 修复 LongCat API 端点：旧端点 /openai 改为 /openai/v1/chat/completions
            database.execSQL(
                "UPDATE ai_model_configs SET apiEndpoint = 'https://api.longcat.chat/openai/v1/chat/completions' " +
                "WHERE apiEndpoint = 'https://api.longcat.chat/openai'"
            )
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // BUG-R14: keyword_rules 加 remoteId 列 (跨租户同步用)
            database.execSQL("ALTER TABLE keyword_rules ADD COLUMN remoteId TEXT")
            // 为已有数据生成 remoteId (tenantId_id 格式), 避免 NULL
            database.execSQL(
                "UPDATE keyword_rules SET remoteId = tenantId || '_' || id WHERE remoteId IS NULL"
            )
            // 建 (tenantId, remoteId) 唯一索引 (名称必须与 Entity @Index 一致)
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_keyword_rules_tenantId_remoteId ON keyword_rules(tenantId, remoteId)"
            )
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 预置 LongCat 默认模型（仅首次安装/无模型时生效）
            database.execSQL(
                "INSERT OR IGNORE INTO ai_model_configs " +
                "(modelType, modelName, apiKey, apiEndpoint, temperature, maxTokens, " +
                "isDefault, isEnabled, monthlyCost, lastUsed, createdAt, tenantId, syncVersion, deleted) " +
                "SELECT 'OPENAI', 'LongCat-2.0', '', 'https://api.longcat.chat/openai/v1', " +
                "0.7, 1000, 1, 1, 0.0, " +
                "CAST(strftime('%s','now') AS INTEGER)*1000, CAST(strftime('%s','now') AS INTEGER)*1000, " +
                "'default_tenant', 0, 0 " +
                "WHERE NOT EXISTS (SELECT 1 FROM ai_model_configs LIMIT 1)"
            )
        }
    }
}

