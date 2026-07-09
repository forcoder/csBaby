package com.csbaby.kefu.presentation.screens.model

import android.content.Context
import com.csbaby.kefu.data.sync.SyncManager
import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
import com.csbaby.kefu.domain.repository.AIModelRepository
import com.csbaby.kefu.infrastructure.ai.AIService
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelViewModelTest {

    private lateinit var appContext: Context
    private lateinit var aiModelRepository: AIModelRepository
    private lateinit var aiService: AIService
    private lateinit var syncManager: SyncManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        appContext = mockk(relaxed = true)
        aiModelRepository = mockk(relaxed = true)
        aiService = mockk(relaxed = true)
        syncManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createTestModel(id: Long, name: String, type: ModelType = ModelType.OPENAI) = AIModelConfig(
        id = id,
        modelType = type,
        modelName = name,
        apiKey = "test-key",
        apiEndpoint = "https://api.test.com",
        isDefault = false,
        isEnabled = true
    )

    private fun createViewModel(): ModelViewModel {
        return ModelViewModel(
            appContext = appContext,
            aiModelRepository = aiModelRepository,
            aiService = aiService,
            syncManager = syncManager
        )
    }

    @Test
    fun `initial state loads models from repository`() = runTest {
        val models = listOf(
            createTestModel(1, "GPT-4", ModelType.OPENAI),
            createTestModel(2, "Claude", ModelType.CLAUDE)
        )
        every { aiModelRepository.getAllModels() } returns flowOf(models)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.models.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun `initial state has empty models list`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.models.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `saveModel inserts new model when id is 0`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())
        coEvery { aiModelRepository.insertModel(any()) } returns 1L
        every { syncManager.isLoggedIn() } returns false

        val viewModel = createViewModel()
        advanceUntilIdle()

        val newModel = createTestModel(0, "New Model")
        viewModel.saveModel(newModel)
        advanceUntilIdle()

        coVerify { aiModelRepository.insertModel(any()) }
    }

    @Test
    fun `saveModel updates existing model when id is not 0`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())
        coEvery { aiModelRepository.updateModel(any()) } returns Unit
        every { syncManager.isLoggedIn() } returns false

        val viewModel = createViewModel()
        advanceUntilIdle()

        val existingModel = createTestModel(5, "Existing Model")
        viewModel.saveModel(existingModel)
        advanceUntilIdle()

        coVerify { aiModelRepository.updateModel(any()) }
    }

    @Test
    fun `saveModel triggers sync when logged in`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())
        coEvery { aiModelRepository.insertModel(any()) } returns 1L
        every { syncManager.isLoggedIn() } returns true
        coEvery { syncManager.triggerSync() } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        val newModel = createTestModel(0, "New Model")
        viewModel.saveModel(newModel)
        advanceUntilIdle()

        coVerify { syncManager.triggerSync() }
    }

    @Test
    fun `deleteModel calls repository`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())
        coEvery { aiModelRepository.deleteModel(any()) } returns Unit
        every { syncManager.isLoggedIn() } returns false

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteModel(1L)
        advanceUntilIdle()

        coVerify { aiModelRepository.deleteModel(1L) }
    }

    @Test
    fun `deleteModel triggers sync when logged in`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())
        coEvery { aiModelRepository.deleteModel(any()) } returns Unit
        every { syncManager.isLoggedIn() } returns true
        coEvery { syncManager.triggerSync() } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteModel(1L)
        advanceUntilIdle()

        coVerify { syncManager.triggerSync() }
    }

    @Test
    fun `setDefaultModel calls repository`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())
        coEvery { aiModelRepository.setDefaultModel(any()) } returns Unit
        every { syncManager.isLoggedIn() } returns false

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setDefaultModel(1L)
        advanceUntilIdle()

        coVerify { aiModelRepository.setDefaultModel(1L) }
    }

    @Test
    fun `setDefaultModel triggers sync when logged in`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())
        coEvery { aiModelRepository.setDefaultModel(any()) } returns Unit
        every { syncManager.isLoggedIn() } returns true
        coEvery { syncManager.triggerSync() } returns Unit

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setDefaultModel(1L)
        advanceUntilIdle()

        coVerify { syncManager.triggerSync() }
    }

    @Test
    fun `testConnection updates testResults on success`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())
        coEvery { aiService.testModelConnection(any()) } returns Result.success(true)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.testConnection(1L)
        // viewModelScope.launch + withContext(Dispatchers.IO) 切到真实 IO 线程池,
        // advanceUntilIdle 只推进 testDispatcher 无法等 IO 完成。
        // 用跑 suspend + yield 循环等协程到达预期状态。
        waitForIdleUpTo(2_000)

        val state = viewModel.uiState.value
        assertEquals(true, state.testResults[1L]?.success)
        assertNull(state.testResults[1L]?.errorMessage)
    }

    @Test
    fun `testConnection updates testResults on failure`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())
        coEvery { aiService.testModelConnection(any()) } returns Result.failure(Exception("连接超时"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.testConnection(1L)
        waitForIdleUpTo(2_000)

        val state = viewModel.uiState.value
        assertEquals(false, state.testResults[1L]?.success)
        assertEquals("连接超时", state.testResults[1L]?.errorMessage)
    }

    /**
     * 等协程完成,最多等 timeoutMs 毫秒。
     *
     * 专为 ViewModel.viewModelScope.launch + withContext(Dispatchers.IO) 场景:
     * advanceUntilIdle 只推进 testDispatcher (Main) 上的协程,
     * 但 IO 是真实线程池,需要 yield 给 IO 协程跑完。
     */
    private suspend fun TestScope.waitForIdleUpTo(timeoutMs: Long) {
        val start = System.currentTimeMillis()
        repeat(20) {
            advanceUntilIdle()
            Thread.sleep(50)
            if (System.currentTimeMillis() - start > timeoutMs) return
        }
    }
}
