# 悬浮窗知识库搜索增强实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现悬浮窗知识库搜索的输入即搜索和搜索联想功能

**Architecture:** 在现有 FloatingWindowService 中添加 TextWatcher 监听 + PopupWindow 联想下拉 + 300ms 防抖机制

**Tech Stack:** Android View System (EditText, PopupWindow, ListView, TextWatcher, Handler)

---

## 文件变更

**Modify:** `app/src/main/java/com/csbaby/kefu/infrastructure/window/FloatingWindowService.kt`

---

## 实现任务

### Task 1: 添加新变量

**Files:**
- Modify: `FloatingWindowService.kt:100-110`

- [ ] **Step 1: 添加联想和防抖相关变量**

在现有变量声明区域添加以下变量（第108行 currentTab 之后）：

```kotlin
// 搜索联想相关变量
private var suggestionPopup: PopupWindow? = null
private var suggestionListView: ListView? = null
private var suggestionAdapter: ArrayAdapter<String>? = null

// 防抖相关变量
private val searchDebounceHandler = Handler(Looper.getMainLooper())
private var searchDebounceRunnable: Runnable? = null
private var lastSearchQuery: String = ""
private val DEBOUNCE_DELAY_MS = 300L
private val DISMISS_DELAY_MS = 200L
private val MAX_SUGGESTIONS = 5
```

- [ ] **Step 2: 提交变更**

```bash
git add app/src/main/java/com/csbaby/kefu/infrastructure/window/FloatingWindowService.kt
git commit -m "chore: 添加知识库搜索增强相关变量"
```

---

### Task 2: 创建联想下拉 PopupWindow

**Files:**
- Modify: `FloatingWindowService.kt` (在 removeFloatingView 方法之前添加新方法，约1747行附近)

- [ ] **Step 1: 添加 createSuggestionPopup 方法**

在 `removeFloatingView()` 方法之前添加：

```kotlin
/**
 * 创建搜索联想下拉 PopupWindow
 */
private fun createSuggestionPopup(): PopupWindow {
    val popupView = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
        background = createSuggestionListBackground()
    }

    suggestionListView = ListView(this).apply {
        divider = null
        dividerHeight = 0
        scrollBarStyle = ListView.SCROLLBARS_NONE
    }

    suggestionAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
    suggestionListView?.adapter = suggestionAdapter

    suggestionListView?.setOnItemClickListener { _, _, position, _ ->
        val keyword = suggestionAdapter?.getItem(position) ?: return@setOnItemClickListener
        hideSuggestionPopup()
        fillSearchInputAndSearch(keyword)
    }

    popupView.addView(suggestionListView)

    return PopupWindow(
        popupView,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        true
    ).apply {
        elevation = dp(8).toFloat()
        setBackgroundDrawable(GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(Color.parseColor("#1E293B"))
            setStroke(dp(1), Color.parseColor("#334155"))
        })
        isOutsideTouchable = true
        inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
    }
}

/**
 * 创建联想列表背景
 */
private fun createSuggestionListBackground(): GradientDrawable {
    return GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(
            Color.parseColor("#1E293B"),
            Color.parseColor("#0F172A")
        )
    ).apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(12).toFloat()
    }
}

/**
 * 填充搜索框并执行搜索
 */
private fun fillSearchInputAndSearch(keyword: String) {
    knowledgeSearchEditText?.setText(keyword)
    knowledgeSearchEditText?.setSelection(keyword.length)
    performKnowledgeSearch()
}
```

- [ ] **Step 2: 提交变更**

```bash
git add app/src/main/java/com/csbaby/kefu/infrastructure/window/FloatingWindowService.kt
git commit -m "feat: 添加联想下拉 PopupWindow 创建方法"
```

---

### Task 3: 添加 TextWatcher 和显示/隐藏方法

**Files:**
- Modify: `FloatingWindowService.kt`

- [ ] **Step 1: 添加 showSuggestionPopup 和 hideSuggestionPopup 方法**

