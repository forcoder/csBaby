package com.csbaby.kefu.data.remote

import com.csbaby.kefu.domain.model.AIModelConfig
import com.csbaby.kefu.domain.model.ModelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 测试 AIClient 在 API endpoint 为空或无效时使用合理的默认 URL。
 *
 * 背景：用户设备上历史数据存在 modelType=OPENAI / modelName=LongCat-* 的配置，
 * 但 apiEndpoint 字段为空字符串。OkHttp 在 .url("") 时直接抛异常，
 * 客户端原本会在 try/catch 中静默吞掉，导致 AI 生成功能看似被禁用。
 * 修复后：AIClient 应在 endpoint 缺失时按 modelType 选择已知 provider 的默认 URL。
 */
class AIClientEndpointFallbackTest {

    private fun config(
        apiEndpoint: String,
        modelType: ModelType = ModelType.OPENAI,
        modelName: String = "LongCat-Flash-Lite",
        apiKey: String = "ak_test_key_xxxxxxxxxxxxxxxxxxxx"
    ) = AIModelConfig(
        id = 1003L,
        modelType = modelType,
        modelName = modelName,
        apiKey = apiKey,
        apiEndpoint = apiEndpoint,
        isDefault = true,
        isEnabled = true
    )

    @Test
    fun `uses configured endpoint when non-empty`() {
        val cfg = config(apiEndpoint = "https://my.custom.endpoint/v1/chat")
        assertEquals("https://my.custom.endpoint/v1/chat", AIClientImpl.resolveEndpoint(cfg))
    }

    @Test
    fun `falls back to LongCat default when OPENAI endpoint is empty`() {
        val cfg = config(apiEndpoint = "", modelType = ModelType.OPENAI)
        assertEquals(
            "https://api.longcat.chat/openai/v1/chat/completions",
            AIClientImpl.resolveEndpoint(cfg)
        )
    }

    @Test
    fun `falls back to Anthropic default when CLAUDE endpoint is empty`() {
        val cfg = config(apiEndpoint = "", modelType = ModelType.CLAUDE)
        assertEquals("https://api.anthropic.com/v1/messages", AIClientImpl.resolveEndpoint(cfg))
    }

    @Test
    fun `falls back to Zhipu default when ZHIPU endpoint is empty`() {
        val cfg = config(apiEndpoint = "", modelType = ModelType.ZHIPU)
        assertEquals("https://open.bigmodel.cn/api/paas/v4/chat/completions", AIClientImpl.resolveEndpoint(cfg))
    }

    @Test
    fun `falls back to Tongyi default when TONGYI endpoint is empty`() {
        val cfg = config(apiEndpoint = "", modelType = ModelType.TONGYI)
        val resolved: String? = AIClientImpl.resolveEndpoint(cfg)
        assertEquals("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation", resolved)
    }

    @Test
    fun `returns null for CUSTOM model with empty endpoint (caller must surface error)`() {
        val cfg = config(apiEndpoint = "", modelType = ModelType.CUSTOM)
        val resolved: String? = AIClientImpl.resolveEndpoint(cfg)
        assertEquals(null, resolved)
    }

    @Test
    fun `treats whitespace-only endpoint as empty`() {
        val cfg = config(apiEndpoint = "   \t  ", modelType = ModelType.OPENAI)
        assertEquals(
            "https://api.longcat.chat/openai/v1/chat/completions",
            AIClientImpl.resolveEndpoint(cfg)
        )
    }

    @Test
    fun `trims whitespace around a valid endpoint`() {
        val cfg = config(apiEndpoint = "  https://api.example.com/v1  ", modelType = ModelType.OPENAI)
        assertEquals("https://api.example.com/v1", AIClientImpl.resolveEndpoint(cfg))
    }
}
