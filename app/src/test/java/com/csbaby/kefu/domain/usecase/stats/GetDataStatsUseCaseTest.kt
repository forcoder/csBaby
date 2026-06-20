package com.csbaby.kefu.domain.usecase.stats

import com.csbaby.kefu.data.local.dao.AIModelConfigDao
import com.csbaby.kefu.data.local.dao.AppConfigDao
import com.csbaby.kefu.data.local.dao.KeywordRuleDao
import com.csbaby.kefu.data.local.dao.MessageBlacklistDao
import com.csbaby.kefu.data.local.dao.ScenarioDao
import com.csbaby.kefu.data.local.entity.AIModelConfigEntity
import com.csbaby.kefu.data.local.entity.AppConfigEntity
import com.csbaby.kefu.data.local.entity.ScenarioEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetDataStatsUseCaseTest {

    private val keywordRuleDao: KeywordRuleDao = mockk()
    private val messageBlacklistDao: MessageBlacklistDao = mockk()
    private val aiModelConfigDao: AIModelConfigDao = mockk()
    private val appConfigDao: AppConfigDao = mockk()
    private val scenarioDao: ScenarioDao = mockk()

    private val useCase = GetDataStatsUseCase(
        keywordRuleDao = keywordRuleDao,
        messageBlacklistDao = messageBlacklistDao,
        aiModelConfigDao = aiModelConfigDao,
        appConfigDao = appConfigDao,
        scenarioDao = scenarioDao
    )

    @Test
    fun `正常返回 5 项统计`() = runTest {
        coEvery { keywordRuleDao.getRuleCount() } returns 10
        coEvery { messageBlacklistDao.getEnabledCount() } returns 3
        coEvery { aiModelConfigDao.getModelsByTenantSync("t1") } returns List(2) {
            AIModelConfigEntity(
                id = it.toLong(),
                modelType = "OPENAI",
                modelName = "gpt-4",
                apiKey = "k",
                apiEndpoint = "https://api.openai.com",
                tenantId = "t1"
            )
        }
        coEvery { appConfigDao.getAppsByTenantSync("t1") } returns List(4) {
            AppConfigEntity(packageName = "p$it", appName = "App$it", iconUri = null, tenantId = "t1")
        }
        coEvery { scenarioDao.getScenariosByTenantSync("t1") } returns List(5) {
            ScenarioEntity(
                id = it.toLong(),
                name = "S$it",
                type = "ALL_PROPERTIES",
                targetId = null,
                description = null,
                tenantId = "t1"
            )
        }

        val stats = useCase("t1")

        assertEquals(10, stats.knowledgeCount)
        assertEquals(3, stats.blacklistCount)
        assertEquals(2, stats.modelCount)
        assertEquals(4, stats.appCount)
        assertEquals(5, stats.scenarioCount)
    }

    @Test
    fun `tenantId null 时 tenant 维度统计全为 0`() = runTest {
        coEvery { keywordRuleDao.getRuleCount() } returns 7
        coEvery { messageBlacklistDao.getEnabledCount() } returns 1

        val stats = useCase(null)

        assertEquals(7, stats.knowledgeCount)
        assertEquals(1, stats.blacklistCount)
        assertEquals(0, stats.modelCount)
        assertEquals(0, stats.appCount)
        assertEquals(0, stats.scenarioCount)
    }

    @Test
    fun `空数据库全 0`() = runTest {
        coEvery { keywordRuleDao.getRuleCount() } returns 0
        coEvery { messageBlacklistDao.getEnabledCount() } returns 0
        coEvery { aiModelConfigDao.getModelsByTenantSync(any()) } returns emptyList()
        coEvery { appConfigDao.getAppsByTenantSync(any()) } returns emptyList()
        coEvery { scenarioDao.getScenariosByTenantSync(any()) } returns emptyList()

        val stats = useCase("t1")

        assertEquals(0, stats.knowledgeCount)
        assertEquals(0, stats.blacklistCount)
        assertEquals(0, stats.modelCount)
        assertEquals(0, stats.appCount)
        assertEquals(0, stats.scenarioCount)
    }

    @Test
    fun `keywordRuleDao 抛异常时 fallback 0`() = runTest {
        coEvery { keywordRuleDao.getRuleCount() } throws RuntimeException("db error")
        coEvery { messageBlacklistDao.getEnabledCount() } returns 5
        coEvery { aiModelConfigDao.getModelsByTenantSync(any()) } returns emptyList()
        coEvery { appConfigDao.getAppsByTenantSync(any()) } returns emptyList()
        coEvery { scenarioDao.getScenariosByTenantSync(any()) } returns emptyList()

        val stats = useCase("t1")

        assertEquals(0, stats.knowledgeCount)
        assertEquals(5, stats.blacklistCount)
    }

    @Test
    fun `messageBlacklistDao 抛异常时 fallback 0`() = runTest {
        coEvery { keywordRuleDao.getRuleCount() } returns 5
        coEvery { messageBlacklistDao.getEnabledCount() } throws RuntimeException("error")
        coEvery { aiModelConfigDao.getModelsByTenantSync(any()) } returns emptyList()
        coEvery { appConfigDao.getAppsByTenantSync(any()) } returns emptyList()
        coEvery { scenarioDao.getScenariosByTenantSync(any()) } returns emptyList()

        val stats = useCase("t1")

        assertEquals(5, stats.knowledgeCount)
        assertEquals(0, stats.blacklistCount)
    }

    @Test
    fun `边界 tenantId 空串仍按 tenant 查询`() = runTest {
        coEvery { keywordRuleDao.getRuleCount() } returns 0
        coEvery { messageBlacklistDao.getEnabledCount() } returns 0
        coEvery { aiModelConfigDao.getModelsByTenantSync("") } returns emptyList()
        coEvery { appConfigDao.getAppsByTenantSync("") } returns emptyList()
        coEvery { scenarioDao.getScenariosByTenantSync("") } returns emptyList()

        val stats = useCase("")

        assertEquals(0, stats.modelCount)
    }
}