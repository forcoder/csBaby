package com.csbaby.kefu.data.local

import com.csbaby.kefu.data.local.entity.*
import com.csbaby.kefu.domain.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object EntityMapper {
    private val gson = Gson()

    // AppConfig mapping
    fun AppConfigEntity.toDomain() = AppConfig(
        packageName = packageName,
        appName = appName,
        iconUri = iconUri,
        isMonitored = isMonitored,
        createdAt = createdAt,
        lastUsed = lastUsed,
        tenantId = tenantId,
        syncVersion = syncVersion,
        deleted = deleted
    )

    fun AppConfig.toEntity() = AppConfigEntity(
        packageName = packageName,
        appName = appName,
        iconUri = iconUri,
        isMonitored = isMonitored,
        createdAt = createdAt,
        lastUsed = lastUsed,
        tenantId = tenantId,
        syncVersion = syncVersion,
        deleted = deleted
    )

    // KeywordRule mapping
    fun KeywordRuleEntity.toDomain(applicableScenarios: List<Long> = emptyList()) = KeywordRule(
        id = id,
        keyword = keyword,
        matchType = enumValueOrDefault(matchType, MatchType.CONTAINS),
        replyTemplate = replyTemplate,
        category = category,
        applicableScenarios = applicableScenarios,
        targetType = enumValueOrDefault(targetType, RuleTargetType.ALL),
        targetNames = parseJsonStringList(targetNamesJson),
        priority = priority,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun KeywordRule.toEntity() = KeywordRuleEntity(
        id = id,
        keyword = keyword,
        matchType = matchType.name,
        replyTemplate = replyTemplate,
        category = category,
        targetType = targetType.name,
        targetNamesJson = gson.toJson(targetNames),
        priority = priority,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
        tenantId = tenantId,
        syncVersion = syncVersion,
        deleted = deleted
    )


    // Scenario mapping
    fun ScenarioEntity.toDomain() = Scenario(
        id = id,
        name = name,
        type = enumValueOrDefault(type, ScenarioType.ALL_PROPERTIES),
        targetId = targetId,
        description = description,
        createdAt = createdAt,
        tenantId = tenantId,
        syncVersion = syncVersion,
        deleted = deleted
    )

    fun Scenario.toEntity() = ScenarioEntity(
        id = id,
        name = name,
        type = type.name,
        targetId = targetId,
        description = description,
        createdAt = createdAt,
        tenantId = tenantId,
        syncVersion = syncVersion,
        deleted = deleted
    )

    // AIModelConfig mapping
    fun AIModelConfigEntity.toDomain() = AIModelConfig(
        id = id,
        modelType = parseModelType(modelType),
        modelName = modelName,
        apiKey = apiKey,
        apiEndpoint = apiEndpoint,
        temperature = temperature,
        maxTokens = maxTokens,
        isDefault = isDefault,
        isEnabled = isEnabled,
        monthlyCost = monthlyCost,
        lastUsed = lastUsed,
        createdAt = createdAt
    )

    fun AIModelConfig.toEntity() = AIModelConfigEntity(
        id = id,
        modelType = modelType.name,
        modelName = modelName,
        apiKey = apiKey,
        apiEndpoint = apiEndpoint,
        temperature = temperature,
        maxTokens = maxTokens,
        isDefault = isDefault,
        isEnabled = isEnabled,
        monthlyCost = monthlyCost,
        lastUsed = lastUsed,
        createdAt = createdAt,
        tenantId = tenantId,
        syncVersion = syncVersion,
        deleted = deleted
    )

    // UserStyleProfile mapping
    fun UserStyleProfileEntity.toDomain() = UserStyleProfile(
        userId = userId,
        formalityLevel = formalityLevel,
        enthusiasmLevel = enthusiasmLevel,
        professionalismLevel = professionalismLevel,
        wordCountPreference = wordCountPreference,
        commonPhrases = parseJsonStringList(commonPhrases),
        avoidPhrases = parseJsonStringList(avoidPhrases),
        learningSamples = learningSamples,
        accuracyScore = accuracyScore,
        lastTrained = lastTrained,
        createdAt = createdAt
    )

    fun UserStyleProfile.toEntity() = UserStyleProfileEntity(
        userId = userId,
        formalityLevel = formalityLevel,
        enthusiasmLevel = enthusiasmLevel,
        professionalismLevel = professionalismLevel,
        wordCountPreference = wordCountPreference,
        commonPhrases = gson.toJson(commonPhrases),
        avoidPhrases = gson.toJson(avoidPhrases),
        learningSamples = learningSamples,
        accuracyScore = accuracyScore,
        lastTrained = lastTrained,
        createdAt = createdAt
    )

    // ReplyHistory mapping
    fun ReplyHistoryEntity.toDomain() = ReplyHistory(
        id = id,
        sourceApp = sourceApp,
        originalMessage = originalMessage,
        generatedReply = generatedReply,
        finalReply = finalReply,
        ruleMatchedId = ruleMatchedId,
        modelUsedId = modelUsedId,
        styleApplied = styleApplied,
        sendTime = sendTime,
        modified = modified
    )

    fun ReplyHistory.toEntity() = ReplyHistoryEntity(
        id = id,
        sourceApp = sourceApp,
        originalMessage = originalMessage,
        generatedReply = generatedReply,
        finalReply = finalReply,
        ruleMatchedId = ruleMatchedId,
        modelUsedId = modelUsedId,
        styleApplied = styleApplied,
        sendTime = sendTime,
        modified = modified
    )

    private fun parseJsonStringList(json: String): List<String> {
        return try {
            if (json.isBlank()) emptyList()
            else gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseModelType(value: String): ModelType {
        return try {
            ModelType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            try {
                ModelType.valueOf(value.uppercase())
            } catch (e2: IllegalArgumentException) {
                ModelType.OPENAI
            }
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T {
        return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
    }
}

