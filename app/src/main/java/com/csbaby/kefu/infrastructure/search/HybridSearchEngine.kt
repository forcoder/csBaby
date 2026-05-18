package com.csbaby.kefu.infrastructure.search

import android.util.Log
import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.domain.model.ReplyContext
import com.csbaby.kefu.infrastructure.knowledge.KeywordMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hybrid Search Engine - Combines keyword and semantic search.
 * 
 * Search Strategy:
 * 1. Keyword Search: Fast, precise matching using Trie tree
 * 2. Semantic Search: Meaning-based matching using embeddings
 * 3. Hybrid: Reciprocal Rank Fusion (RRF) for combining results
 */
@Singleton
class HybridSearchEngine @Inject constructor(
    private val keywordMatcher: KeywordMatcher,
    private val vectorStore: VectorStore
) {
    companion object {
        private const val TAG = "HybridSearchEngine"
        
        // RRF constant for rank fusion
        private const val RRF_K = 60
        
        // Default weights for hybrid search
        const val DEFAULT_KEYWORD_WEIGHT = 0.6f
        const val DEFAULT_SEMANTIC_WEIGHT = 0.4f
        
        // Similarity threshold for semantic results
        const val DEFAULT_SEMANTIC_THRESHOLD = 0.65f
    }
    
    /**
     * Search mode configuration
     */
    enum class SearchMode {
        KEYWORD_ONLY,    // Only use keyword matching
        SEMANTIC_ONLY,   // Only use vector similarity
        HYBRID          // Combine both (default)
    }
    
    /**
     * Hybrid search result with combined scoring.
     */
    data class HybridSearchResult(
        val rule: KeywordRule,
        val keywordScore: Float = 0f,
        val semanticScore: Float = 0f,
        val combinedScore: Float,
        val matchedText: String? = null,
        val matchType: SearchType
    )
    
    /**
     * Perform hybrid search combining keyword and semantic matching.
     */
    suspend fun search(
        query: String,
        context: ReplyContext? = null,
        mode: SearchMode = SearchMode.HYBRID,
        keywordWeight: Float = DEFAULT_KEYWORD_WEIGHT,
        semanticWeight: Float = DEFAULT_SEMANTIC_WEIGHT,
        maxResults: Int = 10
    ): List<HybridSearchResult> = withContext(Dispatchers.IO) {
        
        Log.d(TAG, "Hybrid search: query='${query.take(50)}...', mode=$mode")
        
        when (mode) {
            SearchMode.KEYWORD_ONLY -> {
                keywordSearch(query, context, maxResults)
            }
            SearchMode.SEMANTIC_ONLY -> {
                semanticSearch(query, maxResults)
            }
            SearchMode.HYBRID -> {
                hybridSearch(query, context, keywordWeight, semanticWeight, maxResults)
            }
        }
    }
    
    /**
     * Keyword-only search using Trie tree.
     */
    private fun keywordSearch(
        query: String,
        context: ReplyContext?,
        maxResults: Int
    ): List<HybridSearchResult> {
        val matches = if (context != null) {
            keywordMatcher.findMatches(query)
                .filter { isRuleApplicable(it.rule, context) }
        } else {
            keywordMatcher.findMatches(query)
        }
        
        return matches
            .sortedByDescending { it.confidence }
            .take(maxResults)
            .map { match ->
                HybridSearchResult(
                    rule = match.rule,
                    keywordScore = match.confidence,
                    combinedScore = match.confidence,
                    matchedText = match.matchedText,
                    matchType = SearchType.KEYWORD
                )
            }
    }
    
    /**
     * Semantic-only search using vector embeddings.
     */
    private suspend fun semanticSearch(
        query: String,
        maxResults: Int
    ): List<HybridSearchResult> {
        val results = vectorStore.search(
            query = query,
            threshold = DEFAULT_SEMANTIC_THRESHOLD,
            maxResults = maxResults
        )
        
        return results.map { result ->
            HybridSearchResult(
                rule = result.rule,
                semanticScore = result.similarity,
                combinedScore = result.similarity,
                matchType = SearchType.SEMANTIC
            )
        }
    }
    
    /**
     * Hybrid search using Reciprocal Rank Fusion (RRF).
     */
    private suspend fun hybridSearch(
        query: String,
        context: ReplyContext?,
        keywordWeight: Float,
        semanticWeight: Float,
        maxResults: Int
    ): List<HybridSearchResult> = coroutineScope {
        // Run both searches in parallel
        val keywordDeferred = async {
            keywordMatcher.findMatches(query).let { matches ->
                if (context != null) {
                    matches.filter { isRuleApplicable(it.rule, context) }
                } else {
                    matches
                }
            }
        }
        
        val semanticDeferred = async {
            vectorStore.search(
                query = query,
                threshold = 0.5f, // Lower threshold for hybrid
                maxResults = maxResults * 2
            )
        }
        
        // Wait for both results
        val keywordResults = keywordDeferred.await()
        val semanticResults = semanticDeferred.await()
        
        Log.d(TAG, "Keyword: ${keywordResults.size} results, Semantic: ${semanticResults.size} results")
        
        // Apply RRF fusion
        fuseResults(
            keywordResults = keywordResults,
            semanticResults = semanticResults,
            keywordWeight = keywordWeight,
            semanticWeight = semanticWeight,
            maxResults = maxResults
        )
    }
    
    /**
     * Fuse keyword and semantic results using weighted RRF.
     */
    private fun fuseResults(
        keywordResults: List<KeywordMatcher.MatchedResult>,
        semanticResults: List<VectorStore.VectorSearchResult>,
        keywordWeight: Float,
        semanticWeight: Float,
        maxResults: Int
    ): List<HybridSearchResult> {
        // Build rule ID to result mappings
        val ruleScores = mutableMapOf<Long, MutableList<ScoreInfo>>()
        
        // Add keyword results with rank-based scoring
        keywordResults.sortedByDescending { it.confidence }.forEachIndexed { index, match ->
            val rrfScore = keywordWeight / (RRF_K + index + 1)
            ruleScores.getOrPut(match.rule.id) { mutableListOf() }.add(
                ScoreInfo(
                    keywordScore = match.confidence,
                    keywordRank = index,
                    rrfContribution = rrfScore,
                    matchedText = match.matchedText
                )
            )
        }
        
        // Add semantic results with rank-based scoring
        semanticResults.forEachIndexed { index, result ->
            val rrfScore = semanticWeight / (RRF_K + index + 1)
            ruleScores.getOrPut(result.rule.id) { mutableListOf() }.add(
                ScoreInfo(
                    semanticScore = result.similarity,
                    semanticRank = index,
                    rrfContribution = rrfScore
                )
            )
        }
        
        // Calculate combined scores
        val fusedResults = ruleScores.mapNotNull { (ruleId, scores) ->
            // Find the rule from either result set
            val rule = keywordResults.find { it.rule.id == ruleId }?.rule
                ?: semanticResults.find { it.rule.id == ruleId }?.rule
                ?: return@mapNotNull null
            
            val bestKeywordScore = scores.maxOfOrNull { it.keywordScore } ?: 0f
            val bestSemanticScore = scores.maxOfOrNull { it.semanticScore } ?: 0f
            val totalRrfScore = scores.sumOf { it.rrfContribution.toDouble() }.toFloat()
            val matchedText = scores.firstNotNullOfOrNull { it.matchedText }
            
            // Determine match type
            val hasKeywordMatch = scores.any { it.keywordScore > 0 }
            val hasSemanticMatch = scores.any { it.semanticScore > 0 }
            val matchType = when {
                hasKeywordMatch && hasSemanticMatch -> SearchType.HYBRID
                hasKeywordMatch -> SearchType.KEYWORD
                else -> SearchType.SEMANTIC
            }
            
            HybridSearchResult(
                rule = rule,
                keywordScore = bestKeywordScore,
                semanticScore = bestSemanticScore,
                combinedScore = totalRrfScore,
                matchedText = matchedText,
                matchType = matchType
            )
        }
        
        return fusedResults
            .sortedByDescending { it.combinedScore }
            .take(maxResults)
    }
    
    /**
     * Check if a rule is applicable to the given context.
     */
    private fun isRuleApplicable(rule: KeywordRule, context: ReplyContext): Boolean {
        // Check app package
        if (rule.applicableScenarios.isNotEmpty()) {
            // Scenario-based filtering (if implemented)
        }
        
        // Check target type
        return when (rule.targetType) {
            com.csbaby.kefu.domain.model.RuleTargetType.ALL -> true
            com.csbaby.kefu.domain.model.RuleTargetType.PROPERTY -> {
                // Check if property name matches
                val propertyName = context.propertyName
                if (propertyName.isNullOrEmpty()) true
                else rule.targetNames.any { name ->
                    propertyName.contains(name, ignoreCase = true)
                }
            }
            com.csbaby.kefu.domain.model.RuleTargetType.CONTACT,
            com.csbaby.kefu.domain.model.RuleTargetType.GROUP -> true
        }
    }
    
    /**
     * Quick keyword search (synchronous, for immediate feedback).
     */
    fun quickSearch(query: String): List<HybridSearchResult> {
        return keywordMatcher.findMatches(query)
            .sortedByDescending { it.confidence }
            .take(5)
            .map { match ->
                HybridSearchResult(
                    rule = match.rule,
                    keywordScore = match.confidence,
                    combinedScore = match.confidence,
                    matchedText = match.matchedText,
                    matchType = SearchType.KEYWORD
                )
            }
    }
    
    /**
     * Initialize the search engine (pre-compute embeddings).
     */
    suspend fun initialize(): Boolean {
        Log.d(TAG, "Initializing hybrid search engine...")
        return vectorStore.initialize()
    }
    
    /**
     * Index a new or updated rule.
     */
    suspend fun indexRule(rule: KeywordRule): Boolean {
        return vectorStore.indexRule(rule)
    }
    
    /**
     * Remove a rule from the index.
     */
    fun removeRule(ruleId: Long) {
        vectorStore.removeRule(ruleId)
    }
    
    private data class ScoreInfo(
        val keywordScore: Float = 0f,
        val keywordRank: Int = -1,
        val semanticScore: Float = 0f,
        val semanticRank: Int = -1,
        val rrfContribution: Float = 0f,
        val matchedText: String? = null
    )
}
