package com.csbaby.kefu.infrastructure.window

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.net.Uri
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

import androidx.core.app.NotificationCompat
import com.csbaby.kefu.R
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.domain.model.ReplySource

import com.csbaby.kefu.AppEntryPoint
import com.csbaby.kefu.infrastructure.accessibility.ChatAutomationAccessibilityService
import com.csbaby.kefu.infrastructure.reply.ReplyOrchestrator
import com.csbaby.kefu.presentation.MainActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FloatingWindowService : Service() {

    // 手动获取依赖（避免 Service 被系统回收重建时 @AndroidEntryPoint 注入失败）
    private val replyOrchestrator: ReplyOrchestrator by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            AppEntryPoint::class.java
        ).replyOrchestrator()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var currentDisplayData: DisplayData? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null
    private var isExpanded = false

    private var collapsedBubbleView: FrameLayout? = null
    private var collapsedBadgeView: TextView? = null
    private var collapsedDotView: View? = null
    private var expandedPanelView: LinearLayout? = null
    private var sourceChipTextView: TextView? = null
    private var confidenceChipTextView: TextView? = null
    private var metaTextView: TextView? = null
    private var originalMessageTextView: TextView? = null
    private var replyLabelTextView: TextView? = null
    private var suggestedReplyEditText: EditText? = null
    
    // 新增知识库相关变量
    private var tabContainer: LinearLayout? = null
    private var suggestionTab: TextView? = null
    private var knowledgeTab: TextView? = null
    private var knowledgeSearchEditText: EditText? = null
    private var knowledgeResultsRecyclerContainer: LinearLayout? = null
    private var suggestionPanel: LinearLayout? = null
    private var knowledgePanel: LinearLayout? = null
    private var currentTab: String = "suggestion" // "suggestion" 或 "knowledge"


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        Log.d(TAG, "FWS.onCreate() OK")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FWS.onStartCommand: action=${intent?.action}, extras=${intent?.extras?.keySet() ?: "null"}")
        try {
            when (intent?.action) {
                ACTION_SHOW -> {
                    Log.d(TAG, "FWS.onStartCommand: ACTION_SHOW")
                    val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_DISPLAY_DATA, DisplayData::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(EXTRA_DISPLAY_DATA)
                    }
                    showFloatingWindow(data)
                }
                ACTION_SHOW_ICON_ONLY -> {
                    Log.d(TAG, "FWS.onStartCommand: ACTION_SHOW_ICON_ONLY")
                    showFloatingIconOnly()
                }
                ACTION_HIDE -> {
                    Log.d(TAG, "FWS.onStartCommand: ACTION_HIDE")
                    hideFloatingWindow()
                }
                ACTION_UPDATE -> {
                    Log.d(TAG, "FWS.onStartCommand: ACTION_UPDATE")
                    updateFloatingWindow(intent)
                }
                else -> {
                    Log.w(TAG, "FWS.onStartCommand: unknown action=${intent?.action}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "FWS onStartCommand crashed", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun showFloatingWindow(data: DisplayData?) {
        Log.d(TAG, "FWS.showFloatingWindow called, overlay=${Settings.canDrawOverlays(this)}, data=${data != null}")

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "FWS: Overlay permission missing, skip showing floating window")
            Toast.makeText(
                this,
                "⚠️ 悬浮窗权限未开启，请返回客服小秘APP顶部横幅引导开启",
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
            return
        }

        if (data == null) {
            Log.e(TAG, "FWS: Display data is null, skip showing floating window. This may indicate a problem with data passing between components.")
            return
        }

        if (data.targetPackage == PreferencesManager.BAIJUYI_PACKAGE) {
            Log.d(
                TAG,
                "FWS Baijuyi floating window show request. conversation=${previewForLog(data.conversationTitle)}, source=${data.source}, ruleId=${data.ruleId}, modelId=${data.modelId}, reply=${previewForLog(data.suggestedReply)}"
            )
        }

        try {
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            Log.e(TAG, "FWS.showFloatingWindow: startForeground failed", e)
            stopSelf()
            return
        }


        if (floatingView == null) {
            currentDisplayData = data
            isExpanded = false
            windowLayoutParams = createLayoutParams()
            floatingView = createFloatingView()
            updateFloatingContent(data)
            applyExpandedState(animated = false)

            try {
                windowManager?.addView(floatingView, windowLayoutParams)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add floating view", e)
            }
        } else {
            val wasExpanded = isExpanded
            currentDisplayData = data
            updateFloatingContent(data)
            if (!wasExpanded) {
                pulseCollapsedBubble()
            }
        }
    }

    /**
     * 显示仅图标模式（无消息内容，用于默认显示悬浮图标）
     */
    private fun showFloatingIconOnly() {
        Log.d(TAG, "FWS.showFloatingIconOnly called, overlay=${Settings.canDrawOverlays(this)}, floatingView=${floatingView != null}")

        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "FWS: Overlay permission missing! User needs to grant it in Settings.")
            Toast.makeText(
                this,
                "⚠️ 需要悬浮窗权限才能显示图标\n点击这里去授权",
                Toast.LENGTH_LONG
            ).show()
            // 引导用户去授权
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "FWS: Failed to open overlay permission settings", e)
            }
            stopSelf()
            return
        }

        Log.d(TAG, "FWS.showFloatingIconOnly: overlay permission OK, proceeding")

        try {
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "FWS.showFloatingIconOnly: startForeground succeeded")
        } catch (e: Exception) {
            Log.e(TAG, "FWS.showFloatingIconOnly: startForeground failed", e)
            Toast.makeText(this, "启动前台服务失败: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        // 如果悬浮窗已存在，不需要重复创建
        if (floatingView != null) {
            Log.d(TAG, "FWS.showFloatingIconOnly: view already exists, skip")
            return
        }

        // 创建一个空的 DisplayData 用于显示图标
        val iconData = DisplayData(
            originalMessage = "",
            suggestedReply = "",
            source = ReplySource.AI_GENERATED.name,
            confidence = 0f,
            targetPackage = "",
            conversationTitle = "",
            ruleId = -1L,
            modelId = -1L
        )

        if (floatingView == null) {
            Log.d(TAG, "FWS.showFloatingIconOnly: creating new floating view")
            currentDisplayData = iconData
            isExpanded = false

            val params = createLayoutParams()
            windowLayoutParams = params
            Log.d(TAG, "FWS: layout params created: type=${params.type}, flags=${params.flags}, x=${params.x}, y=${params.y}, w=${params.width}, h=${params.height}")

            floatingView = createFloatingView()
            Log.d(TAG, "FWS: floating view created, about to add to windowManager")

            // 更新为图标模式的内容
            updateFloatingContentForIconOnly()
            applyExpandedState(animated = false)

            try {
                Log.d(TAG, "FWS: calling windowManager.addView, view=${floatingView}, params=${params}")
                windowManager?.addView(floatingView, params)
                Log.d(TAG, "FWS: Floating icon shown successfully!")
            } catch (e: Exception) {
                Log.e(TAG, "FWS: Failed to add floating icon view!", e)
                Toast.makeText(this, "添加悬浮窗失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Log.d(TAG, "FWS.showFloatingIconOnly: view already exists, updating content")
            // 已经显示，更新为图标模式
            currentDisplayData = iconData
            updateFloatingContentForIconOnly()
            // 如果当前是收起状态，脉冲提示一下
            if (!isExpanded) {
                pulseCollapsedBubble()
            }
        }
    }

    /**
     * 更新悬浮窗内容为仅图标模式（无消息气泡提示）
     */
    private fun updateFloatingContentForIconOnly() {
        // 清空徽章文字
        collapsedBadgeView?.text = ""
        // 隐藏绿点
        collapsedDotView?.alpha = 0f
        // 更新气泡背景为默认色
        collapsedBubbleView?.background = createBubbleBackground(ReplySource.AI_GENERATED.name)
        collapsedBubbleView?.contentDescription = "客服小秘，点击展开"

        // 清空展开面板的内容
        sourceChipTextView?.text = ""
        confidenceChipTextView?.text = ""
        metaTextView?.text = "点击展开查看建议回复"
        replyLabelTextView?.text = ""
        originalMessageTextView?.text = ""
        suggestedReplyEditText?.setText("")
    }

    private fun hideFloatingWindow() {
        removeFloatingView()
        currentDisplayData = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateFloatingWindow(intent: Intent) {
        val message = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_MESSAGE, DisplayData::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_MESSAGE)
        }

        if (message == null) return

        currentDisplayData = message
        if (floatingView == null) {
            showFloatingWindow(message)
        } else {
            updateFloatingContent(message)
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val screenWidth = resources.displayMetrics.widthPixels
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            computeWindowFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {

            gravity = Gravity.TOP or Gravity.START
            x = max(screenWidth - dp(96), dp(12))
            y = dp(88)
        }
    }

    private fun createFloatingView(): View {
        val root = FrameLayout(this).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnTouchListener { _, event ->
                if (isExpanded && event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    setExpanded(false, animated = true)
                    true
                } else {
                    false
                }
            }
        }


        val bubble = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(68), dp(68))
            elevation = dp(18).toFloat()
            background = createBubbleBackground(ReplySource.AI_GENERATED.name)
        }
        collapsedBubbleView = bubble

        val bubbleGlow = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(68), dp(68))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2B38BDF8"))
            }
            alpha = 0.32f
        }
        bubble.addView(bubbleGlow)

        val bubbleIcon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER)
            setImageResource(R.drawable.ic_kefu_orb)
            setColorFilter(Color.WHITE)
        }
        bubble.addView(bubbleIcon)

        val bubbleBadge = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                bottomMargin = dp(8)
            }
            minWidth = dp(30)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(2), dp(6), dp(2))
            textSize = 9f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            text = "AI"
            background = createChipBackground(
                fillColor = "#33FFFFFF",
                strokeColor = "#66FFFFFF",
                radiusDp = 10
            )
        }
        collapsedBadgeView = bubbleBadge
        bubble.addView(bubbleBadge)

        val bubbleDot = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(10), dp(10), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(8)
                marginEnd = dp(8)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#22C55E"))
                setStroke(dp(2), Color.parseColor("#CCECFDF5"))
            }
        }
        collapsedDotView = bubbleDot
        bubble.addView(bubbleDot)
        bubble.setOnTouchListener(createWindowTouchListener { setExpanded(true, animated = true) })

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(calculateExpandedPanelWidth(), FrameLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            elevation = dp(20).toFloat()
            background = createPanelBackground()
        }
        expandedPanelView = panel

        val dragHandle = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(5)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(99).toFloat()
                setColor(Color.parseColor("#55E2E8F0"))
            }
        }
        dragHandle.setOnTouchListener(createWindowTouchListener())

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val brandIconWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            background = createBrandIconBackground()
        }
        val brandIcon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
            setImageResource(R.drawable.ic_kefu_orb)
            setColorFilter(Color.WHITE)
        }
        brandIconWrap.addView(brandIcon)

        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(12)
            }
        }
        val titleView = TextView(this).apply {
            text = "客服小秘"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        val subtitleView = TextView(this).apply {
            text = "新消息来了，点一下就能继续处理"
            textSize = 11.5f
            setTextColor(Color.parseColor("#A5B4FC"))
        }
        titleColumn.addView(titleView)
        titleColumn.addView(subtitleView)

        brandRow.addView(brandIconWrap)
        brandRow.addView(titleColumn)

        val headerActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        val collapseButton = createHeaderIconButton(
            iconRes = R.drawable.ic_panel_collapse,
            sizeDp = 44,
            iconSizeDp = 22,
            fillColor = "#1F334155",
            strokeColor = "#33475569"
        ) {
            setExpanded(false, animated = true)
        }
        val closeButton = createHeaderIconButton(
            iconRes = R.drawable.ic_panel_close,
            sizeDp = 38,
            iconSizeDp = 16,
            fillColor = "#261E293B",
            strokeColor = "#335B6475"
        ) {
            hideFloatingWindow()
        }
        headerActions.addView(collapseButton)
        headerActions.addView(spaceView(10))
        headerActions.addView(closeButton)


        headerRow.addView(brandRow)
        headerRow.addView(headerActions)

        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val sourceChip = createInfoChip("AI 灵感")
        sourceChipTextView = sourceChip
        val confidenceChip = createInfoChip("100% 把握")
        confidenceChipTextView = confidenceChip
        chipRow.addView(sourceChip)
        chipRow.addView(spaceView(8))
        chipRow.addView(confidenceChip)

        metaTextView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        val originalLabel = createSectionLabel("客户消息", "#FDBA74")
        originalMessageTextView = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#E5E7EB"))
            setLineSpacing(0f, 1.16f)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            maxLines = 6
            ellipsize = TextUtils.TruncateAt.END
            background = createMessageCardBackground()
        }

        // 标签页切换
        val tabContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(8), dp(2), dp(4))
        }
        this.tabContainer = tabContainer
        
        suggestionTab = createTabButton("建议回复", isSelected = true) {
            switchToTab("suggestion")
        }
        knowledgeTab = createTabButton("知识库搜索", isSelected = false) {
            switchToTab("knowledge")
        }
        tabContainer.addView(suggestionTab)
        tabContainer.addView(spaceView(12))
        tabContainer.addView(knowledgeTab)
        
        // 建议回复面板
        val suggestionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE
        }
        this.suggestionPanel = suggestionPanel
        replyLabelTextView = createSectionLabel("AI 建议回复（可编辑）", "#67E8F9")
        suggestedReplyEditText = EditText(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
            setLineSpacing(0f, 1.18f)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            minLines = 4
            maxLines = 8
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            isSingleLine = false
            background = createReplyCardBackground(ReplySource.AI_GENERATED.name)
        }
        
        // 知识库搜索面板
        val knowledgePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        this.knowledgePanel = knowledgePanel
        
        val searchContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        knowledgeSearchEditText = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
            textSize = 13f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
            hint = "搜索关键词（如：退款、入住时间、房价）"
            setLineSpacing(0f, 1.1f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minLines = 1
            maxLines = 1
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            background = createMessageCardBackground()
        }
        
        val searchButton = createBottomActionButton(
            text = "搜索",
            background = createSolidButtonBackground("#1E293B", "#334155"),
            textColor = "#E2E8F0",
            weight = 0.5f
        ) {
            performKnowledgeSearch()
        }
        
        searchContainer.addView(knowledgeSearchEditText)
        searchContainer.addView(searchButton)
        
        knowledgeResultsRecyclerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(240))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = createMessageCardBackground()
        }
        
        knowledgePanel.addView(searchContainer)
        knowledgePanel.addView(verticalSpace(12))
        knowledgePanel.addView(knowledgeResultsRecyclerContainer)
        
        // 将两个面板添加到垂直布局中
        suggestionPanel.addView(replyLabelTextView)
        suggestionPanel.addView(verticalSpace(8))
        suggestionPanel.addView(suggestedReplyEditText)
        
        knowledgePanel.addView(searchContainer)
        knowledgePanel.addView(verticalSpace(12))
        knowledgePanel.addView(knowledgeResultsRecyclerContainer)
        
        val tabContentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        tabContentContainer.addView(tabContainer)
        tabContentContainer.addView(verticalSpace(8))
        tabContentContainer.addView(suggestionPanel)
        tabContentContainer.addView(knowledgePanel)


        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val sendButton = createBottomActionButton(
            text = "⚡ 直接发送",
            background = createGradientButtonBackground("#7C3AED", "#06B6D4", strokeColor = "#A855F7"),
            textColor = "#FFFFFF",
            weight = 1f
        ) {
            sendSuggestedReply()
        }
        val copyButton = createBottomActionButton(
            text = "复制回复",
            background = createSolidButtonBackground("#1E293B", "#334155"),
            textColor = "#E2E8F0",
            weight = 0.92f
        ) {
            copySuggestedReply()
        }
        actionRow.addView(sendButton)
        actionRow.addView(spaceView(10))
        actionRow.addView(copyButton)

        val footNote = TextView(this).apply {
            text = "小圆球可拖动；收起后不打扰，点一下再展开。"
            textSize = 11f
            setTextColor(Color.parseColor("#64748B"))
        }

        panel.addView(dragHandle)
        panel.addView(verticalSpace(14))
        panel.addView(headerRow)
        panel.addView(verticalSpace(12))
        panel.addView(chipRow)
        panel.addView(verticalSpace(10))
        panel.addView(metaTextView)
        panel.addView(verticalSpace(16))
        panel.addView(originalLabel)
        panel.addView(verticalSpace(8))
        panel.addView(originalMessageTextView)
        panel.addView(verticalSpace(14))
        
        // 使用新的标签页内容容器替换旧的回复布局
        panel.addView(tabContentContainer)
        panel.addView(verticalSpace(16))

        panel.addView(actionRow)
        panel.addView(verticalSpace(12))
        panel.addView(footNote)

        root.addView(bubble)
        root.addView(panel)
        return root
    }

    private fun updateFloatingContent(data: DisplayData) {
        val conversation = data.conversationTitle.ifBlank { data.targetPackage }
        val confidence = (data.confidence * 100).toInt().coerceIn(0, 100)
        val sourceLabel = getSourceLabel(data.source)

        sourceChipTextView?.text = getSourceChipText(data.source)
        confidenceChipTextView?.text = "$confidence% 把握"
        metaTextView?.text = "会话：$conversation · 来源：$sourceLabel"
        replyLabelTextView?.text = getSuggestionTitle(data.source) + "（可编辑）"
        originalMessageTextView?.text = data.originalMessage.ifBlank { "（暂无客户消息内容）" }
        suggestedReplyEditText?.background = createReplyCardBackground(data.source)
        suggestedReplyEditText?.setText(data.suggestedReply.ifBlank { "（暂无建议回复内容）" })
        suggestedReplyEditText?.setSelection(suggestedReplyEditText?.text?.length ?: 0)


        collapsedBadgeView?.text = if (data.source == ReplySource.RULE_MATCH.name) "规则" else "AI"
        collapsedDotView?.alpha = if (isExpanded) 0f else 1f
        collapsedBubbleView?.background = createBubbleBackground(data.source)
        collapsedBubbleView?.contentDescription = "$sourceLabel，点击展开建议回复"
    }

    private fun sendSuggestedReply() {
        val data = currentDisplayData ?: return
        val reply = getCurrentReplyText()
        if (reply.isBlank()) {
            Toast.makeText(this, "当前没有可发送的回复内容", Toast.LENGTH_SHORT).show()
            return
        }

        if (!ChatAutomationAccessibilityService.isEnabled(this)) {
            copyReplyToClipboard(reply)
            Toast.makeText(this, "请先开启无障碍自动发送权限，回复内容已复制", Toast.LENGTH_LONG).show()
            openAccessibilitySettings()
            return
        }

        val sendStatus = temporarilyYieldOverlayFocus {
            ChatAutomationAccessibilityService.sendReply(reply, data.targetPackage)
        }

        when (sendStatus) {
            ChatAutomationAccessibilityService.SendReplyStatus.SUCCESS -> {
                recordSentReply(data, reply)
                Toast.makeText(this, "已自动发送回复", Toast.LENGTH_SHORT).show()
                hideFloatingWindow()
            }
            ChatAutomationAccessibilityService.SendReplyStatus.WRONG_WINDOW -> {
                Toast.makeText(this, "请先停留在对应聊天窗口后再发送", Toast.LENGTH_LONG).show()
            }
            ChatAutomationAccessibilityService.SendReplyStatus.INPUT_NOT_FOUND -> {
                copyReplyToClipboard(reply)
                Toast.makeText(this, "仍未找到聊天输入框，请先停留在目标聊天页并点一下输入区域后重试，回复已先复制", Toast.LENGTH_LONG).show()
            }

            ChatAutomationAccessibilityService.SendReplyStatus.SEND_BUTTON_NOT_FOUND -> {
                copyReplyToClipboard(reply)
                Toast.makeText(this, "已填入内容但未找到发送按钮，请手动检查当前聊天界面", Toast.LENGTH_LONG).show()
            }

            ChatAutomationAccessibilityService.SendReplyStatus.INPUT_FAILED,
            ChatAutomationAccessibilityService.SendReplyStatus.CLICK_FAILED,
            ChatAutomationAccessibilityService.SendReplyStatus.NO_ACTIVE_WINDOW,
            ChatAutomationAccessibilityService.SendReplyStatus.SERVICE_UNAVAILABLE -> {
                copyReplyToClipboard(reply)
                Toast.makeText(this, "自动发送失败，回复内容已复制，可手动粘贴发送", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun copySuggestedReply() {
        val reply = getCurrentReplyText()
        if (reply.isBlank()) return

        copyReplyToClipboard(reply)
        Toast.makeText(this, "已复制回复内容", Toast.LENGTH_SHORT).show()
        setExpanded(false, animated = true)
    }


    private fun copyReplyToClipboard(reply: String) {
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText("suggested_reply", reply))
    }

    private fun recordSentReply(data: DisplayData, finalReply: String) {
        val source = runCatching { ReplySource.valueOf(data.source) }.getOrDefault(ReplySource.AI_GENERATED)
        serviceScope.launch {
            replyOrchestrator.recordFinalReply(
                originalMessage = data.originalMessage,
                generatedReply = data.suggestedReply,
                finalReply = finalReply,
                appPackage = data.targetPackage,
                source = source,
                confidence = data.confidence,
                ruleId = data.ruleId.takeIf { it > 0 },
                modelId = data.modelId.takeIf { it > 0 }
            )
        }
    }

    private fun getCurrentReplyText(): String {
        return suggestedReplyEditText?.text?.toString()?.trim()
            ?: currentDisplayData?.suggestedReply.orEmpty().trim()
    }

    private fun temporarilyYieldOverlayFocus(block: () -> ChatAutomationAccessibilityService.SendReplyStatus): ChatAutomationAccessibilityService.SendReplyStatus {
        val params = windowLayoutParams ?: return block()
        val previousFlags = params.flags
        suggestedReplyEditText?.clearFocus()
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        updateWindowLayout()
        SystemClock.sleep(120)

        return try {
            block()
        } finally {
            params.flags = previousFlags
            updateWindowLayout()
        }
    }

    private fun setExpanded(expanded: Boolean, animated: Boolean) {
        isExpanded = expanded
        updateWindowFlags()
        applyExpandedState(animated)
        collapsedDotView?.animate()?.alpha(if (expanded) 0f else 1f)?.setDuration(160)?.start()
    }


    private fun applyExpandedState(animated: Boolean) {
        val bubble = collapsedBubbleView ?: return
        val panel = expandedPanelView ?: return

        if (isExpanded) {
            panel.visibility = View.VISIBLE
            if (animated) {
                panel.alpha = 0f
                panel.scaleX = 0.95f
                panel.scaleY = 0.95f
                panel.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180)
                    .start()
                bubble.animate()
                    .alpha(0f)
                    .scaleX(0.82f)
                    .scaleY(0.82f)
                    .setDuration(140)
                    .withEndAction {
                        bubble.visibility = View.GONE
                        bubble.alpha = 1f
                        bubble.scaleX = 1f
                        bubble.scaleY = 1f
                        updateWindowLayout()
                    }
                    .start()
            } else {
                bubble.visibility = View.GONE
                panel.alpha = 1f
                panel.scaleX = 1f
                panel.scaleY = 1f
                updateWindowLayout()
            }
        } else {
            bubble.visibility = View.VISIBLE
            if (animated) {
                bubble.alpha = 0f
                bubble.scaleX = 0.84f
                bubble.scaleY = 0.84f
                bubble.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(180)
                    .start()
                panel.animate()
                    .alpha(0f)
                    .scaleX(0.94f)
                    .scaleY(0.94f)
                    .setDuration(140)
                    .withEndAction {
                        panel.visibility = View.GONE
                        panel.alpha = 1f
                        panel.scaleX = 1f
                        panel.scaleY = 1f
                        updateWindowLayout()
                    }
                    .start()
            } else {
                panel.visibility = View.GONE
                bubble.alpha = 1f
                bubble.scaleX = 1f
                bubble.scaleY = 1f
                updateWindowLayout()
            }
        }
    }

    private fun pulseCollapsedBubble() {
        collapsedBubbleView?.animate()?.cancel()
        collapsedBubbleView?.animate()
            ?.scaleX(1.08f)
            ?.scaleY(1.08f)
            ?.setDuration(150)
            ?.withEndAction {
                collapsedBubbleView?.animate()
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.setDuration(170)
                    ?.start()
            }
            ?.start()
    }

    private fun createWindowTouchListener(onClick: (() -> Unit)? = null): View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        return View.OnTouchListener { _, event ->
            val params = windowLayoutParams ?: return@OnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (!moved && (abs(dx) > dp(4) || abs(dy) > dp(4))) {
                        moved = true
                    }
                    params.x = clampWindowX(initialX + dx)
                    params.y = max(initialY + dy, dp(24))
                    updateWindowLayout()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        onClick?.invoke()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun clampWindowX(candidate: Int): Int {
        val maxX = max(resources.displayMetrics.widthPixels - getVisibleWindowWidth() - dp(8), dp(8))
        return candidate.coerceIn(dp(8), maxX)
    }

    private fun getVisibleWindowWidth(): Int {
        return if (isExpanded) {
            calculateExpandedPanelWidth() + dp(16)
        } else {
            dp(84)
        }
    }

    private fun calculateExpandedPanelWidth(): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        return min((screenWidth * 0.88f).toInt(), dp(380))
    }

    private fun computeWindowFlags(): Int {
        return if (isExpanded) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
    }

    private fun updateWindowFlags() {
        val params = windowLayoutParams ?: return
        params.flags = computeWindowFlags()
        updateWindowLayout()
    }

    private fun updateWindowLayout() {

        try {
            floatingView?.requestLayout()
            floatingView?.let { view ->
                windowLayoutParams?.let { params ->
                    windowManager?.updateViewLayout(view, params)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update floating layout", e)
        }
    }

    private fun getSourceLabel(source: String): String {
        return when (source) {
            ReplySource.AI_GENERATED.name -> "AI 灵感"
            ReplySource.RULE_MATCH.name -> "规则直达"
            else -> "智能建议"
        }
    }

    private fun getSourceChipText(source: String): String {
        return when (source) {
            ReplySource.AI_GENERATED.name -> "🤖 AI 灵感"
            ReplySource.RULE_MATCH.name -> "📚 规则直达"
            else -> "💬 智能建议"
        }
    }

    private fun getSuggestionTitle(source: String): String {
        return when (source) {
            ReplySource.AI_GENERATED.name -> "AI 建议回复"
            ReplySource.RULE_MATCH.name -> "规则建议回复"
            else -> "建议回复"
        }
    }

    private fun createPanelBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#FF0F172A"),
                Color.parseColor("#FF162033"),
                Color.parseColor("#FF1E293B")
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(26).toFloat()
            setStroke(dp(1), Color.parseColor("#3367E8F9"))
        }
    }

    private fun createBubbleBackground(source: String): GradientDrawable {
        val colors = when (source) {
            ReplySource.RULE_MATCH.name -> intArrayOf(
                Color.parseColor("#FF2563EB"),
                Color.parseColor("#FF06B6D4")
            )

            else -> intArrayOf(
                Color.parseColor("#FF7C3AED"),
                Color.parseColor("#FF06B6D4")
            )
        }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(1), Color.parseColor("#80E0F2FE"))
        }
    }

    private fun createBrandIconBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#FF8B5CF6"),
                Color.parseColor("#FF0EA5E9")
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
        }
    }

    private fun createMessageCardBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#262A1F10"),
                Color.parseColor("#40361E10")
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.parseColor("#33FDBA74"))
        }
    }

    private fun createReplyCardBackground(source: String): GradientDrawable {
        val (fillStart, fillEnd, strokeColor) = when (source) {
            ReplySource.RULE_MATCH.name -> Triple("#1A0EA5E9", "#262563EB", "#4D60A5FA")
            else -> Triple("#1A10B981", "#2622C55E", "#4D34D399")
        }
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor(fillStart), Color.parseColor(fillEnd))
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.parseColor(strokeColor))
        }
    }

    private fun createChipBackground(fillColor: String, strokeColor: String, radiusDp: Int = 14): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(fillColor))
            setStroke(dp(1), Color.parseColor(strokeColor))
        }
    }

    private fun createGradientButtonBackground(startColor: String, endColor: String, strokeColor: String): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor(startColor), Color.parseColor(endColor))
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.parseColor(strokeColor))
        }
    }

    private fun createSolidButtonBackground(fillColor: String, strokeColor: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(Color.parseColor(fillColor))
            setStroke(dp(1), Color.parseColor(strokeColor))
        }
    }

    private fun createHeaderIconButton(
        iconRes: Int,
        sizeDp: Int,
        iconSizeDp: Int,
        fillColor: String,
        strokeColor: String,
        onClick: () -> Unit
    ): FrameLayout {
        return FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
            background = createChipBackground(
                fillColor = fillColor,
                strokeColor = strokeColor,
                radiusDp = sizeDp / 2
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            addView(ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp), Gravity.CENTER)
                setImageResource(iconRes)
                setColorFilter(Color.parseColor("#E2E8F0"))
            })
        }
    }


    private fun createInfoChip(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#D8EAFE"))
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = createChipBackground(
                fillColor = "#1938BDF8",
                strokeColor = "#3340C4FF",
                radiusDp = 13
            )
        }
    }

    private fun createSectionLabel(text: String, color: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(color))
        }
    }

    private fun createTabButton(
        text: String,
        isSelected: Boolean,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { onClick() }
            updateTabAppearance(isSelected)
        }
    }

    private fun TextView.updateTabAppearance(isSelected: Boolean) {
        if (isSelected) {
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(99).toFloat()
                setColor(Color.parseColor("#374151"))
                setStroke(dp(1), Color.parseColor("#4B5563"))
            }
        } else {
            setTextColor(Color.parseColor("#94A3B8"))
            background = null
        }
    }

    private fun createBottomActionButton(
        text: String,
        background: GradientDrawable,
        textColor: String,
        weight: Float,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor(textColor))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            this.background = background
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            setOnClickListener { onClick() }
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun removeFloatingView() {
        try {
            floatingView?.let { view ->
                windowManager?.removeView(view)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove floating view", e)
        } finally {
            floatingView = null
            collapsedBubbleView = null
            collapsedBadgeView = null
            collapsedDotView = null
            expandedPanelView = null
            sourceChipTextView = null
            confidenceChipTextView = null
            metaTextView = null
            originalMessageTextView = null
            replyLabelTextView = null
            suggestedReplyEditText = null
            tabContainer = null
            suggestionTab = null
            knowledgeTab = null
            knowledgeSearchEditText = null
            knowledgeResultsRecyclerContainer = null
            suggestionPanel = null
            knowledgePanel = null
            windowLayoutParams = null

            isExpanded = false
            currentTab = "suggestion"
        }
    }

    private fun switchToTab(tabName: String) {
        if (currentTab == tabName) return
        
        currentTab = tabName
        
        if (tabName == "suggestion") {
            suggestionPanel?.visibility = View.VISIBLE
            knowledgePanel?.visibility = View.GONE
            suggestionTab?.updateTabAppearance(true)
            knowledgeTab?.updateTabAppearance(false)
        } else {
            suggestionPanel?.visibility = View.GONE
            knowledgePanel?.visibility = View.VISIBLE
            suggestionTab?.updateTabAppearance(false)
            knowledgeTab?.updateTabAppearance(true)
            // 自动聚焦到搜索框
            knowledgeSearchEditText?.requestFocus()
        }
    }

    private fun performKnowledgeSearch() {
        val query = knowledgeSearchEditText?.text?.toString()?.trim() ?: return
        if (query.isBlank()) {
            Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "知识库搜索: $query")

        // 在主线程操作 UI
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            knowledgeResultsRecyclerContainer?.removeAllViews()
            val loadingView = TextView(this).apply {
                text = "正在搜索知识库..."
                textSize = 13f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(80)
                )
            }
            knowledgeResultsRecyclerContainer?.addView(loadingView)
        }

        serviceScope.launch {
            try {
                // replyOrchestrator 是 lazy 注入，首次访问时初始化
                val results = replyOrchestrator.searchKnowledgeRules(query)
                // 切回主线程更新 UI
                mainHandler.post {
                    knowledgeResultsRecyclerContainer?.removeAllViews()
                    if (results.isEmpty()) {
                        val emptyView = TextView(this@FloatingWindowService).apply {
                            text = "没有找到匹配的规则。试试其他关键词？"
                            textSize = 13f
                            setTextColor(Color.parseColor("#94A3B8"))
                            gravity = Gravity.CENTER
                            setPadding(0, dp(30), 0, 0)
                        }
                        knowledgeResultsRecyclerContainer?.addView(emptyView)
                        return@post
                    }
                    results.forEachIndexed { index, rule ->
                        val ruleView = createKnowledgeRuleView(rule, index)
                        knowledgeResultsRecyclerContainer?.addView(ruleView)
                        if (index < results.size - 1) {
                            knowledgeResultsRecyclerContainer?.addView(verticalSpace(12))
                        }
                    }
                    Toast.makeText(this@FloatingWindowService, "找到 ${results.size} 条匹配规则", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "知识库搜索失败", e)
                mainHandler.post {
                    knowledgeResultsRecyclerContainer?.removeAllViews()
                    val errorView = TextView(this@FloatingWindowService).apply {
                        text = "搜索失败: ${e.message ?: "未知错误"}"
                        textSize = 13f
                        setTextColor(Color.parseColor("#F87171"))
                        gravity = Gravity.CENTER
                    }
                    knowledgeResultsRecyclerContainer?.addView(errorView)
                }
            }
        }
    }

    private fun createKnowledgeRuleView(rule: KnowledgeRuleItem, index: Int): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createMessageCardBackground()
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            val headerRow = LinearLayout(this@FloatingWindowService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val keywordView = TextView(this@FloatingWindowService).apply {
                text = "关键词: ${rule.keyword}"
                textSize = 13f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val copyButton = createBottomActionButton(
                text = "复制",
                background = createSolidButtonBackground("#1E293B", "#334155"),
                textColor = "#E2E8F0",
                weight = 0.3f
            ) {
                copyReplyToClipboard(rule.replyTemplate)
                Toast.makeText(this@FloatingWindowService, "已复制回复内容", Toast.LENGTH_SHORT).show()
            }

            headerRow.addView(keywordView)
            headerRow.addView(copyButton)

            val replyView = TextView(this@FloatingWindowService).apply {
                text = rule.replyTemplate
                textSize = 12.5f
                setTextColor(Color.parseColor("#CBD5E1"))
                setLineSpacing(0f, 1.2f)
                setPadding(0, dp(8), 0, 0)
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
            }

            addView(headerRow)
            addView(replyView)
        }
    }

    // 定义知识库规则项的数据类
    data class KnowledgeRuleItem(
        val keyword: String,
        val replyTemplate: String,
        val matchType: String,
        val targetNames: List<String>
    )

    private fun verticalSpace(heightDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(heightDp)
            )
        }
    }

    private fun spaceView(widthDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(widthDp), 1)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_service_desc)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("客服小秘")
            .setContentText(if (isExpanded) "悬浮面板已展开，可直接发送建议回复" else "有新的回复建议，点小图标即可展开")
            .setSmallIcon(R.drawable.ic_kefu_orb)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        removeFloatingView()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    data class DisplayData(
        val originalMessage: String,
        val suggestedReply: String,
        val source: String,
        val confidence: Float,
        val targetPackage: String,
        val conversationTitle: String,
        val ruleId: Long = -1L,
        val modelId: Long = -1L
    ) : android.os.Parcelable {
        constructor(parcel: android.os.Parcel) : this(
            parcel.readString() ?: "",
            parcel.readString() ?: "",
            parcel.readString() ?: "",
            parcel.readFloat(),
            parcel.readString() ?: "",
            parcel.readString() ?: "",
            parcel.readLong(),
            parcel.readLong()
        )

        override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
            parcel.writeString(originalMessage)
            parcel.writeString(suggestedReply)
            parcel.writeString(source)
            parcel.writeFloat(confidence)
            parcel.writeString(targetPackage)
            parcel.writeString(conversationTitle)
            parcel.writeLong(ruleId)
            parcel.writeLong(modelId)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : android.os.Parcelable.Creator<DisplayData> {
            override fun createFromParcel(parcel: android.os.Parcel): DisplayData {
                return DisplayData(parcel)
            }

            override fun newArray(size: Int): Array<DisplayData?> {
                return arrayOfNulls(size)
            }
        }
    }

    private fun previewForLog(value: String?): String {
        val sanitized = value.orEmpty()
            .replace("\n", "\\n")
            .trim()
        return if (sanitized.length <= 120) sanitized else sanitized.take(117) + "..."
    }

    companion object {

        const val ACTION_SHOW = "com.csbaby.kefu.SHOW_WINDOW"
        const val ACTION_SHOW_ICON_ONLY = "com.csbaby.kefu.SHOW_ICON_ONLY"
        const val ACTION_HIDE = "com.csbaby.kefu.HIDE_WINDOW"
        const val ACTION_UPDATE = "com.csbaby.kefu.UPDATE_WINDOW"
        const val EXTRA_DISPLAY_DATA = "extra_display_data"
        const val EXTRA_MESSAGE = "extra_message"
        private const val CHANNEL_ID = "floating_window_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "FloatingWindowService"

        fun show(context: Context, displayData: DisplayData? = null) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_DISPLAY_DATA, displayData)
            }
            context.startForegroundService(intent)
        }

        /**
         * 显示悬浮图标（无消息内容，用于默认显示）
         */
        fun showIconOnly(context: Context) {
            Log.d(TAG, "FWS.showIconOnly() called")
            try {
                val intent = Intent(context, FloatingWindowService::class.java).apply {
                    action = ACTION_SHOW_ICON_ONLY
                }
                context.startForegroundService(intent)
                Log.d(TAG, "FWS.showIconOnly(): startForegroundService succeeded")
            } catch (e: Exception) {
                Log.e(TAG, "FWS.showIconOnly(): startForegroundService failed", e)
            }
        }

        fun hide(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }

        fun update(context: Context, displayData: DisplayData) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_MESSAGE, displayData)
            }
            context.startService(intent)
        }
    }
}
