package com.csbaby.kefu.infrastructure.knowledge

import com.csbaby.kefu.domain.model.KeywordRule
import com.csbaby.kefu.domain.model.MatchType
import com.csbaby.kefu.domain.repository.KeywordRuleRepository
import com.csbaby.kefu.domain.repository.ReplyHistoryRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 知识库规则优化器
 * 负责智能推荐和自动优化知识库规则
 */
@Singleton
class RuleOptimizer @Inject constructor(
    private val keywordRuleRepository: KeywordRuleRepository,
    private val replyHistoryRepository: ReplyHistoryRepository,
    private val keywordMatcher: KeywordMatcher
) {
    
    companion object {
        private const val TAG = "RuleOptimizer"
        private const val MIN_HISTORY_COUNT = 10
        private const val CONFIDENCE_THRESHOLD = 0.6f
    }
    
    /**
     * 优化现有规则
     */
    suspend fun optimizeRules() {
        try {
            val rules = keywordRuleRepository.getAllRules().first()
            val history = replyHistoryRepository.getRecentReplies(100).first()
            
            if (history.size < MIN_HISTORY_COUNT) {
                Timber.d("历史数据不足，跳过规则优化")
                return
            }
            
            // 分析规则使用情况
            val ruleUsage = analyzeRuleUsage(history, rules)
            
            // 优化规则优先级
            optimizeRulePriorities(ruleUsage)
            
            // 识别需要新增的规则
            val suggestedRules = generateSuggestedRules(history, rules)
            
            Timber.d("优化完成，建议新增 ${suggestedRules.size} 条规则")
            
        } catch (e: Exception) {
            Timber.e(e, "规则优化失败")
        }
    }
    
    /**
     * 分析规则使用情况
     */
    private fun analyzeRuleUsage(history: List<com.csbaby.kefu.domain.model.ReplyHistory>, rules: List<KeywordRule>): Map<Long, RuleUsage>
    {
        val usageMap = mutableMapOf<Long, RuleUsage>()
        
        // 初始化使用情况
        rules.forEach { rule ->
            usageMap[rule.id] = RuleUsage(rule, 0, 0f)
        }
        
        // 分析历史记录
        history.forEach { record ->
            record.ruleMatchedId?.let { ruleId ->
                usageMap[ruleId]?.let { usage ->
                    usageMap[ruleId] = usage.copy(
                        usageCount = usage.usageCount + 1
                    )
                }
            }
        }
        
        return usageMap
    }
    
    /**
     * 优化规则优先级
     */
    private suspend fun optimizeRulePriorities(ruleUsage: Map<Long, RuleUsage>) {
        ruleUsage.forEach { (ruleId, usage) ->
            // 根据使用频率调整优先级
            val newPriority = when {
                usage.usageCount > 20 -> 10
                usage.usageCount > 10 -> 8
                usage.usageCount > 5 -> 6
                else -> usage.rule.priority
            }
            
            if (newPriority != usage.rule.priority) {
                // 暂时注释掉，需要在KeywordRuleRepository中添加updatePriority方法
                // keywordRuleRepository.updatePriority(ruleId, newPriority)
                Timber.d("更新规则 $ruleId 优先级从 ${usage.rule.priority} 到 $newPriority")
            }
        }
    }
    
    /**
     * 生成建议的新规则
     */
    private fun generateSuggestedRules(
        history: List<com.csbaby.kefu.domain.model.ReplyHistory>,
        existingRules: List<KeywordRule>
    ): List<KeywordRule>
    {
        val suggestedRules = mutableListOf<KeywordRule>()
        val messageGroups = mutableMapOf<String, MutableList<String>>()
        
        // 按消息内容分组回复
        history.forEach { record ->
            if (record.ruleMatchedId == null && record.modified) {
                // 只考虑未匹配规则且用户修改过的回复
                val key = record.originalMessage.take(50) // 使用消息前50个字符作为键
                messageGroups.getOrPut(key) { mutableListOf() }.add(record.finalReply)
            }
        }
        
        // 分析每组消息，生成规则建议
        messageGroups.forEach { (message, replies) ->
            if (replies.size >= 3) {
                // 如果同一类消息有3个以上的回复，考虑生成规则
                val commonReply = findMostCommonReply(replies)
                if (commonReply != null) {
                    // 提取关键词
                    val keywords = extractKeywords(message)
                    keywords.forEach { keyword ->
                        // 检查是否已存在类似规则
                        if (!existingRules.any { it.keyword == keyword && it.replyTemplate == commonReply }) {
                            val suggestedRule = KeywordRule(
                                id = 0, // 新规则，ID会由数据库生成
                                keyword = keyword,
                                matchType = MatchType.CONTAINS,
                                replyTemplate = commonReply,
                                priority = 5,
                                enabled = true,
                                category = "auto_generated",
                                targetType = com.csbaby.kefu.domain.model.RuleTargetType.ALL,
                                targetNames = emptyList()
                            )
                            suggestedRules.add(suggestedRule)
                        }
                    }
                }
            }
        }
        
        return suggestedRules
    }
    
    /**
     * 找到最常见的回复
     */
    private fun findMostCommonReply(replies: List<String>): String? {
        val replyCounts = replies.groupingBy { it }.eachCount()
        return replyCounts.maxByOrNull { it.value }?.key
    }
    
    /**
     * 提取消息中的关键词
     */
    private fun extractKeywords(message: String): List<String> {
        val keywords = mutableListOf<String>()
        
        // 简单的关键词提取逻辑
        val words = message.split(Regex("[\\s,，.。!！?？;；:：]+"))
        words.forEach { word ->
            val trimmedWord = word.trim()
            if (trimmedWord.length >= 2 && trimmedWord.length <= 10) {
                keywords.add(trimmedWord)
            }
        }
        
        return keywords.distinct().take(5) // 最多提取5个关键词
    }
    
    /**
     * 获取规则优化建议
     */
    suspend fun getOptimizationSuggestions(): List<OptimizationSuggestion> {
        val suggestions = mutableListOf<OptimizationSuggestion>()
        
        try {
            val rules = keywordRuleRepository.getAllRules().first()
            val history = replyHistoryRepository.getRecentReplies(50).first()
            
            // 分析未匹配的消息
            val unmatchedMessages = history.filter { it.ruleMatchedId == null }
            if (unmatchedMessages.size > 5) {
                suggestions.add(
                    OptimizationSuggestion(
                        type = SuggestionType.UNMATCHED_MESSAGES,
                        message = "发现 ${unmatchedMessages.size} 条未匹配的消息，建议添加相应规则",
                        priority = 3
                    )
                )
            }
            
            // 分析低优先级规则
            val lowPriorityRules = rules.filter { it.priority < 3 && it.enabled }
            if (lowPriorityRules.size > 10) {
                suggestions.add(
                    OptimizationSuggestion(
                        type = SuggestionType.LOW_PRIORITY_RULES,
                        message = "有 ${lowPriorityRules.size} 条低优先级规则，建议调整",
                        priority = 2
                    )
                )
            }
            
            // 分析规则覆盖情况
            val coverage = calculateRuleCoverage(history, rules)
            if (coverage < 0.5f) {
                suggestions.add(
                    OptimizationSuggestion(
                        type = SuggestionType.LOW_COVERAGE,
                        message = "规则覆盖率较低 (${(coverage * 100).toInt()}%)，建议添加更多规则",
                        priority = 3
                    )
                )
            }
            
        } catch (e: Exception) {
            Timber.e(e, "获取优化建议失败")
        }
        
        return suggestions
    }
    
    /**
     * 计算规则覆盖率
     */
    private fun calculateRuleCoverage(
        history: List<com.csbaby.kefu.domain.model.ReplyHistory>,
        rules: List<KeywordRule>
    ): Float {
        if (history.isEmpty()) return 0f
        
        val matchedCount = history.count { it.ruleMatchedId != null }
        return matchedCount.toFloat() / history.size
    }
    
    /**
     * 规则使用情况
     */
    data class RuleUsage(
        val rule: KeywordRule,
        var usageCount: Int,
        var successRate: Float
    )
    
    /**
     * 优化建议
     */
    data class OptimizationSuggestion(
        val type: SuggestionType,
        val message: String,
        val priority: Int // 1-3，3为最高
    )
    
    /**
     * 建议类型
     */
    enum class SuggestionType {
        UNMATCHED_MESSAGES,
        LOW_PRIORITY_RULES,
        LOW_COVERAGE,
        DUPLICATE_RULES,
        INVALID_RULES
    }
}