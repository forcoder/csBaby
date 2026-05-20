package com.csbaby.kefu.infrastructure.knowledge

import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.domain.model.MatchType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keyword matching engine with Trie tree optimization for efficient matching.
 */
@Singleton
class KeywordMatcher @Inject constructor() {

    private val trieRoot = TrieNode()
    private var rules: List<KeywordRule> = emptyList()
    private val trieMutex = Mutex()

    /**
     * Initialize the matcher with a list of rules.
     * Rebuilds the Trie tree for efficient matching.
     */
    suspend fun initialize(rules: List<KeywordRule>) {
        trieMutex.withLock {
            this.rules = rules
            rebuildTrie()
        }
    }

    /**
     * Rebuild the Trie tree from current rules.
     */
    private fun rebuildTrie() {
        trieRoot.children.clear()
        trieRoot.isEndOfWord = false
        trieRoot.ruleIds.clear()

        rules.filter { it.enabled }.forEach { rule ->
            when (rule.matchType) {
                MatchType.EXACT, MatchType.CONTAINS -> {
                    extractKeywordAliases(rule).forEach { alias ->
                        insertKeyword(alias, rule.id, rule.priority)
                    }
                }
                MatchType.REGEX -> {
                    // Regex patterns are stored separately and matched differently
                    insertRegexPattern(rule.keyword, rule.id, rule.priority)
                }
            }
        }
    }


    private fun insertKeyword(keyword: String, ruleId: Long, priority: Int) {
        var node = trieRoot
        for (char in keyword) {
            val lowerChar = char.lowercaseChar()
            if (!node.children.containsKey(lowerChar)) {
                node.children[lowerChar] = TrieNode()
            }
            node = node.children[lowerChar]!!
        }
        node.isEndOfWord = true
        node.ruleIds.add(MatchedRule(ruleId, priority))
    }

    private fun insertRegexPattern(pattern: String, ruleId: Long, priority: Int) {
        // Store regex patterns separately for later matching
        // This is handled in the matchWithRegex method
    }

    /**
     * Find all matching rules for the given message.
     * Returns rules sorted by priority (higher priority first).
     */
    fun findMatches(message: String): List<MatchedResult> {
        val results = mutableListOf<MatchedResult>()

        // Trie-based matching for exact and contains
        results.addAll(trieMatch(message))

        // Regex matching
        results.addAll(matchWithRegex(message))

        return results
            .groupBy { it.rule.id }
            .mapNotNull { (_, matches) ->
                matches.maxWithOrNull(
                    compareBy<MatchedResult> { it.confidence }
                        .thenBy { it.matchedText.length }
                )
            }
            .sortedByDescending { it.confidence }
    }


    /**
     * Find the best matching rule for the given message.
     */
    fun findBestMatch(message: String): MatchedResult? {
        return findMatches(message).firstOrNull()
    }

    private fun trieMatch(message: String): List<MatchedResult> {
        val results = mutableListOf<MatchedResult>()
        val lowerMessage = message.lowercase()

        // Check each position for potential matches
        for (startIndex in lowerMessage.indices) {
            var node = trieRoot

            for (endIndex in startIndex until lowerMessage.length) {
                val char = lowerMessage[endIndex]
                val child = node.children[char] ?: break

                node = child
                if (node.isEndOfWord) {
                    val matchedText = message.substring(startIndex, endIndex + 1)
                    node.ruleIds.forEach { matchedRule ->
                        val rule = rules.find { it.id == matchedRule.ruleId }
                        if (rule != null) {
                            results.add(
                                MatchedResult(
                                    rule = rule,
                                    matchedText = matchedText,
                                    matchStart = startIndex,
                                    matchEnd = endIndex,
                                    confidence = calculateConfidence(rule, matchedText, message)
                                )
                            )
                        }
                    }
                }
            }
        }

        return results
    }


    private fun matchWithRegex(message: String): List<MatchedResult> {
        val results = mutableListOf<MatchedResult>()

        rules.filter { it.matchType == MatchType.REGEX && it.enabled }.forEach { rule ->
            try {
                val regex = Regex(rule.keyword, RegexOption.IGNORE_CASE)
                regex.findAll(message).forEach { match ->
                    results.add(
                        MatchedResult(
                            rule = rule,
                            matchedText = match.value,
                            matchStart = match.range.first,
                            matchEnd = match.range.last,
                            confidence = calculateConfidence(rule, match.value, message)
                        )
                    )
                }
            } catch (e: Exception) {
                // Invalid regex, skip
            }
        }

        return results
    }

    private fun extractKeywordAliases(rule: KeywordRule): List<String> {
        return if (rule.matchType == MatchType.REGEX) {
            listOf(rule.keyword)
        } else {
            rule.keyword.split(Regex("[,，、|\n]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .ifEmpty { listOf(rule.keyword.trim()) }
        }
    }


    /**
     * Calculate match confidence score.
     * Higher score means better match.
     */
    private fun calculateConfidence(rule: KeywordRule, matchedText: String, fullMessage: String): Float {
        // Length ratio: how much of the message is matched
        val lengthRatio = matchedText.length.toFloat() / fullMessage.length

        // Priority factor: higher priority gets slight boost
        val priorityFactor = (rule.priority.coerceIn(0, 10) / 10f) * 0.2f

        // Match type factor: exact match is more confident
        val matchTypeFactor = when (rule.matchType) {
            MatchType.EXACT -> 0.3f
            MatchType.CONTAINS -> 0.2f
            MatchType.REGEX -> 0.25f
        }

        return (lengthRatio + priorityFactor + matchTypeFactor).coerceIn(0f, 1f)
    }

    /**
     * Apply variable substitution to reply template.
     * Supports variables like {price}, {name}, etc.
     */
    fun applyTemplate(template: String, variables: Map<String, String> = emptyMap()): String {
        var result = template
        variables.forEach { (key, value) ->
            result = result.replace("{$key}", value, ignoreCase = true)
        }
        return result
    }

    private class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        var isEndOfWord = false
        val ruleIds = mutableListOf<MatchedRule>()
    }

    private data class MatchedRule(
        val ruleId: Long,
        val priority: Int
    )

    data class MatchedResult(
        val rule: KeywordRule,
        val matchedText: String,
        val matchStart: Int,
        val matchEnd: Int,
        val confidence: Float
    )
}
