package com.csbaby.kefu.infrastructure.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import com.csbaby.kefu.data.local.PreferencesManager


class ChatAutomationAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        // API 26+ 中 serviceInfo 由系统管理，不可直接赋值。
        // packageNames 已在 accessibility_service_config.xml 中配置，无需在此覆盖。
        instance = this
        Log.d(TAG, "Chat automation accessibility service connected, serviceInfo=$serviceInfo")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 目前只需要保持服务在线，在点击悬浮窗发送按钮时读取当前聊天窗口并执行发送。
    }

    override fun onInterrupt() {
        Log.d(TAG, "Chat automation accessibility service interrupted")
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    private fun sendReplyInternal(reply: String, targetPackage: String?): SendReplyStatus {
        val root = resolveRootForPackage(targetPackage)
        if (root == null) {
            Log.w(TAG, "sendReplyInternal: root is null, targetPackage=$targetPackage")
            return SendReplyStatus.NO_ACTIVE_WINDOW
        }
        val activePackage = root.packageName?.toString().orEmpty()
        Log.d(TAG, "sendReplyInternal: activePackage=$activePackage, targetPackage=$targetPackage")

        if (!targetPackage.isNullOrBlank() && activePackage != targetPackage) {
            Log.w(TAG, "Active window package mismatch. expected=$targetPackage actual=$activePackage")
            return SendReplyStatus.WRONG_WINDOW
        }

        val preparedRoot = ensureChatComposerReady(root, activePackage, targetPackage)
        val inputNode = findInputNode(preparedRoot, activePackage)
        if (inputNode == null) {
            Log.w(TAG, "sendReplyInternal: inputNode not found for package=$activePackage")
            // 打印窗口层级帮助调试
            logAllRelevantWindows(targetPackage = activePackage)
            return SendReplyStatus.INPUT_NOT_FOUND
        }
        prepareInputNode(inputNode)
        SystemClock.sleep(80)
        if (!setInputText(inputNode, reply)) {
            val refreshedRoot = resolveRootForPackage(targetPackage) ?: preparedRoot
            val retryInputNode = findInputNode(refreshedRoot, activePackage) ?: return SendReplyStatus.INPUT_FAILED
            prepareInputNode(retryInputNode)
            SystemClock.sleep(80)
            if (!setInputText(retryInputNode, reply)) {
                return SendReplyStatus.INPUT_FAILED
            }
        }

        SystemClock.sleep(120)
        val latestRoot = resolveRootForPackage(targetPackage) ?: preparedRoot
        val latestInputNode = findInputNode(latestRoot, activePackage, shouldLogFailure = false)
        val sendButton = findSendButton(latestRoot, activePackage, latestInputNode) ?: return SendReplyStatus.SEND_BUTTON_NOT_FOUND
        if (!performClick(sendButton)) {
            return SendReplyStatus.CLICK_FAILED
        }

        Log.d(TAG, "Send reply succeeded for package=$activePackage")
        return SendReplyStatus.SUCCESS
    }


    private fun resolveRootForPackage(targetPackage: String?): AccessibilityNodeInfo? {
        val activeRoot = rootInActiveWindow
        val activePackage = activeRoot?.packageName?.toString().orEmpty()
        if (targetPackage.isNullOrBlank()) {
            return activeRoot
        }
        if (activePackage == targetPackage) {
            return activeRoot
        }

        val candidateRoots = buildList {
            activeRoot?.let(::add)
            windows
                .asSequence()
                .mapNotNull { it.root }
                .filter { it.packageName?.toString() == targetPackage }
                .forEach(::add)
        }.distinctBy { System.identityHashCode(it) }

        val bestMatch = candidateRoots
            .filter { it.packageName?.toString() == targetPackage }
            .maxByOrNull { scoreWindowRoot(it, targetPackage) }

        if (bestMatch == null) {
            Log.w(TAG, "Target package window not found in interactive windows. expected=$targetPackage active=$activePackage")
        } else {
            Log.d(TAG, "Resolved window root for package=$targetPackage score=${scoreWindowRoot(bestMatch, targetPackage)}")
        }
        return bestMatch ?: activeRoot
    }

    private fun ensureChatComposerReady(
        root: AccessibilityNodeInfo,
        activePackage: String,
        targetPackage: String?
    ): AccessibilityNodeInfo {
        if (hasLikelyInputCandidate(root, activePackage)) {
            return root
        }

        val activator = findComposerActivator(root, activePackage) ?: return root
        Log.d(TAG, "Attempting to activate composer for package=$activePackage via ${describeNode(activator)}")
        performClick(activator)
        SystemClock.sleep(140)
        return resolveRootForPackage(targetPackage) ?: root
    }

    private fun scoreWindowRoot(root: AccessibilityNodeInfo, targetPackage: String): Int {
        if (root.packageName?.toString() != targetPackage) {
            return Int.MIN_VALUE
        }

        var score = 220
        if (hasLikelyInputCandidate(root, targetPackage)) score += 120
        if (findSendButton(root, targetPackage, inputNode = null, shouldLog = false) != null) score += 80
        if (findComposerActivator(root, targetPackage, shouldLog = false) != null) score += 45
        return score
    }

    private fun hasLikelyInputCandidate(root: AccessibilityNodeInfo, activePackage: String): Boolean {
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && scoreInputCandidate(focusedNode, activePackage, depth = 0) > 0) {
            return true
        }

        val candidates = mutableListOf<ScoredNode>()
        collectInputCandidates(root, candidates, activePackage, depth = 0)
        return candidates.isNotEmpty()
    }

    private fun findInputNode(
        root: AccessibilityNodeInfo?,
        activePackage: String,
        shouldLogFailure: Boolean = true
    ): AccessibilityNodeInfo? {

        root ?: return null

        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && scoreInputCandidate(focusedNode, activePackage, depth = 0) > 0) {
            Log.d(TAG, "Using focused input node: ${describeNode(focusedNode)}")
            return focusedNode
        }

        val candidates = mutableListOf<ScoredNode>()
        collectInputCandidates(root, candidates, activePackage, depth = 0)
        val bestCandidate = candidates.maxByOrNull { it.score }
        if (bestCandidate != null) {
            Log.d(TAG, "Found input node score=${bestCandidate.score}: ${describeNode(bestCandidate.node)}")
            return bestCandidate.node
        }

        val sendNeighbor = findInputNearSendButton(
            findSendButton(root, activePackage, inputNode = null, shouldLog = false),
            activePackage
        )
        if (sendNeighbor != null) {
            Log.d(TAG, "Using nearby input node around send button: ${describeNode(sendNeighbor)}")
            return sendNeighbor
        }

        if (shouldLogFailure) {
            Log.w(TAG, "Failed to find input node in package=$activePackage, rootChildCount=${root.childCount}")
            // 轻量级打印可编辑节点（限制深度和数量，避免ANR）
            val editableNodes = mutableListOf<String>()
            collectEditableNodes(root, editableNodes, depth = 0, maxDepth = 3, maxNodes = 20)
            Log.w(TAG, "Editable nodes found: ${editableNodes.size}")
            editableNodes.forEach { Log.w(TAG, "  editable: $it") }
        }
        return null
    }



    private fun collectInputCandidates(
        node: AccessibilityNodeInfo?,
        candidates: MutableList<ScoredNode>,
        activePackage: String,
        depth: Int
    ) {
        node ?: return

        val score = scoreInputCandidate(node, activePackage, depth)
        if (score > 0) {
            candidates += ScoredNode(node, score)
        }

        for (index in 0 until node.childCount) {
            collectInputCandidates(node.getChild(index), candidates, activePackage, depth + 1)
        }
    }

    private fun scoreInputCandidate(node: AccessibilityNodeInfo, activePackage: String, depth: Int): Int {
        if (!node.isVisibleToUser) return Int.MIN_VALUE

        val profile = getChatAppProfile(activePackage)
        val className = node.className?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()
        val description = node.contentDescription?.toString().orEmpty()
        val text = node.text?.toString().orEmpty()
        val hint = getNodeHint(node)
        val supportsSetText = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
        val supportsPaste = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_PASTE }
        val bounds = getNodeBounds(node)

        var score = 0
        if (node.isEditable) score += 140
        if (supportsSetText) score += 110
        if (supportsPaste) score += 55

        if (className.contains("EditText", ignoreCase = true)) score += 90
        if (className.contains("AutoCompleteTextView", ignoreCase = true)) score += 70
        if (node.isFocused) score += 30
        if (node.isFocusable) score += 22
        if (node.isClickable) score += 8
        if (containsAnyKeyword(viewId, INPUT_VIEW_ID_KEYWORDS)) score += 65
        if (containsAnyKeyword(hint, INPUT_HINT_KEYWORDS)) score += 60
        if (containsAnyKeyword(description, INPUT_HINT_KEYWORDS)) score += 35
        if (containsAnyKeyword(text, INPUT_HINT_KEYWORDS)) score += 28
        if (containsAnyKeyword(viewId, profile.inputIdKeywords)) score += 92
        if (matchesNodeKeywords(node, profile.inputTextKeywords)) score += 72
        if (isNodeNearBottom(bounds)) score += 26
        if (bounds.width() > resources.displayMetrics.widthPixels * 0.28f) score += 14

        if (activePackage == PreferencesManager.BAIJUYI_PACKAGE && className.contains("View", ignoreCase = true) && supportsSetText) {
            score += profile.customInputViewBonus
        }
        if (!node.isEditable && !supportsSetText && !supportsPaste &&
            !className.contains("EditText", ignoreCase = true) &&
            !className.contains("AutoCompleteTextView", ignoreCase = true)
        ) {
            score -= 150
        }
        if (containsAnyKeyword(className, NON_INPUT_CLASS_KEYWORDS)) score -= 120
        score -= depth.coerceAtMost(10) * 2

        return score
    }

    private fun findComposerActivator(
        root: AccessibilityNodeInfo?,
        activePackage: String,
        shouldLog: Boolean = true
    ): AccessibilityNodeInfo? {
        root ?: return null
        val candidates = mutableListOf<ScoredNode>()
        collectComposerActivators(root, candidates, activePackage, depth = 0)
        val bestCandidate = candidates.maxByOrNull { it.score }
        if (shouldLog && bestCandidate != null) {
            Log.d(TAG, "Found composer activator score=${bestCandidate.score}: ${describeNode(bestCandidate.node)}")
        }
        return bestCandidate?.node
    }

    private fun collectComposerActivators(
        node: AccessibilityNodeInfo?,
        candidates: MutableList<ScoredNode>,
        activePackage: String,
        depth: Int
    ) {
        node ?: return

        val score = scoreComposerActivator(node, activePackage, depth)
        if (score > 0) {
            candidates += ScoredNode(node, score)
        }

        for (index in 0 until node.childCount) {
            collectComposerActivators(node.getChild(index), candidates, activePackage, depth + 1)
        }
    }

    private fun scoreComposerActivator(node: AccessibilityNodeInfo, activePackage: String, depth: Int): Int {
        if (!node.isVisibleToUser) return Int.MIN_VALUE

        val profile = getChatAppProfile(activePackage)
        val className = node.className?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()
        val bounds = getNodeBounds(node)
        val supportsSetText = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
        val supportsPaste = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_PASTE }

        var score = 0
        if (matchesNodeKeywords(node, profile.composerKeywords)) score += 95
        if (containsAnyKeyword(viewId, profile.inputIdKeywords)) score += 62
        if (containsAnyKeyword(viewId, INPUT_VIEW_ID_KEYWORDS)) score += 34
        if (node.isClickable) score += 40
        if (node.isFocusable) score += 20
        if (supportsSetText || supportsPaste || node.isEditable) score += 26
        if (isNodeNearBottom(bounds)) score += 28
        if (bounds.width() > resources.displayMetrics.widthPixels * 0.25f) score += 10
        if (className.contains("EditText", ignoreCase = true)) score += 24
        score -= depth.coerceAtMost(12)

        return score
    }

    private fun findInputNearSendButton(sendButton: AccessibilityNodeInfo?, activePackage: String): AccessibilityNodeInfo? {
        var currentNode = sendButton?.parent
        while (currentNode != null) {
            val localCandidates = mutableListOf<ScoredNode>()
            collectInputCandidates(currentNode, localCandidates, activePackage, depth = 0)
            val localBest = localCandidates.maxByOrNull { it.score }
            if (localBest != null) {
                return localBest.node
            }
            currentNode = currentNode.parent
        }
        return null
    }


    private fun findSendButton(
        root: AccessibilityNodeInfo?,
        activePackage: String,
        inputNode: AccessibilityNodeInfo? = null,
        shouldLog: Boolean = true
    ): AccessibilityNodeInfo? {
        root ?: return null

        val candidates = mutableListOf<ScoredNode>()
        collectSendButtonCandidates(root, candidates, activePackage, inputNode, depth = 0)
        val bestCandidate = candidates.maxByOrNull { it.score }
        if (shouldLog && bestCandidate != null) {
            Log.d(TAG, "Found send button score=${bestCandidate.score}: ${describeNode(bestCandidate.node)}")
        }
        return bestCandidate?.node
    }

    private fun collectSendButtonCandidates(
        node: AccessibilityNodeInfo?,
        candidates: MutableList<ScoredNode>,
        activePackage: String,
        inputNode: AccessibilityNodeInfo?,
        depth: Int
    ) {
        node ?: return

        val score = scoreSendCandidate(node, activePackage, inputNode, depth)
        if (score > 0) {
            candidates += ScoredNode(node, score)
        }

        for (index in 0 until node.childCount) {
            collectSendButtonCandidates(node.getChild(index), candidates, activePackage, inputNode, depth + 1)
        }
    }

    private fun scoreSendCandidate(
        node: AccessibilityNodeInfo,
        activePackage: String,
        inputNode: AccessibilityNodeInfo?,
        depth: Int
    ): Int {
        if (!node.isVisibleToUser) return Int.MIN_VALUE

        val profile = getChatAppProfile(activePackage)
        val text = node.text?.toString().orEmpty()
        val description = node.contentDescription?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()
        val className = node.className?.toString().orEmpty()
        val bounds = getNodeBounds(node)
        val canClickDirectly = node.isClickable || node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }

        var score = 0
        if (matchesSendLabel(text, description, viewId, activePackage)) score += 120
        if (containsAnyKeyword(viewId, SEND_VIEW_ID_KEYWORDS)) score += 65
        if (containsAnyKeyword(viewId, profile.sendIdKeywords)) score += 95
        if (containsAnyKeyword(description, profile.sendTextKeywords)) score += 70
        if (containsAnyKeyword(text, profile.sendTextKeywords)) score += 70
        if (canClickDirectly) score += 30
        if (node.isEnabled) score += 20
        if (className.contains("Button", ignoreCase = true)) score += 20
        if (className.contains("Image", ignoreCase = true)) score += 10
        if (isNodeNearBottom(bounds)) score += 18
        if (isNodeNearRight(bounds)) score += 20
        score += scoreSendButtonProximity(bounds, inputNode)
        if (!canClickDirectly) score -= 80
        if (node.childCount > 4) score -= 12
        score -= depth.coerceAtMost(10)

        return score
    }

    private fun matchesSendLabel(text: String, description: String, viewId: String, activePackage: String): Boolean {
        val profile = getChatAppProfile(activePackage)
        val candidates = listOf(text, description, viewId)
        return candidates.any { candidate ->
            SEND_LABELS.any { label ->
                candidate.contains(label, ignoreCase = true)
            } || profile.sendTextKeywords.any { label ->
                candidate.contains(label, ignoreCase = true)
            }
        }
    }


    private fun prepareInputNode(node: AccessibilityNodeInfo) {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        performClick(node)
    }

    private fun setInputText(node: AccessibilityNodeInfo, reply: String): Boolean {
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                reply
            )
        }

        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) {
            SystemClock.sleep(60)
            if (verifyInputText(node, reply, allowUnknownState = true)) {
                return true
            }
        }

        if (pasteFromClipboard(node, reply)) {
            SystemClock.sleep(60)
            return verifyInputText(node, reply, allowUnknownState = true)
        }

        return false
    }

    private fun verifyInputText(node: AccessibilityNodeInfo, reply: String, allowUnknownState: Boolean): Boolean {
        runCatching { node.refresh() }
        val currentText = node.text?.toString().orEmpty()
        if (currentText.contains(reply)) {
            return true
        }
        return allowUnknownState && currentText.isBlank()
    }

    private fun pasteFromClipboard(node: AccessibilityNodeInfo, reply: String): Boolean {
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText("suggested_reply", reply))
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        var currentNode: AccessibilityNodeInfo? = node
        while (currentNode != null) {
            if (currentNode.isClickable && currentNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            currentNode = currentNode.parent
        }
        return false
    }

    private fun containsAnyKeyword(value: String, keywords: List<String>): Boolean {
        return keywords.any { keyword -> value.contains(keyword, ignoreCase = true) }
    }

    private fun matchesNodeKeywords(node: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        return buildList {
            add(node.viewIdResourceName.orEmpty())
            add(node.text?.toString().orEmpty())
            add(node.contentDescription?.toString().orEmpty())
            add(getNodeHint(node))
        }.any { value ->
            containsAnyKeyword(value, keywords)
        }
    }

    private fun getNodeHint(node: AccessibilityNodeInfo): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.toString().orEmpty()
        } else {
            ""
        }
    }

    private fun getNodeBounds(node: AccessibilityNodeInfo): Rect {
        return Rect().also { node.getBoundsInScreen(it) }
    }

    private fun isNodeNearBottom(bounds: Rect): Boolean {
        val screenHeight = resources.displayMetrics.heightPixels.coerceAtLeast(1)
        return bounds.centerY() >= (screenHeight * 0.56f).toInt()
    }

    private fun isNodeNearRight(bounds: Rect): Boolean {
        val screenWidth = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        return bounds.centerX() >= (screenWidth * 0.58f).toInt()
    }

    private fun scoreSendButtonProximity(bounds: Rect, inputNode: AccessibilityNodeInfo?): Int {
        inputNode ?: return 0
        val inputBounds = getNodeBounds(inputNode)
        val horizontalGap = bounds.left - inputBounds.right
        val verticalDelta = kotlin.math.abs(bounds.centerY() - inputBounds.centerY())
        val isRightSideNeighbor = horizontalGap in -dp(16)..dp(220) && verticalDelta <= dp(120)
        val isBottomNeighbor = bounds.top >= inputBounds.bottom - dp(24) &&
            kotlin.math.abs(bounds.centerX() - inputBounds.centerX()) <= dp(180)

        var score = 0
        if (isRightSideNeighbor) score += 72
        if (isBottomNeighbor) score += 44
        return score
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun describeNode(node: AccessibilityNodeInfo): String {
        val hint = getNodeHint(node)
        return "class=${node.className}, id=${node.viewIdResourceName}, text=${node.text}, desc=${node.contentDescription}, hint=$hint, editable=${node.isEditable}, focusable=${node.isFocusable}, clickable=${node.isClickable}"
    }


    private fun logWindowHierarchy(root: AccessibilityNodeInfo) {
        val builder = StringBuilder()
        appendNodeSummary(root, depth = 0, builder = builder, maxDepth = 4, maxNodes = 80, visited = intArrayOf(0))
        Log.w(TAG, "Accessibility tree snapshot:\n$builder")
    }

    private fun logAllRelevantWindows(targetPackage: String) {
        val matchingRoots = windows
            .mapNotNull { it.root }
            .filter { it.packageName?.toString() == targetPackage }

        if (matchingRoots.isEmpty()) {
            rootInActiveWindow?.let(::logWindowHierarchy)
            return
        }

        matchingRoots.forEachIndexed { index, root ->
            Log.w(TAG, "Logging window hierarchy for package=$targetPackage windowIndex=$index")
            logWindowHierarchy(root)
        }
    }


    private fun appendNodeSummary(
        node: AccessibilityNodeInfo?,
        depth: Int,
        builder: StringBuilder,
        maxDepth: Int,
        maxNodes: Int,
        visited: IntArray
    ) {
        if (node == null || depth > maxDepth || visited[0] >= maxNodes) return

        visited[0] += 1
        builder.append("  ".repeat(depth))
            .append("- ")
            .append(describeNode(node))
            .append('\n')

        for (index in 0 until node.childCount) {
            appendNodeSummary(node.getChild(index), depth + 1, builder, maxDepth, maxNodes, visited)
        }
    }

    private fun getChatAppProfile(packageName: String): ChatAppProfile {
        return CHAT_APP_PROFILES[packageName] ?: DEFAULT_CHAT_APP_PROFILE
    }

    companion object {

        @Volatile
        private var instance: ChatAutomationAccessibilityService? = null

        private const val TAG = "ChatAutomationA11y"
        private val SEND_LABELS = listOf("发送", "發送", "send", "提交", "发出")
        private val INPUT_VIEW_ID_KEYWORDS = listOf(
            "input", "edit", "reply", "composer", "message", "chat", "entry", "textbox", "textarea",
            "consult", "draft", "content", "talk"
        )
        private val INPUT_HINT_KEYWORDS = listOf(
            "输入", "回复", "消息", "请输入", "message", "reply", "type", "咨询", "联系房东", "说点什么", "沟通"
        )
        private val SEND_VIEW_ID_KEYWORDS = listOf("send", "submit", "reply", "publish", "confirm", "commit", "plane", "arrow")
        private val NON_INPUT_CLASS_KEYWORDS = listOf("Button", "Image", "RecyclerView", "ListView", "ScrollView", "WebView")
        private val DEFAULT_CHAT_APP_PROFILE = ChatAppProfile(
            displayName = "聊天应用",
            inputIdKeywords = INPUT_VIEW_ID_KEYWORDS,
            inputTextKeywords = INPUT_HINT_KEYWORDS,
            composerKeywords = INPUT_HINT_KEYWORDS,
            sendIdKeywords = SEND_VIEW_ID_KEYWORDS,
            sendTextKeywords = SEND_LABELS,
            customInputViewBonus = 18
        )
        private val CHAT_APP_PROFILES = mapOf(
            PreferencesManager.BAIJUYI_PACKAGE to ChatAppProfile(
                displayName = "百居易",
                inputIdKeywords = listOf("message", "input", "reply", "composer", "entry", "editor", "content", "chat", "edit"),
                inputTextKeywords = listOf("输入", "回复", "消息", "请输入", "咨询", "联系房东", "message", "reply"),
                composerKeywords = listOf("输入", "回复", "消息", "请输入", "咨询", "联系房东", "message", "reply"),
                sendIdKeywords = listOf("send", "submit", "reply", "publish", "commit", "confirm", "finish"),
                sendTextKeywords = listOf("发送", "send", "提交", "发出", "确认"),
                customInputViewBonus = 40
            ),
            PreferencesManager.MEITUAN_MINSU_PACKAGE to ChatAppProfile(
                displayName = "美团民宿",
                inputIdKeywords = listOf("message", "input", "reply", "edit", "composer", "consult", "chat", "talk", "draft"),
                inputTextKeywords = listOf("输入", "回复", "消息", "请输入", "联系房东", "和房东沟通", "咨询", "说点什么", "message", "reply"),
                composerKeywords = listOf("输入", "回复", "咨询", "联系房东", "和房东沟通", "说点什么", "message", "reply"),
                sendIdKeywords = listOf("send", "submit", "reply", "consult", "publish", "arrow", "plane", "paper"),
                sendTextKeywords = listOf("发送", "send", "提交", "回复", "发出"),
                customInputViewBonus = 22
            ),
            PreferencesManager.TUJIA_MINSU_PACKAGE to ChatAppProfile(
                displayName = "途家民宿",
                inputIdKeywords = listOf("message", "input", "reply", "edit", "composer", "chat", "consult", "talk", "content"),
                inputTextKeywords = listOf("输入", "回复", "消息", "请输入", "联系房东", "咨询", "说点什么", "message", "reply"),
                composerKeywords = listOf("输入", "回复", "咨询", "联系房东", "说点什么", "message", "reply"),
                sendIdKeywords = listOf("send", "submit", "reply", "publish", "confirm", "plane", "arrow"),
                sendTextKeywords = listOf("发送", "send", "提交", "回复", "发出"),
                customInputViewBonus = 22
            )
        )
        private val SUPPORTED_CHAT_PACKAGES = listOf(
            PreferencesManager.WECHAT_PACKAGE,
            PreferencesManager.BAIJUYI_PACKAGE,
            PreferencesManager.MEITUAN_MINSU_PACKAGE,
            PreferencesManager.TUJIA_MINSU_PACKAGE
        )


        fun isEnabled(context: Context): Boolean {
            // 优先通过 instance 引用判断（服务已连接且运行中）
            Log.d(TAG, "isEnabled: instance=$instance")
            if (instance != null) {
                Log.d(TAG, "isEnabled: instance is not null, returning true")
                return true
            }

            // 回退：通过系统设置检查服务是否在已启用列表中
            val enabledServicesSetting = try {
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            } catch (e: Exception) {
                Log.e(TAG, "isEnabled: error reading settings", e)
                null
            }

            Log.d(TAG, "isEnabled: enabledServicesSetting=$enabledServicesSetting")

            if (enabledServicesSetting.isNullOrBlank()) return false

            val expectedComponent = ComponentName(context, ChatAutomationAccessibilityService::class.java)
            val colonSplitter = enabledServicesSetting.split(':')
            val result = colonSplitter.any { componentStr ->
                val component = ComponentName.unflattenFromString(componentStr.trim())
                Log.d(TAG, "isEnabled: comparing $component with $expectedComponent, match=${component == expectedComponent}")
                component == expectedComponent
            }
            Log.d(TAG, "isEnabled: result=$result")
            return result
        }

        fun sendReply(reply: String, targetPackage: String?): SendReplyStatus {
            Log.d(TAG, "sendReply: instance=$instance, replyLength=${reply.length}, targetPackage=$targetPackage")
            val service = instance ?: run {
                Log.e(TAG, "sendReply: instance is null, service not connected")
                return SendReplyStatus.SERVICE_UNAVAILABLE
            }
            return service.sendReplyInternal(reply, targetPackage)
        }
    }

    private data class ScoredNode(
        val node: AccessibilityNodeInfo,
        val score: Int
    )

    private data class ChatAppProfile(
        val displayName: String,
        val inputIdKeywords: List<String>,
        val inputTextKeywords: List<String>,
        val composerKeywords: List<String>,
        val sendIdKeywords: List<String>,
        val sendTextKeywords: List<String>,
        val customInputViewBonus: Int
    )

    enum class SendReplyStatus {

        SUCCESS,
        SERVICE_UNAVAILABLE,
        NO_ACTIVE_WINDOW,
        WRONG_WINDOW,
        INPUT_NOT_FOUND,
        INPUT_FAILED,
        SEND_BUTTON_NOT_FOUND,
        CLICK_FAILED
    }
}

