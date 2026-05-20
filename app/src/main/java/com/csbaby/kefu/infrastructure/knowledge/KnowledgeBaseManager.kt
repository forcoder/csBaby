package com.csbaby.kefu.infrastructure.knowledge

import android.util.Xml
import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.domain.model.MatchType
import com.csbaby.kefu.domain.model.ReplyContext
import com.csbaby.kefu.domain.model.RuleTargetType
import com.csbaby.kefu.domain.repository.KeywordRuleRepository
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Knowledge base manager for CRUD operations and import/export functionality.
 */
@Singleton
class KnowledgeBaseManager @Inject constructor(
    private val keywordRuleRepository: KeywordRuleRepository,
    private val keywordMatcher: KeywordMatcher
) {
    private val gson = Gson()

    // Default categories
    object Categories {
        const val PRE_SALES = "售前咨询"
        const val AFTER_SALES = "售后服务"
        const val COMPLAINT = "投诉处理"
        const val GENERAL = "通用问题"
        const val SHIPPING = "物流配送"
        const val PAYMENT = "支付问题"
        const val RETURN = "退换货"

        val ALL = listOf(PRE_SALES, AFTER_SALES, COMPLAINT, GENERAL, SHIPPING, PAYMENT, RETURN)
    }

    /**
     * Get all rules as Flow.
     */
    fun getAllRules(): Flow<List<KeywordRule>> = keywordRuleRepository.getAllRules()

    /**
     * Get enabled rules as Flow.
     */
    fun getEnabledRules(): Flow<List<KeywordRule>> = keywordRuleRepository.getEnabledRules()

    /**
     * Get all categories.
     */
    fun getAllCategories(): Flow<List<String>> = keywordRuleRepository.getAllCategories()

    /**
     * Get rules by category.
     */
    fun getRulesByCategory(category: String): Flow<List<KeywordRule>> =
        keywordRuleRepository.getRulesByCategory(category)

    /**
     * Get a single rule by ID.
     */
    suspend fun getRuleById(id: Long): KeywordRule? = keywordRuleRepository.getRuleById(id)

    /**
     * Create a new rule.
     */
    suspend fun createRule(rule: KeywordRule): Long = keywordRuleRepository.insertRule(rule)

    /**
     * Update an existing rule.
     */
    suspend fun updateRule(rule: KeywordRule) = keywordRuleRepository.updateRule(rule)

    /**
     * Delete a rule.
     */
    suspend fun deleteRule(id: Long) = keywordRuleRepository.deleteRule(id)

    /**
     * Clear all rules and return removed count.
     */
    suspend fun clearAllRules(): Int {
        val count = keywordRuleRepository.getRuleCount()
        if (count > 0) {
            keywordRuleRepository.deleteAllRules()
        }
        return count
    }

    /**
     * Toggle rule enabled status.
     */

    suspend fun toggleRule(id: Long, enabled: Boolean) {
        keywordRuleRepository.getRuleById(id)?.let { rule ->
            updateRule(rule.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
        }
    }

    /**
     * Search rules by keyword.
     */
    suspend fun searchRules(keyword: String): List<KeywordRule> =
        keywordRuleRepository.searchByKeyword(keyword)

    /**
     * Search rules by keyword with limit.
     */
    suspend fun searchRulesByKeyword(keyword: String, limit: Int = 10): List<KeywordRule> =
        keywordRuleRepository.searchByKeyword(keyword).take(limit)

    /**
     * Get rule count.
     */
    suspend fun getRuleCount(): Int = keywordRuleRepository.getRuleCount()

    /**
     * Initialize the keyword matcher with current rules (one-time initialization).
     */
    suspend fun initializeMatcher() {
        withContext(Dispatchers.IO) {
            val rules = keywordRuleRepository.getEnabledRules().first()
            keywordMatcher.initialize(rules)
        }
    }

    /**
     * Find matching rule for a message.
     */
    fun findMatch(message: String): KeywordMatcher.MatchedResult? {
        return keywordMatcher.findBestMatch(message)
    }

    fun findBestMatch(message: String): KeywordMatcher.MatchedResult? {
        return keywordMatcher.findBestMatch(message)
    }

    fun findBestMatch(message: String, context: ReplyContext): KeywordMatcher.MatchedResult? {
        return findAllMatches(message, context).firstOrNull()
    }

    /**
     * Find all matching rules for a message.
     */
    fun findAllMatches(message: String): List<KeywordMatcher.MatchedResult> {
        return keywordMatcher.findMatches(message)
    }

    fun findAllMatches(message: String, context: ReplyContext): List<KeywordMatcher.MatchedResult> {
        return keywordMatcher.findMatches(message)
            .filter { matchedResult -> isRuleApplicableToContext(matchedResult.rule, context) }
            .sortedWith(
                compareByDescending<KeywordMatcher.MatchedResult> { propertyMatchPriority(it.rule, context) }
                    .thenByDescending { it.rule.priority }
                    .thenByDescending { it.confidence }
                    .thenByDescending { it.matchedText.length }
            )
    }


    /**
     * Apply template with variables.
     * Supports variables like {price}, {name}, etc.
     */
    fun applyTemplate(template: String, variables: Map<String, String> = emptyMap()): String {
        return keywordMatcher.applyTemplate(template, variables)
    }

    /**
     * Generate reply from matched rule.
     */
    fun generateReplyFromRule(
        matchedResult: KeywordMatcher.MatchedResult,
        variables: Map<String, String> = emptyMap()
    ): String {
        return applyTemplate(matchedResult.rule.replyTemplate, variables)
    }

    /**
     * Export rules to JSON format.
     */
    suspend fun exportToJson(outputStream: OutputStream) {
        withContext(Dispatchers.IO) {
            val rules = keywordRuleRepository.getEnabledRules().first()
            val json = gson.toJson(ExportData(rules = rules))
            outputStream.write(json.toByteArray())
        }
    }

    /**
     * Export rules to CSV format.
     */
    suspend fun exportToCsv(outputStream: OutputStream) {
        withContext(Dispatchers.IO) {
            val sb = StringBuilder()
            sb.appendLine("keyword,match_type,reply_template,category,target_type,target_names,priority,enabled")

            keywordRuleRepository.getAllRules().first().forEach { rule ->
                sb.appendLine(
                    "\"${rule.keyword}\",${rule.matchType},\"${rule.replyTemplate.replace("\"", "\"\"")}\",\"${rule.category}\",${rule.targetType},\"${rule.targetNames.joinToString("|")}\",${rule.priority},${rule.enabled}"
                )
            }
            outputStream.write(sb.toString().toByteArray())
        }
    }

    /**
     * Import rules from JSON format.
     */
    suspend fun importFromJson(inputStream: InputStream): ImportResult {
        return withContext(Dispatchers.IO) {
            try {
                val json = inputStream.bufferedReader().use { it.readText() }
                val parsedRules = parseImportedRules(json)
                if (parsedRules.isEmpty()) {
                    return@withContext ImportResult(0, 0, "JSON 中没有可导入的规则")
                }

                var successCount = 0
                var errorCount = 0

                parsedRules.forEach { rule ->
                    try {
                        keywordRuleRepository.insertRule(
                            rule.copy(
                                id = 0,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        successCount++
                    } catch (e: Exception) {
                        errorCount++
                    }
                }

                ImportResult(successCount, errorCount)
            } catch (e: Exception) {
                ImportResult(0, 1, e.message)
            }
        }
    }

    /**
     * Import rules from CSV format.
     */
    suspend fun importFromCsv(inputStream: InputStream): ImportResult {
        return withContext(Dispatchers.IO) {
            try {
                val lines = inputStream.bufferedReader().readLines()
                importTabularRows(
                    rows = lines.map(::parseCsvLine),
                    emptyMessage = "CSV 中没有可导入的规则"
                )
            } catch (e: Exception) {
                ImportResult(0, 1, e.message)
            }
        }
    }

    /**
     * Import rules from Excel (.xlsx) format.
     */
    suspend fun importFromExcel(inputStream: InputStream): ImportResult {
        return withContext(Dispatchers.IO) {
            try {
                val worksheets = parseExcelWorksheets(inputStream)
                if (worksheets.isEmpty()) {
                    return@withContext ImportResult(0, 0, "Excel 中没有可读取的工作表")
                }

                var successCount = 0
                var errorCount = 0
                var parsedWorksheetCount = 0

                worksheets.forEach { rows ->
                    if (rows.none { row -> row.any { it.isNotBlank() } }) {
                        return@forEach
                    }

                    parsedWorksheetCount++
                    val result = importTabularRows(
                        rows = rows,
                        emptyMessage = "工作表中没有可导入的规则",
                        categoryColumnMeansProperty = true
                    )

                    successCount += result.successCount
                    errorCount += result.errorCount
                }

                when {
                    successCount == 0 && errorCount == 0 && parsedWorksheetCount == 0 -> {
                        ImportResult(0, 0, "Excel 中没有可导入的规则")
                    }
                    successCount == 0 && errorCount == 0 -> {
                        ImportResult(0, 0, "工作表中没有可导入的规则")
                    }
                    else -> {
                        ImportResult(successCount, errorCount)
                    }
                }
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("old .xls", ignoreCase = true) == true -> {
                        "暂不支持旧版 .xls，请另存为 .xlsx 后再导入"
                    }
                    else -> e.message
                }
                ImportResult(0, 1, errorMessage)
            }
        }
    }

    private suspend fun importTabularRows(
        rows: List<List<String>>,
        emptyMessage: String,
        categoryColumnMeansProperty: Boolean = false
    ): ImportResult {

        val normalizedRows = rows
            .map { row -> row.map { it.removePrefix("\uFEFF").trim() } }
            .filter { row -> row.any { it.isNotBlank() } }
        if (normalizedRows.isEmpty()) {
            return ImportResult(0, 0, emptyMessage)
        }

        val headerKeys = normalizedRows.firstOrNull()
            ?.takeIf(::isCsvHeaderRow)
            ?.map(::normalizeCsvHeader)
            .orEmpty()
        val dataRows = if (headerKeys.isNotEmpty()) normalizedRows.drop(1) else normalizedRows
        var successCount = 0
        var errorCount = 0

        dataRows.forEach { parts ->
            if (parts.all { it.isBlank() }) {
                return@forEach
            }

            try {
                val rule = if (headerKeys.isNotEmpty()) {
                    parseCsvRule(parts, headerKeys, categoryColumnMeansProperty)
                } else {
                    parseLegacyCsvRule(parts)
                }


                if (rule != null) {
                    keywordRuleRepository.insertRule(rule)
                    successCount++
                } else {
                    errorCount++
                }
            } catch (e: Exception) {
                errorCount++
            }
        }

        return if (successCount == 0 && errorCount == 0) {
            ImportResult(0, 0, emptyMessage)
        } else {
            ImportResult(successCount, errorCount)
        }
    }





    private fun isRuleApplicableToContext(rule: KeywordRule, context: ReplyContext): Boolean {
        return when (rule.targetType) {
            RuleTargetType.ALL -> true
            RuleTargetType.CONTACT -> {
                if (context.isGroupConversation == true) return false
                matchesConversationTarget(rule.targetNames, context.conversationTitle) ||
                    matchesLegacyPropertyTarget(rule, context)
            }
            RuleTargetType.GROUP -> {
                if (context.isGroupConversation == false) return false
                matchesConversationTarget(rule.targetNames, context.conversationTitle)
            }
            RuleTargetType.PROPERTY -> {
                matchesConversationTarget(rule.targetNames, context.propertyName)
            }
        }
    }


    private fun matchesConversationTarget(targetNames: List<String>, conversationTitle: String?): Boolean {
        if (targetNames.isEmpty()) return true
        val normalizedConversationTitle = conversationTitle.normalizeForMatch()
        if (normalizedConversationTitle.isBlank()) return false

        return targetNames.any { targetName ->
            val normalizedTargetName = targetName.normalizeForMatch()
            normalizedTargetName.isNotBlank() &&
                (normalizedConversationTitle == normalizedTargetName ||
                    normalizedConversationTitle.contains(normalizedTargetName))
        }
    }

    private fun matchesLegacyPropertyTarget(rule: KeywordRule, context: ReplyContext): Boolean {
        if (context.appPackage.isBlank()) return false
        return context.propertyName.normalizeForMatch().isNotBlank() &&
            context.appPackage == "com.myhostex.hostexapp" &&
            matchesConversationTarget(rule.targetNames, context.propertyName)
    }

    private fun propertyMatchPriority(rule: KeywordRule, context: ReplyContext): Int {
        return when {
            rule.targetType == RuleTargetType.PROPERTY && matchesConversationTarget(rule.targetNames, context.propertyName) -> 3
            matchesLegacyPropertyTarget(rule, context) -> 2
            rule.targetType != RuleTargetType.ALL && rule.targetNames.isNotEmpty() -> 1
            else -> 0
        }
    }

    private fun String?.normalizeForMatch(): String {
        return this.orEmpty().trim().lowercase()
    }


    private fun parseImportedRules(json: String): List<KeywordRule> {
        val normalizedJson = json.removePrefix("\uFEFF").trim()
        if (normalizedJson.isBlank()) return emptyList()

        val parsedRoot = runCatching { JsonParser().parse(normalizedJson) }.getOrNull()
        val ruleObjects: List<JsonObject> = when {
            parsedRoot == null -> parseJsonLines(normalizedJson)
            parsedRoot.isJsonArray -> {
                parsedRoot.asJsonArray.mapNotNull { element ->
                    element.takeIf { it.isJsonObject }?.asJsonObject
                }
            }
            parsedRoot.isJsonObject -> {
                val jsonObject = parsedRoot.asJsonObject
                when {
                    jsonObject.has("rules") && jsonObject.get("rules").isJsonArray -> {
                        jsonObject.getAsJsonArray("rules").mapNotNull { element ->
                            element.takeIf { it.isJsonObject }?.asJsonObject
                        }
                    }
                    else -> listOf(jsonObject)
                }
            }
            else -> emptyList()
        }

        val parsedRules = mutableListOf<KeywordRule>()
        for (ruleObject in ruleObjects) {
            runCatching { parseRuleObject(ruleObject) }
                .getOrNull()
                ?.let(parsedRules::add)
        }
        return parsedRules
    }


    private fun parseJsonLines(json: String): List<JsonObject> {
        return json.lineSequence()
            .map { it.trim().trimEnd(',') }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                runCatching {
                    JsonParser().parse(line).takeIf { it.isJsonObject }?.asJsonObject
                }.getOrNull()
            }
            .toList()
    }

    private fun parseRuleObject(jsonObject: JsonObject): KeywordRule? {
        val triggerCondition = jsonObject.readString("trigger_condition", "triggerCondition")
        val matchType = parseMatchType(
            jsonObject.readString("matchType", "match_type").ifBlank {
                inferMatchTypeFromTriggerCondition(triggerCondition)
            }
        )
        val keywordText = extractImportedKeywordText(
            directKeyword = jsonObject.readString("keyword"),
            triggerCondition = triggerCondition,
            matchType = matchType
        )
        val replyTemplate = jsonObject.readString("replyTemplate", "reply_template", "reply_content")
        if (keywordText.isBlank() || replyTemplate.isBlank()) {
            return null
        }

        val importedTargets = resolveImportedTargets(
            targetTypeValue = jsonObject.readString("targetType", "target_type"),
            propertyTargetNames = jsonObject.readStringList(
                "applicable_properties",
                "applicableProperties",
                "applicablePropertiesJson"
            ),
            genericTargetNames = jsonObject.readStringList("targetNames", "target_names", "targetNamesJson")
        )
        val category = jsonObject.readString("category").ifBlank {
            inferCategoryFromTriggerType(jsonObject.readString("trigger_type", "triggerType"))
        }
        val applicableScenarios = jsonObject.readLongList("applicableScenarios", "scenarioIds", "scenario_ids")
        val priority = jsonObject.readInt("priority", defaultValue = 0)
        val enabled = parseEnabledValue(jsonObject)
        val now = System.currentTimeMillis()

        return KeywordRule(
            keyword = keywordText,
            matchType = matchType,
            replyTemplate = replyTemplate,
            category = category,
            applicableScenarios = applicableScenarios,
            targetType = importedTargets.targetType,
            targetNames = importedTargets.targetNames,
            priority = priority,
            enabled = enabled,
            createdAt = now,
            updatedAt = now
        )
    }



    private fun parseMatchType(value: String?): MatchType {
        return runCatching { MatchType.valueOf(value?.uppercase().orEmpty()) }
            .getOrDefault(MatchType.CONTAINS)
    }

    private fun inferMatchTypeFromTriggerCondition(triggerCondition: String): String {
        return if (triggerCondition.contains("正则")) {
            MatchType.REGEX.name
        } else {
            MatchType.CONTAINS.name
        }
    }

    private fun inferCategoryFromTriggerType(triggerType: String): String {
        return when (triggerType.trim().lowercase()) {
            "inquiry_question", "咨询问题" -> Categories.PRE_SALES
            else -> Categories.GENERAL
        }
    }

    private fun extractImportedKeywordText(
        directKeyword: String,
        triggerCondition: String,
        matchType: MatchType
    ): String {
        if (directKeyword.isNotBlank()) {
            return normalizeImportedKeywordText(stripKeywordLabelPrefix(directKeyword), matchType)
        }

        val payload = extractTriggerConditionPayload(triggerCondition) ?: return ""
        return normalizeImportedKeywordText(payload, matchType)
    }

    private fun stripKeywordLabelPrefix(rawValue: String): String {
        val normalizedValue = rawValue.removePrefix("\uFEFF").trim()
        val prefixes = listOf("关键字", "关键词")
        prefixes.forEach { prefix ->
            if (normalizedValue.startsWith(prefix)) {
                return normalizedValue.removePrefix(prefix)
                    .trimStart(':', '：', ' ', '\t')
                    .trim()
            }
        }
        return normalizedValue
    }

    private fun extractTriggerConditionPayload(triggerCondition: String): String? {

        val normalizedCondition = triggerCondition.trim()
        val prefixes = listOf("关键字", "关键词", "咨询问题")
        prefixes.forEach { prefix ->
            if (normalizedCondition.startsWith(prefix)) {
                val payload = normalizedCondition.removePrefix(prefix)
                    .trimStart(':', '：', ' ', '\t')
                    .trim()
                return payload.ifBlank { null }
            }
        }
        return null
    }

    private fun parseEnabledValue(jsonObject: JsonObject): Boolean {
        if (jsonObject.has("enabled")) {
            return jsonObject.readBoolean("enabled", defaultValue = true)
        }

        return parseEnabledValue(
            enabledValue = null,
            statusValue = jsonObject.readString("status")
        )
    }

    private fun parseEnabledValue(enabledValue: String?, statusValue: String?): Boolean {
        enabledValue?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
            return when (value.lowercase()) {
                "true", "1", "yes", "y", "enabled", "enable", "启用" -> true
                "false", "0", "no", "n", "disabled", "disable", "禁用" -> false
                else -> true
            }
        }

        return when (statusValue?.trim()?.lowercase().orEmpty()) {
            "", "启用", "enabled", "enable", "true", "1" -> true
            "禁用", "disabled", "disable", "false", "0" -> false
            else -> true
        }
    }

    private fun parseRuleTargetType(value: String?): RuleTargetType {
        return when (value?.trim()?.uppercase()) {
            "CONTACT", "PERSON", "USER", "PRIVATE" -> RuleTargetType.CONTACT
            "GROUP", "CHATROOM", "ROOM" -> RuleTargetType.GROUP
            "PROPERTY", "LISTING", "HOUSE", "HOME", "ACCOMMODATION", "房源", "民宿" -> RuleTargetType.PROPERTY
            else -> RuleTargetType.ALL
        }
    }

    private fun resolveImportedTargets(
        targetTypeValue: String?,
        propertyTargetNames: List<String>,
        genericTargetNames: List<String>
    ): ImportedTargets {
        val normalizedPropertyTargetNames = propertyTargetNames.distinct()
        if (normalizedPropertyTargetNames.isNotEmpty()) {
            return ImportedTargets(
                targetType = RuleTargetType.PROPERTY,
                targetNames = normalizedPropertyTargetNames
            )
        }

        val normalizedTargetNames = genericTargetNames.distinct()
        return ImportedTargets(
            targetType = resolveImportedTargetType(targetTypeValue, normalizedTargetNames),
            targetNames = normalizedTargetNames
        )
    }

    private fun resolveImportedTargetType(value: String?, targetNames: List<String>): RuleTargetType {
        val parsedType = parseRuleTargetType(value)
        if (parsedType != RuleTargetType.ALL) {
            return parsedType
        }
        return if (targetNames.isNotEmpty()) RuleTargetType.PROPERTY else RuleTargetType.ALL
    }

    private fun normalizeImportedKeywordText(rawKeyword: String, matchType: MatchType): String {
        val normalized = rawKeyword.removePrefix("\uFEFF").trim()
        if (normalized.isBlank()) return ""
        if (matchType == MatchType.REGEX) return normalized

        return normalized.split(Regex("[,，、|\n]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
    }

    private fun parseImportedTargetNames(raw: String?): List<String> {
        val normalized = raw.orEmpty().trim()
        if (normalized.isBlank()) return emptyList()
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            return runCatching {
                JsonParser().parse(normalized).asJsonArray.mapNotNull { item ->
                    runCatching { item.asString.trim() }.getOrNull()?.takeIf { it.isNotBlank() }
                }
            }.getOrElse { emptyList() }
        }

        return normalized.split(Regex("[,，|\n]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private data class ImportedTargets(
        val targetType: RuleTargetType,
        val targetNames: List<String>
    )




    private fun JsonObject.readString(vararg keys: String): String {
        keys.forEach { key ->
            val value = get(key) ?: return@forEach
            if (value.isJsonNull) return@forEach
            if (value.isJsonPrimitive) {
                return runCatching { value.asString.trim() }.getOrDefault("")
            }
        }
        return ""
    }

    private fun JsonObject.readInt(key: String, defaultValue: Int): Int {
        val value = get(key) ?: return defaultValue
        return runCatching { value.asInt }.getOrDefault(defaultValue)
    }

    private fun JsonObject.readBoolean(key: String, defaultValue: Boolean): Boolean {
        val value = get(key) ?: return defaultValue
        return runCatching { value.asBoolean }.getOrDefault(defaultValue)
    }

    private fun JsonObject.readLongList(vararg keys: String): List<Long> {
        keys.forEach { key ->
            val value = get(key) ?: return@forEach
            if (value.isJsonArray) {
                return value.asJsonArray.mapNotNull { item ->
                    runCatching { item.asLong }.getOrNull()
                }
            }
        }
        return emptyList()
    }

    private fun JsonObject.readStringList(vararg keys: String): List<String> {
        keys.forEach { key ->
            val value = get(key) ?: return@forEach
            if (value.isJsonNull) return@forEach
            when {
                value.isJsonArray -> {
                    return value.asJsonArray.mapNotNull { item ->
                        runCatching { item.asString.trim() }.getOrNull()?.takeIf { it.isNotBlank() }
                    }
                }
                value.isJsonPrimitive -> {
                    return parseImportedTargetNames(
                        runCatching { value.asString.trim() }.getOrDefault("")
                    )
                }
            }
        }
        return emptyList()
    }

    private fun parseCsvRule(
        parts: List<String>,
        headerKeys: List<String>,
        categoryColumnMeansProperty: Boolean = false
    ): KeywordRule? {
        val row = buildMap<String, String> {
            headerKeys.forEachIndexed { index, key ->
                put(key, parts.getOrNull(index).orEmpty())
            }
        }

        val triggerCondition = row.readCsvValue("trigger_condition", "triggercondition", "触发条件")
        val triggerType = row.readCsvValue("trigger_type", "triggertype", "触发类型")
        val matchType = parseMatchType(
            row.readCsvValue("match_type", "matchtype").ifBlank {
                if (triggerType == "关键词回复") MatchType.CONTAINS.name
                else inferMatchTypeFromTriggerCondition(triggerCondition)
            }
        )
        val keywordText = extractImportedKeywordText(
            directKeyword = row.readCsvValue("keyword", "rule_title", "ruletitle", "规则标题"),
            triggerCondition = triggerCondition,
            matchType = matchType
        )
        val replyTemplate = row.readCsvValue(
            "reply_template",
            "replytemplate",
            "reply_content",
            "replycontent",
            "回复内容"
        )

        // 对"询问问题"等非关键词类型：keyword 从触发条件里提，回复内容即模板
        val resolvedKeywordText = if (keywordText.isBlank() && triggerCondition.isNotBlank()) {
            val trimmedCondition = triggerCondition.removePrefix("\uFEFF").trim()
            // 触发条件本身就是关键词（不含"关键字:"前缀时，直接当关键词用）
            normalizeImportedKeywordText(trimmedCondition, matchType)
        } else {
            keywordText
        }

        if (resolvedKeywordText.isBlank() || replyTemplate.isBlank()) {
            return null
        }


        val propertyTargetNames = parseImportedTargetNames(
            row.readCsvValue(
                "applicable_properties",
                "applicableproperties",
                "applicable_property",
                "property_names",
                "propertynames",
                "适用房源"
            ).ifBlank {
                if (categoryColumnMeansProperty) row.readCsvValue("category") else ""
            }
        )
        val importedTargets = resolveImportedTargets(
            targetTypeValue = row.readCsvValue("target_type", "targettype"),
            propertyTargetNames = propertyTargetNames,
            genericTargetNames = parseImportedTargetNames(
                row.readCsvValue("target_names", "targetnames")
            )
        )
        val now = System.currentTimeMillis()

        return KeywordRule(
            keyword = resolvedKeywordText,
            matchType = matchType,
            replyTemplate = replyTemplate,
            category = row.readCsvValue("rule_category", "rulecategory", "规则分类").ifBlank {
                if (categoryColumnMeansProperty) {
                    inferCategoryFromTriggerType(triggerType)
                } else {
                    row.readCsvValue("category").ifBlank {
                        inferCategoryFromTriggerType(triggerType)
                    }
                }
            },
            applicableScenarios = emptyList(),
            targetType = importedTargets.targetType,
            targetNames = importedTargets.targetNames,
            priority = row.readCsvValue("priority").toIntOrNull() ?: 0,
            enabled = parseEnabledValue(
                enabledValue = row.readCsvValue("enabled"),
                statusValue = row.readCsvValue("status", "状态")
            ),
            createdAt = now,
            updatedAt = now
        )
    }



    private fun parseLegacyCsvRule(parts: List<String>): KeywordRule? {
        if (parts.size < 4) return null

        val matchType = parseMatchType(parts.getOrNull(1))
        val importedTargets = resolveImportedTargets(
            targetTypeValue = parts.getOrNull(4),
            propertyTargetNames = emptyList(),
            genericTargetNames = parseImportedTargetNames(parts.getOrNull(5))
        )
        val now = System.currentTimeMillis()

        return KeywordRule(
            keyword = normalizeImportedKeywordText(parts[0], matchType),
            matchType = matchType,
            replyTemplate = parts[2],
            category = parts[3],
            targetType = importedTargets.targetType,
            targetNames = importedTargets.targetNames,
            priority = parts.getOrNull(6)?.toIntOrNull() ?: 0,
            enabled = parseEnabledValue(enabledValue = parts.getOrNull(7), statusValue = null),
            createdAt = now,
            updatedAt = now
        )
    }

    private fun isCsvHeaderRow(parts: List<String>): Boolean {
        val normalizedKeys = parts.map(::normalizeCsvHeader).toSet()
        val knownHeaders = setOf(
            // 英文/系统字段
            "keyword",
            "rule_title",
            "ruletitle",
            "match_type",
            "matchtype",
            "reply_template",
            "replytemplate",
            "reply_content",
            "replycontent",
            "category",
            "rule_category",
            "rulecategory",
            "target_type",
            "targettype",
            "target_names",
            "targetnames",
            "applicable_properties",
            "applicableproperties",
            "trigger_condition",
            "triggercondition",
            "trigger_type",
            "triggertype",
            "enabled",
            "status",
            "priority",
            // 中文表头（精确匹配，不走 lowercase）
            "规则标题",
            "回复内容",
            "适用房源",
            "规则分类",
            "触发条件",
            "触发类型",
            "状态",
            "规则名称",
            "规则id",
            "适用阶段",
            "适用渠道"
        )

        return normalizedKeys.any { it in knownHeaders } ||
            parts.any { it.trim() in setOf("回复内容", "适用房源", "触发条件", "触发类型", "状态", "规则名称") }

    }

    private fun normalizeCsvHeader(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.any { it.code > 0x7F }) {
            // 含非 ASCII 字符（中文等）：只去首尾空格，保留原始字符
            trimmed
        } else {
            trimmed.lowercase()
                .replace("-", "_")
                .replace(" ", "")
        }
    }


    private fun Map<String, String>.readCsvValue(vararg keys: String): String {
        keys.forEach { key ->
            this[key]?.trim()?.let { value ->
                if (value.isNotEmpty()) return value
            }
        }
        return ""
    }

    private fun parseCsvLine(line: String): List<String> {

        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && index + 1 < line.length && line[index + 1] == '"' -> {
                    sb.append('"')
                    index += 1
                }
                char == '"' -> {
                    inQuotes = !inQuotes
                }
                char == ',' && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(char)
            }
            index += 1
        }
        result.add(sb.toString().trim())

        return result
    }

    private fun parseExcelWorksheets(inputStream: InputStream): List<List<List<String>>> {
        val worksheetEntries = mutableListOf<Pair<String, ByteArray>>()
        var sharedStrings = emptyList<String>()

        ZipInputStream(BufferedInputStream(inputStream)).use { zipInputStream ->
            generateSequence { zipInputStream.nextEntry }.forEach { entry ->
                val entryBytes = zipInputStream.readBytes()
                when {
                    entry.name == "xl/sharedStrings.xml" -> {
                        sharedStrings = parseSharedStrings(entryBytes)
                    }
                    entry.name.startsWith("xl/worksheets/") && entry.name.endsWith(".xml") -> {
                        worksheetEntries += entry.name to entryBytes
                    }
                }
                zipInputStream.closeEntry()
            }
        }

        return worksheetEntries
            .sortedBy { it.first }
            .map { (_, bytes) -> parseWorksheetRows(bytes, sharedStrings) }
            .filter { rows -> rows.any { row -> row.any { it.isNotBlank() } } }
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val parser = Xml.newPullParser().apply {
            setInput(ByteArrayInputStream(bytes), Charsets.UTF_8.name())
        }
        val sharedStrings = mutableListOf<String>()
        var currentText: StringBuilder? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        currentText = StringBuilder()
                    }
                }
                XmlPullParser.TEXT -> {
                    currentText?.append(parser.text.orEmpty())
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si") {
                        sharedStrings += currentText?.toString().orEmpty()
                        currentText = null
                    }
                }
            }
            eventType = parser.next()
        }

        return sharedStrings
    }

    private fun parseWorksheetRows(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val parser = Xml.newPullParser().apply {
            setInput(ByteArrayInputStream(bytes), Charsets.UTF_8.name())
        }
        val rows = mutableListOf<List<String>>()
        var currentRow = linkedMapOf<Int, String>()
        var currentCellColumn = -1
        var currentCellType: String? = null
        var currentCellValue = StringBuilder()
        var readingValueTag = false
        var readingInlineTextTag = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRow = linkedMapOf()
                        "c" -> {
                            currentCellColumn = columnIndexFromCellReference(parser.getAttributeValue(null, "r"))
                            currentCellType = parser.getAttributeValue(null, "t")
                            currentCellValue = StringBuilder()
                        }
                        "v" -> readingValueTag = true
                        "t" -> if (currentCellType == "inlineStr") {
                            readingInlineTextTag = true
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (readingValueTag || readingInlineTextTag) {
                        currentCellValue.append(parser.text.orEmpty())
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> readingValueTag = false
                        "t" -> readingInlineTextTag = false
                        "c" -> {
                            val resolvedValue = resolveWorksheetCellValue(
                                rawValue = currentCellValue.toString(),
                                cellType = currentCellType,
                                sharedStrings = sharedStrings
                            )
                            if (currentCellColumn >= 0) {
                                currentRow[currentCellColumn] = resolvedValue
                            }
                            currentCellColumn = -1
                            currentCellType = null
                            currentCellValue = StringBuilder()
                        }
                        "row" -> {
                            if (currentRow.isNotEmpty()) {
                                rows += currentRow.toDenseRow()
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return rows
    }

    private fun resolveWorksheetCellValue(
        rawValue: String,
        cellType: String?,
        sharedStrings: List<String>
    ): String {
        val normalizedValue = rawValue.trim()
        return when (cellType) {
            "s" -> sharedStrings.getOrNull(normalizedValue.toIntOrNull() ?: -1).orEmpty()
            "b" -> if (normalizedValue == "1") "true" else "false"
            else -> normalizedValue
        }
    }

    private fun columnIndexFromCellReference(cellReference: String?): Int {
        val columnName = cellReference.orEmpty().takeWhile { it.isLetter() }.uppercase()
        if (columnName.isBlank()) return -1

        var result = 0
        columnName.forEach { char ->
            result = result * 26 + (char - 'A' + 1)
        }
        return result - 1
    }

    private fun Map<Int, String>.toDenseRow(): List<String> {
        val lastColumnIndex = keys.maxOrNull() ?: return emptyList()
        return (0..lastColumnIndex).map { index -> this[index].orEmpty().trim() }
    }

    data class ImportResult(

        val successCount: Int,
        val errorCount: Int,
        val errorMessage: String? = null
    )

    private data class ExportData(
        val version: Int = 2,
        val exportTime: Long = System.currentTimeMillis(),
        val rules: List<KeywordRule>
    )
}