在 `createSuggestionPopup()` 方法之后添加：

```kotlin
/**
 * 显示搜索联想下拉
 */
private fun showSuggestionPopup(results: List<String>) {
    if (results.isEmpty()) {
        hideSuggestionPopup()
        return
    }

    if (suggestionPopup == null) {
        suggestionPopup = createSuggestionPopup()
    }

    suggestionAdapter?.clear()
    suggestionAdapter?.addAll(results.take(MAX_SUGGESTIONS))

    knowledgeSearchEditText?.let { editText ->
        suggestionPopup?.width = editText.width
        suggestionPopup?.showAsDropDown(editText, 0, dp(4))
    }
}

/**
 * 隐藏搜索联想下拉
 */
private fun hideSuggestionPopup() {
    suggestionPopup?.dismiss()
}

/**
 * 设置搜索框 TextWatcher
 */
private fun setupSearchTextWatcher() {
    knowledgeSearchEditText?.addTextChangedListener(object : android.text.TextWatcher {
        private var lastQuery = ""

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val newQuery = s?.toString()?.trim() ?: ""
            if (newQuery != lastQuery) {
                lastQuery = newQuery
                onSearchQueryChanged(newQuery)
            }
        }

        override fun afterTextChanged(s: android.text.Editable?) {}
    })

    knowledgeSearchEditText?.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
        if (!hasFocus) {
            // 延迟隐藏，给点击联想词留出时间
            searchDebounceHandler.postDelayed({
                hideSuggestionPopup()
            }, DISMISS_DELAY_MS)
        }
    }
}
```

- [ ] **Step 2: 提交变更**

```bash
git add app/src/main/java/com/csbaby/kefu/infrastructure/window/FloatingWindowService.kt
git commit -m "feat: 添加 TextWatcher 和联想显示隐藏方法"
```

---

### Task 4: 实现防抖搜索和联想查询

**Files:**
- Modify: `FloatingWindowService.kt`

- [ ] **Step 1: 添加 onSearchQueryChanged 方法**

在 `setupSearchTextWatcher()` 方法之后添加：

```kotlin
/**
 * 处理搜索输入变化
 * 1. 取消之前的防抖任务
 * 2. 延迟执行搜索（300ms 防抖）
 * 3. 同时更新联想列表
 */
private fun onSearchQueryChanged(query: String) {
    // 取消之前的防抖任务
    searchDebounceRunnable?.let { searchDebounceHandler.removeCallbacks(it) }

    if (query.isBlank()) {
        lastSearchQuery = ""
        hideSuggestionPopup()
        return
    }

    // 如果查询没变化，不重复搜索
    if (query == lastSearchQuery) return

    // 创建新的防抖任务
    searchDebounceRunnable = Runnable {
        lastSearchQuery = query
        performKnowledgeSearch()
        updateSearchSuggestions(query)
    }

    // 延迟执行搜索
    searchDebounceHandler.postDelayed(searchDebounceRunnable!!, DEBOUNCE_DELAY_MS)
}

/**
 * 更新搜索联想列表
 */
private fun updateSearchSuggestions(query: String) {
    if (query.isBlank()) {
        hideSuggestionPopup()
        return
    }

    serviceScope.launch {
        try {
            val keywords = getMatchingKeywords(query)
            android.os.Handler(Looper.getMainLooper()).post {
                showSuggestionPopup(keywords)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取联想词失败", e)
        }
    }
}

/**
 * 获取匹配的关键词列表
 */
private suspend fun getMatchingKeywords(query: String): List<String> {
    val keywords = mutableListOf<String>()
    try {
        // 从 replyOrchestrator 获取所有关键词
        val allRules = replyOrchestrator.getAllKnowledgeRules()
        keywords.addAll(
            allRules
                .map { it.keyword }
                .filter { it.contains(query, ignoreCase = true) }
                .distinct()
                .take(MAX_SUGGESTIONS)
        )
    } catch (e: Exception) {
        Log.e(TAG, "获取知识库关键词失败", e)
    }
    return keywords
}
```

