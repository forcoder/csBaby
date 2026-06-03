# MVVM Pattern

## Pattern Overview

csBaby使用MVVM模式管理UI状态和数据流：

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│    View     │ ◄── │  ViewModel   │ ◄── │    Model    │
│ (Composable)│     │ (StateFlow)  │     │ (Repository)│
└─────────────┘     └──────────────┘     └─────────────┘
     UI Events            State              Data
```

## State Management

### UiState Pattern
```kotlin
data class KnowledgeUiState(
    val rules: List<KeywordRule> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

### StateFlow Usage
```kotlin
class KnowledgeViewModel @Inject constructor(
    private val repository: KeywordRuleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KnowledgeUiState())
    val uiState: StateFlow<KnowledgeUiState> = _uiState.asStateFlow()

    fun loadRules() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getAllRules().collect { rules ->
                _uiState.update {
                    it.copy(rules = rules, isLoading = false)
                }
            }
        }
    }
}
```

## Unidirectional Data Flow

1. **User Action** → View发送事件
2. **ViewModel** → 处理业务逻辑
3. **Repository** → 访问数据
4. **State Update** → ViewModel更新State
5. **UI Render** → Compose观察State变化

## ViewModel Guidelines

- **禁止持有**: View引用、Context引用
- **必须持有**: Repository引用、StateFlow
- **生命周期**: 自动处理配置变更

## Related
- [[Architecture Overview]]
- [[Clean Architecture]]
- [[Repository Pattern]]
- [[Coding Standards]]