package com.csbaby.kefu.presentation.screens.model

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

    private lateinit var aiModelRepository: AIModelRepository
    private lateinit var aiService: AIService
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        aiModelRepository = mockk(relaxed = true)
        aiService = mockk(relaxed = true)
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
        model = "test-model",
        apiKey = "test-key",
        apiEndpoint = "https://api.test.com",
        isDefault = false,
        isEnabled = true
    )

    @Test
    fun `initial state loads models from repository`() = runTest {
        val models = listOf(
            createTestModel(1, "GPT-4", ModelType.OPENAI),
            createTestModel(2, "Claude", ModelType.CLAUDE)
        )
        every { aiModelRepository.getAllModels() } returns flowOf(models)

        val viewModel = ModelViewModel(
            aiModelRepository = aiModelRepository,
            aiService = aiService
        )

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(2, state.models.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun `initial state has empty models list`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())

        val viewModel = ModelViewModel(
            aiModelRepository = aiModelRepository,
            aiService = aiService
        )

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.models.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `saveModel inserts new model when id is 0`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())
        coEvery { aiModelRepository.insertModel(any()) } returns 1L

        val viewModel = ModelViewModel(
            aiModelRepository = aiModelRepository,
            aiService = aiService
        )

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

        val viewModel = ModelViewModel(
            aiModelRepository = aiModelRepository,
            aiService = aiService
        )

        advanceUntilIdle()
        val existingModel = createTestModel(5, "Existing Model")
        viewModel.saveModel(existingModel)
        advanceUntilIdle()

        coVerify { aiModelRepository.updateModel(any()) }
    }

    @Test
    fun `deleteModel calls repository`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())
        coEvery { aiModelRepository.deleteModel(any()) } returns Unit

        val viewModel = ModelViewModel(
            aiModelRepository = aiModelRepository,
            aiService = aiService
        )

        advanceUntilIdle()
        viewModel.deleteModel(1L)
        advanceUntilIdle()

        coVerify { aiModelRepository.deleteModel(1L) }
    }

    @Test
    fun `setDefaultModel calls repository`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())
        coEvery { aiModelRepository.setDefaultModel(any()) } returns Unit

        val viewModel = ModelViewModel(
            aiModelRepository = aiModelRepository,
            aiService = aiService
        )

        advanceUntilIdle()
        viewModel.setDefaultModel(1L)
        advanceUntilIdle()

        coVerify { aiModelRepository.setDefaultModel(1L) }
    }

    @Test
    fun `testConnection updates testResults on success`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())
        coEvery { aiService.testModelConnection(any()) } returns Result.success(true)

        val viewModel = ModelViewModel(
            aiModelRepository = aiModelRepository,
            aiService = aiService
        )

        advanceUntilIdle()
        viewModel.testConnection(1L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(true, state.testResults[1L])
    }

    @Test
    fun `testConnection updates testErrorMessages on failure`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())
        coEvery { aiService.testModelConnection(any()) } returns Result.failure(Exception("连接超时"))

        val viewModel = ModelViewModel(
            aiModelRepository = aiModelRepository,
            aiService = aiService
        )

        advanceUntilIdle()
        viewModel.testConnection(1L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.testResults[1L])
        assertEquals("连接超时", state.testErrorMessages[1L])
    }

    @Test
    fun `testConnectionWithConfig sets dialog state to success`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())
        coEvery { aiService.testConnection(any()) } returns Result.success(true)

        val viewModel = ModelViewModel(
            aiModelRepository = aiModelRepository,
            aiService = aiService
        )

        advanceUntilIdle()
        val config = createTestModel(0, "Test")
        viewModel.testConnectionWithConfig(config)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.dialogTestState is DialogTestState.Success)
    }

    @Test
    fun `testConnectionWithConfig sets dialog state to error on failure`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())
        coEvery { aiService.testConnection(any()) } returns Result.success(false)

        val viewModel = ModelViewModel(
            aiModelRepository = aiModelRepository,
            aiService = aiService
        )

        advanceUntilIdle()
        val config = createTestModel(0, "Test")
        viewModel.testConnectionWithConfig(config)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.dialogTestState is DialogTestState.Error)
    }

    @Test
    fun `resetDialogTestState resets to Idle`() = runTest {
        every { aiModelRepository.getAllModels() } returns flowOf(emptyList())
        coEvery { aiService.testConnection(any()) } returns Result.success(true)

        val viewModel = ModelViewModel(
            aiModelRepository = aiModelRepository,
            aiService = aiService
        )

        advanceUntilIdle()
        val config = createTestModel(0, "Test")
        viewModel.testConnectionWithConfig(config)
        advanceUntilIdle()

        viewModel.resetDialogTestState()
        val state = viewModel.uiState.value
        assertTrue(state.dialogTestState is DialogTestState.Idle)
    }
}
