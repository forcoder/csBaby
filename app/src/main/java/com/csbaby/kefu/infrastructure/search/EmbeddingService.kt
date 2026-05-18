package com.csbaby.kefu.infrastructure.search

import android.util.Log
import com.csbaby.kefu.data.remote.AIClient
import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
import com.csbaby.kefu.domain.repository.AIModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Embedding Service - Generate vector embeddings for text.
 * Supports multiple embedding API providers.
 */
@Singleton
class EmbeddingService @Inject constructor(
    private val aiClient: AIClient,
    private val aiModelRepository: AIModelRepository
) {
    companion object {
        private const val TAG = "EmbeddingService"
        
        // Default embedding dimension (OpenAI text-embedding-3-small)
        const val DEFAULT_EMBEDDING_DIM = 1536
        
        // Embedding models by provider
        val EMBEDDING_MODELS = mapOf(
            ModelType.OPENAI to "text-embedding-3-small",
            ModelType.ZHIPU to "embedding-3",
            ModelType.TONGYI to "text-embedding-v3"
        )
    }
    
    // Cache for embeddings (simple in-memory cache)
    private val embeddingCache = ConcurrentHashMap<String, FloatArray>()
    
    /**
     * Generate embedding for a single text.
     * Returns a normalized vector representation.
     */
    suspend fun embed(text: String): Result<FloatArray> = withContext(Dispatchers.IO) {
        // Check cache first
        embeddingCache[text]?.let {
            Log.d(TAG, "Embedding cache hit for: ${text.take(50)}...")
            return@withContext Result.success(it)
        }
        
        val defaultModel = aiModelRepository.getDefaultModel()
        if (defaultModel == null) {
            return@withContext Result.failure(Exception("No default model configured"))
        }
        
        val result = generateEmbeddingWithModel(defaultModel, text)
        
        // Cache successful result (ConcurrentHashMap is thread-safe)
        result.onSuccess { embedding ->
            if (embeddingCache.size < 1000) {
                embeddingCache.putIfAbsent(text, embedding)
            }
        }
        
        result
    }
    
    /**
     * Generate embeddings for multiple texts in batch.
     * More efficient than individual calls.
     */
    suspend fun embedBatch(texts: List<String>): Result<List<FloatArray>> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) {
            return@withContext Result.success(emptyList())
        }
        
        // Check cache for all texts
        val cached = mutableMapOf<Int, FloatArray>()
        val uncached = mutableListOf<Pair<Int, String>>()
        
        texts.forEachIndexed { index, text ->
            embeddingCache[text]?.let { cached[index] = it }
                ?: uncached.add(index to text)
        }
        
        if (uncached.isEmpty()) {
            Log.d(TAG, "All ${texts.size} embeddings from cache")
            return@withContext Result.success(texts.indices.map { i -> cached[i] ?: FloatArray(DEFAULT_EMBEDDING_DIM) })
        }
        
        Log.d(TAG, "Generating embeddings for ${uncached.size} texts (${cached.size} from cache)")
        
        val defaultModel = aiModelRepository.getDefaultModel()
        if (defaultModel == null) {
            return@withContext Result.failure(Exception("No default model configured"))
        }
        
        val batchResult = generateEmbeddingBatch(defaultModel, uncached.map { it.second })
        
        batchResult.map { embeddings ->
            // Merge cached and new embeddings
            val result = mutableListOf<FloatArray>()
            var uncachedIndex = 0
            
            texts.indices.forEach { index ->
                cached[index]?.let { result.add(it) }
                    ?: result.add(embeddings[uncachedIndex++])
            }
            
            // Cache new embeddings
            embeddings.forEachIndexed { i, embedding ->
                if (embeddingCache.size < 1000) {
                    embeddingCache.putIfAbsent(uncached[i].second, embedding)
                }
            }
            
            result
        }
    }
    
    /**
     * Generate embedding using the model's API.
     */
    private suspend fun generateEmbeddingWithModel(
        model: AIModelConfig,
        text: String
    ): Result<FloatArray> {
        return try {
            val embeddingModel = EMBEDDING_MODELS[model.modelType] ?: model.modelName
            
            val response = when (model.modelType) {
                ModelType.OPENAI -> callOpenAIEmbedding(model, embeddingModel, text)
                ModelType.ZHIPU -> callZhipuEmbedding(model, embeddingModel, text)
                ModelType.TONGYI -> callTongyiEmbedding(model, embeddingModel, text)
                else -> {
                    // For custom models, try OpenAI-compatible API
                    callOpenAIEmbedding(model, embeddingModel, text)
                }
            }
            
            response.map { normalizeVector(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate embedding", e)
            Result.failure(e)
        }
    }
    
    /**
     * Generate batch embeddings using the model's API.
     */
    private suspend fun generateEmbeddingBatch(
        model: AIModelConfig,
        texts: List<String>
    ): Result<List<FloatArray>> {
        return try {
            val embeddingModel = EMBEDDING_MODELS[model.modelType] ?: model.modelName
            
            val response = when (model.modelType) {
                ModelType.OPENAI -> callOpenAIEmbeddingBatch(model, embeddingModel, texts)
                ModelType.ZHIPU -> callZhipuEmbeddingBatch(model, embeddingModel, texts)
                ModelType.TONGYI -> callTongyiEmbeddingBatch(model, embeddingModel, texts)
                else -> callOpenAIEmbeddingBatch(model, embeddingModel, texts)
            }
            
            response.map { embeddings -> embeddings.map { normalizeVector(it) } }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate batch embeddings", e)
            Result.failure(e)
        }
    }
    
    // OpenAI embedding API
    private suspend fun callOpenAIEmbedding(
        model: AIModelConfig,
        embeddingModel: String,
        text: String
    ): Result<FloatArray> {
        val request = JSONObject().apply {
            put("model", embeddingModel)
            put("input", text)
        }
        
        return aiClient.makeRawRequest(
            config = model,
            endpoint = "embeddings",
            requestBody = request.toString()
        ).mapCatching { response ->
            parseEmbeddingResponse(response)
        }
    }
    
    private suspend fun callOpenAIEmbeddingBatch(
        model: AIModelConfig,
        embeddingModel: String,
        texts: List<String>
    ): Result<List<FloatArray>> {
        val request = JSONObject().apply {
            put("model", embeddingModel)
            put("input", JSONArray(texts))
        }
        
        return aiClient.makeRawRequest(
            config = model,
            endpoint = "embeddings",
            requestBody = request.toString()
        ).mapCatching { response ->
            parseBatchEmbeddingResponse(response)
        }
    }
    
    // Zhipu (智谱) embedding API
    private suspend fun callZhipuEmbedding(
        model: AIModelConfig,
        embeddingModel: String,
        text: String
    ): Result<FloatArray> {
        val request = JSONObject().apply {
            put("model", embeddingModel)
            put("input", text)
        }
        
        return aiClient.makeRawRequest(
            config = model,
            endpoint = "embeddings",
            requestBody = request.toString()
        ).mapCatching { response ->
            parseEmbeddingResponse(response)
        }
    }
    
    private suspend fun callZhipuEmbeddingBatch(
        model: AIModelConfig,
        embeddingModel: String,
        texts: List<String>
    ): Result<List<FloatArray>> {
        // Zhipu doesn't support batch, call individually
        val results = texts.map { text ->
            callZhipuEmbedding(model, embeddingModel, text).getOrDefault(FloatArray(DEFAULT_EMBEDDING_DIM))
        }
        return Result.success(results)
    }
    
    // Tongyi (通义) embedding API
    private suspend fun callTongyiEmbedding(
        model: AIModelConfig,
        embeddingModel: String,
        text: String
    ): Result<FloatArray> {
        val request = JSONObject().apply {
            put("model", embeddingModel)
            put("input", JSONObject().apply {
                put("texts", JSONArray(listOf(text)))
            })
            put("parameters", JSONObject().apply {
                put("text_type", "query")
            })
        }
        
        return aiClient.makeRawRequest(
            config = model,
            endpoint = "services/embeddings/text-embedding/text-embedding",
            requestBody = request.toString()
        ).mapCatching { response ->
            parseTongyiEmbeddingResponse(response)
        }
    }
    
    private suspend fun callTongyiEmbeddingBatch(
        model: AIModelConfig,
        embeddingModel: String,
        texts: List<String>
    ): Result<List<FloatArray>> {
        val request = JSONObject().apply {
            put("model", embeddingModel)
            put("input", JSONObject().apply {
                put("texts", JSONArray(texts))
            })
            put("parameters", JSONObject().apply {
                put("text_type", "query")
            })
        }
        
        return aiClient.makeRawRequest(
            config = model,
            endpoint = "services/embeddings/text-embedding/text-embedding",
            requestBody = request.toString()
        ).mapCatching { response ->
            parseTongyiBatchEmbeddingResponse(response)
        }
    }
    
    // Parse OpenAI/Zhipu response format
    private fun parseEmbeddingResponse(response: String): FloatArray {
        val json = JSONObject(response)
        val dataArray = json.getJSONArray("data")
        if (dataArray.length() == 0) throw Exception("Empty data array in embedding response")
        val data = dataArray.getJSONObject(0)
        if (!data.has("embedding")) throw Exception("Missing 'embedding' field in response data")
        val embedding = data.getJSONArray("embedding")

        return FloatArray(embedding.length()) { i ->
            embedding.getDouble(i).toFloat()
        }
    }

    private fun parseBatchEmbeddingResponse(response: String): List<FloatArray> {
        val json = JSONObject(response)
        val dataArray = json.getJSONArray("data")

        if (dataArray.length() == 0) return emptyList()

        // Sort by index to maintain order
        val sortedData = (0 until dataArray.length()).map { i ->
            dataArray.getJSONObject(i)
        }.sortedBy { it.optInt("index", 0) }

        return sortedData.map { data ->
            val embedding = data.getJSONArray("embedding")
            FloatArray(embedding.length()) { i ->
                embedding.getDouble(i).toFloat()
            }
        }
    }

    // Parse Tongyi response format
    private fun parseTongyiEmbeddingResponse(response: String): FloatArray {
        val json = JSONObject(response)
        val output = json.getJSONObject("output")
        val embeddings = output.getJSONArray("embeddings")
        if (embeddings.length() == 0) throw Exception("Empty embeddings array in Tongyi response")
        val firstEmbedding = embeddings.getJSONObject(0)
        if (!firstEmbedding.has("embedding")) throw Exception("Missing 'embedding' field in Tongyi response")
        val embedding = firstEmbedding.getJSONArray("embedding")

        return FloatArray(embedding.length()) { i ->
            embedding.getDouble(i).toFloat()
        }
    }

    private fun parseTongyiBatchEmbeddingResponse(response: String): List<FloatArray> {
        val json = JSONObject(response)
        val output = json.getJSONObject("output")
        val embeddings = output.getJSONArray("embeddings")

        if (embeddings.length() == 0) return emptyList()

        // Sort by text_index to maintain order
        val sortedEmbeddings = (0 until embeddings.length()).map { i ->
            embeddings.getJSONObject(i)
        }.sortedBy { it.optInt("text_index", 0) }

        return sortedEmbeddings.map { data ->
            val embedding = data.getJSONArray("embedding")
            FloatArray(embedding.length()) { i ->
                embedding.getDouble(i).toFloat()
            }
        }
    }
    
    /**
     * Normalize a vector to unit length.
     * This is important for cosine similarity calculations.
     */
    private fun normalizeVector(vector: FloatArray): FloatArray {
        val norm = kotlin.math.sqrt(vector.sumOf { it.toDouble() * it.toDouble() }).toFloat()
        if (norm < 1e-10f) return vector
        
        return FloatArray(vector.size) { i -> vector[i] / norm }
    }
    
    /**
     * Clear the embedding cache.
     */
    fun clearCache() {
        embeddingCache.clear()
        Log.d(TAG, "Embedding cache cleared")
    }
    
    /**
     * Get cache statistics.
     */
    fun getCacheStats(): Pair<Int, Int> {
        return embeddingCache.size to 1000
    }
}