- [ ] **Step 2: 在 ReplyOrchestrator 中添加获取所有规则的方法**

**Files:**
- Modify: `app/src/main/java/com/csbaby/kefu/infrastructure/reply/ReplyOrchestrator.kt`

在 `searchKnowledgeRules` 方法附近添加：

```kotlin
/**
 * 获取所有知识库规则（用于搜索联想）
 */
suspend fun getAllKnowledgeRules(): List<KnowledgeRuleItem> {
    return try {
        val keywordRuleDao = DatabaseManager.getInstance().keywordRuleDao()
        keywordRuleDao.getAllRules()
            .filter { it.enabled }
            .map { entity ->
                KnowledgeRuleItem(
                    keyword = entity.keyword,
                    replyTemplate = entity.replyTemplate,
                    matchType = entity.matchType.name,
                    targetNames = emptyList()
                )
            }
    } catch (e: Exception) {
        Log.e(TAG, "获取所有知识库规则失败", e)
        emptyList()
    }
}
```

- [ ] **Step 3: 提交变更**

```bash
git add app/src/main/java/com/csbaby/kefu/infrastructure/window/FloatingWindowService.kt
git add app/src/main/java/com/csbaby/kefu/infrastructure/reply/ReplyOrchestrator.kt
git commit -m "feat: 实现防抖搜索和联想查询功能"
```

---

### Task 5: 在 createFloatingView 中绑定 TextWatcher

**Files:**
- Modify: `FloatingWindowService.kt:714` 附近

- [ ] **Step 1: 在 knowledgeSearchEditText 创建后添加 TextWatcher**

找到第714行的 `knowledgeSearchEditText` 创建代码块，在其末尾（`.background = createMessageCardBackground()` 之后）添加：

```kotlin
// 设置搜索输入监听
setupSearchTextWatcher()
```

- [ ] **Step 2: 提交变更**

```bash
git add app/src/main/java/com/csbaby/kefu/infrastructure/window/FloatingWindowService.kt
git commit -m "feat: 在创建搜索框时绑定 TextWatcher"
```

---

### Task 6: 在 removeFloatingView 中清理资源

**Files:**
- Modify: `FloatingWindowService.kt:1573-1605`

- [ ] **Step 1: 在 removeFloatingView 方法中添加清理代码**

在 `windowLayoutParams = null` 之前添加：

```kotlin
// 清理搜索联想相关资源
suggestionPopup?.dismiss()
suggestionPopup = null
suggestionListView = null
suggestionAdapter = null
searchDebounceRunnable?.let { searchDebounceHandler.removeCallbacks(it) }
searchDebounceRunnable = null
lastSearchQuery = ""
```

- [ ] **Step 2: 提交变更**

```bash
git add app/src/main/java/com/csbaby/kefu/infrastructure/window/FloatingWindowService.kt
git commit -m "chore: 在 removeFloatingView 中清理联想资源"
```

---

### Task 7: 本地编译验证

**Files:**
- 无文件变更

- [ ] **Step 1: 运行 Gradle 构建**

```bash
cd D:/workspace/workbuddy/csBaby
./gradlew assembleDebug --no-daemon
```

预期输出：BUILD SUCCESSFUL

- [ ] **Step 2: 提交验证结果**

```bash
git add -A
git commit -m "chore: 知识库搜索增强功能实现完成，编译通过"
```

---

## 实现确认清单

- [x] Task 1: 添加新变量
- [x] Task 2: 创建联想下拉 PopupWindow
- [x] Task 3: 添加 TextWatcher 和显示/隐藏方法
- [x] Task 4: 实现防抖搜索和联想查询
- [x] Task 5: 在 createFloatingView 中绑定 TextWatcher
- [x] Task 6: 在 removeFloatingView 中清理资源
- [x] Task 7: 本地编译验证