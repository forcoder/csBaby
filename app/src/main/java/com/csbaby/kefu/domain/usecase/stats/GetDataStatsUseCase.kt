package com.csbaby.kefu.domain.usecase.stats

import com.csbaby.kefu.data.local.dao.AIModelConfigDao
import com.csbaby.kefu.data.local.dao.AppConfigDao
import com.csbaby.kefu.data.local.dao.KeywordRuleDao
import com.csbaby.kefu.data.local.dao.MessageBlacklistDao
import com.csbaby.kefu.data.local.dao.ScenarioDao
import javax.inject.Inject

/**
 * 数据统计 UseCase
 *
 * 汇总本地各表的数据条数，用于"我的"页面展示。
 * 不再在 ViewModel 中直接调用多个 DAO。
 */
class GetDataStatsUseCase @Inject constructor(
    private val keywordRuleDao: KeywordRuleDao,
    private val messageBlacklistDao: MessageBlacklistDao,
    private val aiModelConfigDao: AIModelConfigDao,
    private val appConfigDao: AppConfigDao,
    private val scenarioDao: ScenarioDao
) {
    data class Stats(
        val knowledgeCount: Int = 0,
        val blacklistCount: Int = 0,
        val modelCount: Int = 0,
        val appCount: Int = 0,
        val scenarioCount: Int = 0
    )

    /**
     * 加载统计。tenantId 为 null 时不计入 tenant 维度的统计。
     */
    suspend operator fun invoke(tenantId: String?): Stats {
        val knowledgeCount = runCatching { keywordRuleDao.getRuleCount() }.getOrDefault(0)
        val blacklistCount = runCatching { messageBlacklistDao.getEnabledCount() }.getOrDefault(0)
        val (modelCount, appCount, scenarioCount) = if (tenantId != null) {
            Triple(
                runCatching { aiModelConfigDao.getModelsByTenantSync(tenantId).size }.getOrDefault(0),
                runCatching { appConfigDao.getAppsByTenantSync(tenantId).size }.getOrDefault(0),
                runCatching { scenarioDao.getScenariosByTenantSync(tenantId).size }.getOrDefault(0)
            )
        } else Triple(0, 0, 0)

        return Stats(
            knowledgeCount = knowledgeCount,
            blacklistCount = blacklistCount,
            modelCount = modelCount,
            appCount = appCount,
            scenarioCount = scenarioCount
        )
    }
}
