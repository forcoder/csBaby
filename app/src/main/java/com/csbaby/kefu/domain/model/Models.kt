package com.csbaby.kefu.domain.model

data class AppConfig(
    val packageName: String,
    val appName: String,
    val iconUri: String?,
    val isMonitored: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis(),
    val tenantId: String = "default_tenant",
    val syncVersion: Long = 0L,
    val deleted: Boolean = false
)

data class KeywordRule(
    val id: Long = 0,
    val keyword: String,
    val matchType: MatchType,
    val replyTemplate: String,
    val category: String,
    val applicableScenarios: List<Long> = emptyList(),
    val targetType: RuleTargetType = RuleTargetType.ALL,
    val targetNames: List<String> = emptyList(),
    val priority: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val tenantId: String = "default_tenant",
    val syncVersion: Long = 0L,
    val deleted: Boolean = false
)


data class Scenario(
    val id: Long = 0,
    val name: String,
    val type: ScenarioType,
    val targetId: String?,
    val description: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val tenantId: String = "default_tenant",
    val syncVersion: Long = 0L,
    val deleted: Boolean = false
)

data class AIModelConfig(
    val id: Long = 0,
    val modelType: ModelType,
    val modelName: String,
    val apiKey: String,
    val apiEndpoint: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1000,
    val isDefault: Boolean = false,
    val isEnabled: Boolean = true,
    val monthlyCost: Double = 0.0,
    val lastUsed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val tenantId: String = "default_tenant",
    val syncVersion: Long = 0L,
    val deleted: Boolean = false
)

data class UserStyleProfile(
    val userId: String,
    val formalityLevel: Float = 0.5f,
    val enthusiasmLevel: Float = 0.5f,
    val professionalismLevel: Float = 0.5f,
    val wordCountPreference: Int = 50,
    val commonPhrases: List<String> = emptyList(),
    val avoidPhrases: List<String> = emptyList(),
    val learningSamples: Int = 0,
    val accuracyScore: Float = 0.0f,
    val lastTrained: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val tenantId: String = "default_tenant",
    val syncVersion: Long = 0L,
    val deleted: Boolean = false
)

data class ReplyHistory(
    val id: Long = 0,
    val sourceApp: String,
    val originalMessage: String,
    val generatedReply: String,
    val finalReply: String,
    val ruleMatchedId: Long?,
    val modelUsedId: Long?,
    val styleApplied: Boolean = false,
    val sendTime: Long = System.currentTimeMillis(),
    val modified: Boolean = false,
    val tenantId: String = "default_tenant",
    val syncVersion: Long = 0L,
    val deleted: Boolean = false
)

data class ReplyContext(
    val appPackage: String,
    val scenarioId: String? = null,
    val conversationTitle: String? = null,
    val propertyName: String? = null,
    val isGroupConversation: Boolean? = null,
    val userId: String
)




data class ReplyResult(
    val reply: String,
    val source: ReplySource,
    val confidence: Float,
    val ruleId: Long? = null,
    val modelId: Long? = null
)
