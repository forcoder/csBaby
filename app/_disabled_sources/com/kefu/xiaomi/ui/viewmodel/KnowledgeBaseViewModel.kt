package com.kefu.xiaomi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefu.xiaomi.data.model.KeywordRule
import com.kefu.xiaomi.data.model.MatchType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KnowledgeBaseUiState(
    val rules: List<KeywordRule> = emptyList(),
    val categories: List<String> = listOf("全部", "售前咨询", "售后问题", "投诉处理", "物流咨询"),
    val selectedCategory: String = "全部",
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val showAddDialog: Boolean = false,
    val editingRule: KeywordRule? = null
)

@HiltViewModel
class KnowledgeBaseViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(KnowledgeBaseUiState())
    val uiState: StateFlow<KnowledgeBaseUiState> = _uiState.asStateFlow()

    init {
        loadRules()
    }

    private fun loadRules() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // 模拟加载数据
            val rules = listOf(
                KeywordRule(
                    id = 1,
                    keyword = "价格",
                    matchType = MatchType.CONTAINS,
                    replyTemplate = "您好，感谢您的咨询。关于价格问题，我们的产品定价为 {price} 元，现在正在促销中，详情请查看我们的官网。",
                    category = "售前咨询",
                    priority = 1,
                    enabled = true
                ),
                KeywordRule(
                    id = 2,
                    keyword = "发货",
                    matchType = MatchType.CONTAINS,
                    replyTemplate = "您好，您的订单将在 24 小时内发货，预计 {delivery_time} 天到达。",
                    category = "售后问题",
                    priority = 2,
                    enabled = true
                ),
                KeywordRule(
                    id = 3,
                    keyword = "退货",
                    matchType = MatchType.CONTAINS,
                    replyTemplate = "您好，我们支持 7 天无理由退货，请联系客服提供订单号办理退货手续。",
                    category = "售后问题",
                    priority = 1,
                    enabled = true
                ),
                KeywordRule(
                    id = 4,
                    keyword = "投诉",
                    matchType = MatchType.EXACT,
                    replyTemplate = "非常抱歉给您带来不好的体验，请您详细描述您的问题，我们会第一时间处理并给您反馈。",
                    category = "投诉处理",
                    priority = 0,
                    enabled = true
                ),
                KeywordRule(
                    id = 5,
                    keyword = "物流",
                    matchType = MatchType.CONTAINS,
                    replyTemplate = "您的订单正在配送中，快递单号：{tracking_no}，您可以点击查看物流详情。",
                    category = "物流咨询",
                    priority = 2,
                    enabled = false
                )
            )
            _uiState.value = _uiState.value.copy(
                rules = rules,
                isLoading = false
            )
        }
    }

    fun setSelectedCategory(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun getFilteredRules(): List<KeywordRule> {
        val state = _uiState.value
        return state.rules.filter { rule ->
            val matchesCategory = state.selectedCategory == "全部" || rule.category == state.selectedCategory
            val matchesSearch = state.searchQuery.isEmpty() ||
                    rule.keyword.contains(state.searchQuery, ignoreCase = true) ||
                    rule.replyTemplate.contains(state.searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true, editingRule = null)
    }

    fun showEditDialog(rule: KeywordRule) {
        _uiState.value = _uiState.value.copy(showAddDialog = true, editingRule = rule)
    }

    fun hideDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false, editingRule = null)
    }

    fun saveRule(rule: KeywordRule) {
        viewModelScope.launch {
            val rules = if (rule.id == 0L) {
                // 添加新规则
                val newRule = rule.copy(id = System.currentTimeMillis())
                _uiState.value.rules + newRule
            } else {
                // 更新规则
                _uiState.value.rules.map {
                    if (it.id == rule.id) rule else it
                }
            }
            _uiState.value = _uiState.value.copy(rules = rules, showAddDialog = false, editingRule = null)
        }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch {
            val rules = _uiState.value.rules.filter { it.id != ruleId }
            _uiState.value = _uiState.value.copy(rules = rules)
        }
    }

    fun toggleRuleEnabled(ruleId: Long) {
        viewModelScope.launch {
            val rules = _uiState.value.rules.map { rule ->
                if (rule.id == ruleId) {
                    rule.copy(enabled = !rule.enabled)
                } else {
                    rule
                }
            }
            _uiState.value = _uiState.value.copy(rules = rules)
        }
    }

    fun exportRules() {
        // 导出到CSV
    }

    fun importRules() {
        // 从CSV导入
    }
}
