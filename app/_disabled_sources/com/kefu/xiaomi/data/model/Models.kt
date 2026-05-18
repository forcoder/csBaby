package com.kefu.xiaomi.data.model

data class AppConfig(
    val packageName: String,
    val appName: String,
    val iconUri: String? = null,
    val isMonitored: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis()
)

data class KeywordRule(
    val id: Long = 0,
    val keyword: String,
    val matchType: MatchType = MatchType.CONTAINS,
    val replyTemplate: String,
    val category: String = "默认",
    val applicableScenarios: List<String> = emptyList(),
    val priority: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Scenario(
    val id: Long = 0,
    val name: String,
    val type: ScenarioType = ScenarioType.ALL_PROPERTIES,
    val targetId: String? = null,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class AIModelConfig(
    val id: Long = 0,
    val modelType: ModelType = ModelType.OPENAI,
    val modelName: String,
    val apiKey: String,
    val apiEndpoint: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1000,
    val isDefault: Boolean = false,
    val isEnabled: Boolean = true,
    val monthlyCost: Double = 0.0,
    val lastUsed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

data class UserStyleProfile(
    val userId: String = "default",
    val formalityLevel: Float = 0.5f,       // 0-1：正式度
    val enthusiasmLevel: Float = 0.5f,     // 0-1：热情度
    val professionalismLevel: Float = 0.5f, // 0-1：专业度
    val wordCountPreference: Int = 50,
    val commonPhrases: List<String> = emptyList(),
    val avoidPhrases: List<String> = emptyList(),
    val learningSamples: Int = 0,
    val accuracyScore: Float = 0.0f,
    val lastTrained: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

data class ReplyHistory(
    val id: Long = 0,
    val sourceApp: String,
    val originalMessage: String,
    val generatedReply: String,
    val finalReply: String,
    val ruleMatchedId: Long? = null,
    val modelUsedId: Long? = null,
    val styleApplied: Boolean = false,
    val sendTime: Long = System.currentTimeMillis(),
    val modified: Boolean = false
)

data class ReplyResult(
    val reply: String,
    val source: ReplySource,
    val confidence: Float,
    val ruleId: Long? = null,
    val modelId: Long? = null
)

data class NewMessage(
    val id: String,
    val appPackage: String,
    val appName: String,
    val content: String,
    val sender: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
