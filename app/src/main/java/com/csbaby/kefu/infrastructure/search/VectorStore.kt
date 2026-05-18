package com.csbaby.kefu.infrastructure.search

import android.util.Log
import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.domain.repository.KeywordRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Vector Store - Store and search rule embeddings.
 * Provides semantic similarity search using cosine similarity.
 */
@Singleton
class VectorStore @Inject constructor(
    private val keywordRuleRepository: KeywordRuleRepository,
    private val embeddingService: EmbeddingService
) {
    companion object {
        private const val TAG = "VectorStore"
        
        // Similarity threshold for semantic search
        const val DEFAULT_SIMILARITY_THRESHOLD = 0.7f
        
        // Maximum results to return
        const val MAX_RESULTS = 10
    }
    
    // In-memory vector index: ruleId -> (rule, embedding)
    private val vectorIndex = ConcurrentHashMap<Long, Pair<KeywordRule, FloatArray>>()
    
    // Flag to track if index is initialized
    @Volatile
    private var isInitialized = false

    private val initMutex = Mutex()

    /**
     * Initialize the vector store by loading all rules and generating embeddings.
     * This should be called when the app starts or when rules change.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.d(TAG, "Vector store already initialized")
            return@withContext true
        }

        initMutex.withLock {
            if (isInitialized) {
                return@withContext true
            }

            try {
                Log.d(TAG, "Initializing vector store...")

                val rules = keywordRuleRepository.getEnabledRules().first()
                Log.d(TAG, "Found ${rules.size} enabled rules to index")

                if (rules.isEmpty()) {
                    isInitialized = true
                    return@withContext true
                }

                // Generate embeddings for all rules
                val texts = rules.map { rule ->
                    "${rule.keyword} ${rule.replyTemplate}"
                }

                val embeddingsResult = embeddingService.embedBatch(texts)

                embeddingsResult.fold(
                    onSuccess = { embeddings ->
                        rules.forEachIndexed { index, rule ->
                            if (index < embeddings.size) {
                                vectorIndex[rule.id] = rule to embeddings[index]
                            }
                        }
                        Log.d(TAG, "Vector store initialized with ${vectorIndex.size} rules")
                        isInitialized = true
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to generate embeddings: ${error.message}")
                        // Fall back to keyword-only mode
                        isInitialized = true
                    }
                )

                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize vector store", e)
                false
            }
        }
    }
    
    /**
     * Add or update a rule's embedding.
     */
    suspend fun indexRule(rule: KeywordRule): Boolean = withContext(Dispatchers.IO) {
        if (!rule.enabled) {
            vectorIndex.remove(rule.id)
            return@withContext true
        }
        
        val text = "${rule.keyword} ${rule.replyTemplate}"
        val embeddingResult = embeddingService.embed(text)
        
        embeddingResult.fold(
            onSuccess = { embedding ->
                vectorIndex[rule.id] = rule to embedding
                Log.d(TAG, "Indexed rule ${rule.id}: ${rule.keyword}")
                true
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to index rule ${rule.id}: ${error.message}")
                false
            }
        )
    }
    
    /**
     * Remove a rule from the index.
     */
    fun removeRule(ruleId: Long) {
        vectorIndex.remove(ruleId)
        Log.d(TAG, "Removed rule $ruleId from vector index")
    }
    
    /**
     * Clear the entire index.
     */
    fun clearIndex() {
        vectorIndex.clear()
        isInitialized = false
        Log.d(TAG, "Vector index cleared")
    }
    
    /**
     * Search for similar rules using semantic similarity.
     * Returns list of (rule, similarity) pairs sorted by similarity.
     */
    suspend fun search(
        query: String,
        threshold: Float = DEFAULT_SIMILARITY_THRESHOLD,
        maxResults: Int = MAX_RESULTS
    ): List<VectorSearchResult> = withContext(Dispatchers.IO) {
        if (!isInitialized || vectorIndex.isEmpty()) {
            Log.d(TAG, "Vector store not initialized or empty, returning empty results")
            return@withContext emptyList()
        }
        
        val queryEmbeddingResult = embeddingService.embed(query)
        
        queryEmbeddingResult.fold(
            onSuccess = { queryEmbedding ->
                val results = vectorIndex.mapNotNull { (ruleId, pair) ->
                    val (rule, ruleEmbedding) = pair
                    val similarity = cosineSimilarity(queryEmbedding, ruleEmbedding)
                    
                    if (similarity >= threshold) {
                        VectorSearchResult(
                            rule = rule,
                            similarity = similarity,
                            searchType = SearchType.SEMANTIC
                        )
                    } else {
                        null
                    }
                }.sortedByDescending { it.similarity }
                    .take(maxResults)
                
                Log.d(TAG, "Semantic search found ${results.size} results for query: ${query.take(50)}...")
                results
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to embed query: ${error.message}")
                emptyList()
            }
        )
    }
    
    /**
     * Search with hybrid scoring (semantic + keyword overlap).
     */
    suspend fun searchHybrid(
        query: String,
        keywordMatches: List<Long>,
        semanticWeight: Float = 0.5f
    ): List<VectorSearchResult> = withContext(Dispatchers.IO) {
        if (!isInitialized || vectorIndex.isEmpty()) {
            return@withContext emptyList()
        }

        // For rules that matched keywords, boost their scores
        return@withContext vectorIndex.mapNotNull { (ruleId, pair) ->
            val (rule, _) = pair
            val isKeywordMatch = ruleId in keywordMatches
            
            // Simple heuristic: if keyword matched, give it a boost
            val baseScore = if (isKeywordMatch) 0.8f else 0.5f
            
            VectorSearchResult(
                rule = rule,
                similarity = baseScore,
                searchType = if (isKeywordMatch) SearchType.KEYWORD else SearchType.SEMANTIC
            )
        }.sortedByDescending { it.similarity }
            .take(MAX_RESULTS)
    }
    
    /**
     * Calculate cosine similarity between two vectors.
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) (dotProduct / denominator).toFloat() else 0f
    }
    
    /**
     * Get index statistics.
     */
    fun getStats(): VectorStoreStats {
        return VectorStoreStats(
            totalRules = vectorIndex.size,
            isInitialized = isInitialized
        )
    }
    
    data class VectorSearchResult(
        val rule: KeywordRule,
        val similarity: Float,
        val searchType: SearchType
    )
    
    data class VectorStoreStats(
        val totalRules: Int,
        val isInitialized: Boolean
    )
}

enum class SearchType {
    KEYWORD,      // Exact/partial keyword match
    SEMANTIC,     // Vector similarity match
    HYBRID        // Combined keyword + semantic
}
