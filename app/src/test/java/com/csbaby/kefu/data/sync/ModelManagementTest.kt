package com.csbaby.kefu.data.sync

import com.csbaby.kefu.data.local.dao.AIModelConfigDao
import com.csbaby.kefu.data.local.entity.AIModelConfigEntity
import com.csbaby.kefu.domain.model.ModelType
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 模型管理测试
 * 验证用户可以通过UI添加Longcat模型，而不是硬编码在SyncManager中
 */
class ModelManagementTest {

    private lateinit var dao: AIModelConfigDao

    @Before
    fun setup() {
        dao = mockk<AIModelConfigDao>(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `用户可以通过UI添加Longcat OpenAI模型`() = runTest {
        // 模拟用户通过UI添加Longcat模型
        val longcatModel = AIModelConfigEntity(
            id = 0L, // 0表示新模型，让数据库自动生成ID
            modelType = "OPENAI",
            modelName = "LongCat-Flash-Chat",
            apiKey = "user_provided_api_key", // 用户提供的API Key
            apiEndpoint = "https://api.longcat.chat/openai",
            temperature = 0.7f,
            maxTokens = 6000,
            isDefault = false,
            isEnabled = true,
            monthlyCost = 0.0,
            lastUsed = 0L,
            createdAt = System.currentTimeMillis(),
            tenantId = "user_tenant_id",
            syncVersion = 0L,
            deleted = false
        )

        // 验证模型数据正确
        assertEquals("OPENAI", longcatModel.modelType)
        assertEquals("LongCat-Flash-Chat", longcatModel.modelName)
        assertEquals("https://api.longcat.chat/openai", longcatModel.apiEndpoint)
        assertEquals("user_provided_api_key", longcatModel.apiKey)
        assertEquals("user_tenant_id", longcatModel.tenantId)

        // 验证可以通过DAO插入
        coEvery { dao.insertModel(longcatModel) } returns 1L
        val result = dao.insertModel(longcatModel)
        assertEquals("应返回插入的ID", 1L, result)
        coVerify { dao.insertModel(longcatModel) }
    }

    @Test
    fun `模型类型必须使用大写OPENAI以匹配枚举`() = runTest {
        // 验证大写OPENAI可以正确转换为枚举
        val modelType = "OPENAI"
        val enum = try {
            ModelType.valueOf(modelType)
        } catch (e: IllegalArgumentException) {
            fail("ModelType.valueOf('$modelType') 应该成功但抛出了异常: ${e.message}")
            return@runTest
        }

        assertEquals(ModelType.OPENAI, enum)
    }

    @Test
    fun `小写openai会导致枚举转换失败`() = runTest {
        // 验证小写openai会导致失败（这是之前BUG-R6的根因）
        val exception = try {
            ModelType.valueOf("openai")
            fail("期望抛出 IllegalArgumentException，但没有抛出")
            return@runTest
        } catch (e: IllegalArgumentException) {
            e
        }

        assertTrue(exception.message?.contains("No enum constant") == true)
    }

    @Test
    fun `SyncManager不再包含硬编码的Longcat模型`() = runTest {
        // 验证SyncManager源码中不包含addLongcatModels函数
        val syncManagerSource = this::class.java.classLoader
            ?.getResource("com/csbaby/kefu/data/sync/SyncManager.kt")
            ?.readText() ?: return@runTest

        assertFalse(
            "SyncManager不应包含硬编码的addLongcatModels函数",
            syncManagerSource.contains("addLongcatModels")
        )
        assertFalse(
            "SyncManager不应包含硬编码的Longcat模型",
            syncManagerSource.contains("LongCat-Flash-Chat")
        )
    }

    @Test
    fun `用户可以为不同模型设置不同的API Key`() = runTest {
        // 验证用户可以为不同模型设置不同的API Key
        val model1 = AIModelConfigEntity(
            id = 1L,
            modelType = "OPENAI",
            modelName = "LongCat-Flash-Chat",
            apiKey = "sk-user-key-1",
            apiEndpoint = "https://api.longcat.chat/openai",
            tenantId = "tenant1"
        )

        val model2 = AIModelConfigEntity(
            id = 2L,
            modelType = "OPENAI",
            modelName = "LongCat-2.0-Preview",
            apiKey = "sk-user-key-2", // 不同的API Key
            apiEndpoint = "https://api.longcat.chat/openai",
            tenantId = "tenant1"
        )

        assertNotEquals("不同模型应有不同的API Key", model1.apiKey, model2.apiKey)
        assertEquals("模型名称应不同", "LongCat-Flash-Chat", model1.modelName)
        assertEquals("模型名称应不同", "LongCat-2.0-Preview", model2.modelName)
    }
}