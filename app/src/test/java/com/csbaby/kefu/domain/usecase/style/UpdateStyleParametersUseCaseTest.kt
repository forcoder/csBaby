package com.csbaby.kefu.domain.usecase.style

import com.csbaby.kefu.infrastructure.style.StyleLearningEngine
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class UpdateStyleParametersUseCaseTest {

    private val styleLearningEngine: StyleLearningEngine = mockk(relaxed = true)
    private val useCase = UpdateStyleParametersUseCase(styleLearningEngine)

    @Test
    fun `全部参数都传时透传`() = runTest {
        useCase("user1", formality = 0.5f, enthusiasm = 0.6f, professionalism = 0.7f)

        coVerify {
            styleLearningEngine.updateStyleParameters(
                userId = "user1",
                formality = 0.5f,
                enthusiasm = 0.6f,
                professionalism = 0.7f
            )
        }
    }

    @Test
    fun `只更新 formality`() = runTest {
        useCase("u1", formality = 0.8f)

        coVerify {
            styleLearningEngine.updateStyleParameters(
                userId = "u1",
                formality = 0.8f,
                enthusiasm = null,
                professionalism = null
            )
        }
    }

    @Test
    fun `只更新 enthusiasm`() = runTest {
        useCase("u1", enthusiasm = 0.9f)

        coVerify {
            styleLearningEngine.updateStyleParameters(
                userId = "u1",
                formality = null,
                enthusiasm = 0.9f,
                professionalism = null
            )
        }
    }

    @Test
    fun `只更新 professionalism`() = runTest {
        useCase("u1", professionalism = 0.3f)

        coVerify {
            styleLearningEngine.updateStyleParameters(
                userId = "u1",
                formality = null,
                enthusiasm = null,
                professionalism = 0.3f
            )
        }
    }

    @Test
    fun `全部 null 时仍然调用 (边界)`() = runTest {
        useCase("u1")

        coVerify {
            styleLearningEngine.updateStyleParameters(
                userId = "u1",
                formality = null,
                enthusiasm = null,
                professionalism = null
            )
        }
    }

    @Test
    fun `边界值 0 透传`() = runTest {
        useCase("u1", formality = 0f, enthusiasm = 0f, professionalism = 0f)

        coVerify {
            styleLearningEngine.updateStyleParameters(
                userId = "u1",
                formality = 0f,
                enthusiasm = 0f,
                professionalism = 0f
            )
        }
    }

    @Test
    fun `边界值 1f 透传`() = runTest {
        useCase("u1", formality = 1f)

        coVerify {
            styleLearningEngine.updateStyleParameters(
                userId = "u1",
                formality = 1f,
                enthusiasm = null,
                professionalism = null
            )
        }
    }
}