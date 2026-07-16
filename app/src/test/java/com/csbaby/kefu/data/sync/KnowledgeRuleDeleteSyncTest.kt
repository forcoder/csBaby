import com.csbaby.kefu.data.local.dao.KeywordRuleDao
import com.csbaby.kefu.data.local.dao.ScenarioDao
import com.csbaby.kefu.data.local.entity.KeywordRuleEntity
import com.csbaby.kefu.data.repository.KeywordRuleRepositoryImpl
import com.csbaby.kefu.data.sync.AuthManager
import com.csbaby.kefu.data.sync.SyncManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 测试知识库规则删除功能
 * 验证：当本地删除规则时，能够正确标记为软删除并触发同步
 */
class KnowledgeRuleDeleteSyncTest {

    private lateinit var keywordRuleDao: KeywordRuleDao
    private lateinit var scenarioDao: ScenarioDao
    private lateinit var syncManager: SyncManager
    private lateinit var authManager: AuthManager
    private lateinit var keywordRuleRepository: KeywordRuleRepositoryImpl

    @Before
    fun setup() {
        keywordRuleDao = mockk()
        scenarioDao = mockk()
        syncManager = mockk()
        authManager = mockk()

        // 创建 Repository 实例
        keywordRuleRepository = KeywordRuleRepositoryImpl(
            keywordRuleDao = keywordRuleDao,
            scenarioDao = scenarioDao,
            syncManager = syncManager,
            authManager = authManager
        )

        // 设置同步管理器行为
        coEvery { syncManager.triggerSync(any()) } returns Unit
    }

    @Test
    fun `删除规则后应该调用软删除并触发同步`() = runTest {
        // 准备测试数据
        val ruleId = 100L
        val rule = KeywordRuleEntity(
            id = ruleId,
            keyword = "测试关键词",
            matchType = "EXACT",
            replyTemplate = "测试回复",
            category = "测试"
        )

        // 模拟 DAO 行为
        coEvery { keywordRuleDao.getById(ruleId) } returns rule
        coEvery { scenarioDao.deleteRelationsForRule(ruleId) } returns Unit
        coEvery { keywordRuleDao.softDelete(ruleId) } returns 1

        // 执行删除操作
        val result = keywordRuleRepository.deleteRule(ruleId)

        // 验证删除操作成功
        assertTrue(result.isSuccess)

        // 验证 softDelete 被调用
        coVerify { keywordRuleDao.softDelete(ruleId) }

        // 验证同步被触发
        coVerify { syncManager.triggerSync(any()) }
    }

    @Test
    fun `删除不存在的规则应该返回错误`() = runTest {
        // 准备测试数据
        val nonExistentRuleId = 999L

        // 模拟 DAO 返回 null（规则不存在）
        coEvery { keywordRuleDao.getById(nonExistentRuleId) } returns null
        coEvery { scenarioDao.deleteRelationsForRule(nonExistentRuleId) } returns Unit

        // 执行删除操作
        val result = keywordRuleRepository.deleteRule(nonExistentRuleId)

        // 验证删除操作失败
        assertTrue(result.isFailure)

        // 验证 softDelete 没有被调用
        coVerify(exactly = 0) { keywordRuleDao.softDelete(any()) }

        // 验证同步没有被触发
        coVerify(exactly = 0) { syncManager.triggerSync() }
    }

    @Test
    fun `删除规则时数据库操作失败应该返回错误`() = runTest {
        // 准备测试数据
        val ruleId = 400L
        val rule = KeywordRuleEntity(
            id = ruleId,
            keyword = "测试关键词",
            matchType = "EXACT",
            replyTemplate = "测试回复",
            category = "测试"
        )

        // 模拟 DAO 行为
        coEvery { keywordRuleDao.getById(ruleId) } returns rule
        coEvery { scenarioDao.deleteRelationsForRule(ruleId) } returns Unit
        coEvery { keywordRuleDao.softDelete(ruleId) } returns 0  // 模拟更新失败（0 行受影响）

        // 执行删除操作
        val result = keywordRuleRepository.deleteRule(ruleId)

        // 验证删除操作失败
        assertTrue(result.isFailure)

        // 验证 syncManager.triggerSync() 没有被调用
        coVerify(exactly = 0) { syncManager.triggerSync() }
    }
}