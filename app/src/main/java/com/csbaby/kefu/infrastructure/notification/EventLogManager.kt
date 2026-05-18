package com.csbaby.kefu.infrastructure.notification

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory event log for tracking detection and exclusion events.
 * Provides a circular buffer of recent log entries for display in the UI.
 */
@Singleton
class EventLogManager @Inject constructor() {

    companion object {
        private const val TAG = "EventLogManager"
        private const val MAX_LOG_ENTRIES = 100
    }

    private val logEntries = mutableListOf<LogEntry>()
    private val lock = Any()

    /**
     * Log entry types
     */
    enum class LogType {
        MESSAGE_RECEIVED,      // 新消息被检测到
        MESSAGE_FILTERED,      // 消息被过滤（占位符/系统通知）
        MESSAGE_PROCESSING,     // 开始处理消息
        REPLY_GENERATED,        // 生成了回复建议
        FLOATING_WINDOW_SHOWN,  // 悬浮窗显示
        PERMISSION_DENIED,      // 权限不足
        ERROR                   // 错误
    }

    /**
     * Log entry data class
     */
    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val type: LogType,
        val appName: String,
        val message: String,
        val details: String? = null
    ) {
        fun getFormattedTime(): String {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

        fun getTypeIcon(): String {
            return when (type) {
                LogType.MESSAGE_RECEIVED -> "📥"
                LogType.MESSAGE_FILTERED -> "🚫"
                LogType.MESSAGE_PROCESSING -> "⚙️"
                LogType.REPLY_GENERATED -> "✅"
                LogType.FLOATING_WINDOW_SHOWN -> "🪟"
                LogType.PERMISSION_DENIED -> "🔒"
                LogType.ERROR -> "❌"
            }
        }

        fun getTypeLabel(): String {
            return when (type) {
                LogType.MESSAGE_RECEIVED -> "收到消息"
                LogType.MESSAGE_FILTERED -> "已过滤"
                LogType.MESSAGE_PROCESSING -> "处理中"
                LogType.REPLY_GENERATED -> "已生成回复"
                LogType.FLOATING_WINDOW_SHOWN -> "显示悬浮窗"
                LogType.PERMISSION_DENIED -> "权限不足"
                LogType.ERROR -> "错误"
            }
        }
    }

    /**
     * Log a new event
     */
    fun log(type: LogType, appName: String, message: String, details: String? = null) {
        synchronized(lock) {
            val entry = LogEntry(
                type = type,
                appName = appName,
                message = message,
                details = details
            )
            logEntries.add(entry)

            // Keep only the last MAX_LOG_ENTRIES
            if (logEntries.size > MAX_LOG_ENTRIES) {
                logEntries.removeAt(0)
            }

            // Also log to Logcat for debugging
            val logMessage = "${entry.getFormattedTime()} [${entry.getTypeLabel()}] $appName: $message${details?.let { " ($it)" } ?: ""}"
            Log.d(TAG, logMessage)
        }
    }

    /**
     * Get all log entries (newest first)
     */
    fun getAllLogs(): List<LogEntry> {
        synchronized(lock) {
            return logEntries.reversed().toList()
        }
    }

    /**
     * Get recent log entries (newest first)
     */
    fun getRecentLogs(count: Int = 50): List<LogEntry> {
        synchronized(lock) {
            return logEntries.takeLast(count).reversed()
        }
    }

    /**
     * Clear all logs
     */
    fun clearLogs() {
        synchronized(lock) {
            logEntries.clear()
        }
        Log.d(TAG, "Logs cleared")
    }

    /**
     * Get log count
     */
    fun getLogCount(): Int {
        synchronized(lock) {
            return logEntries.size
        }
    }

    // Convenience methods for common logging scenarios

    fun logMessageReceived(packageName: String, appName: String, content: String) {
        log(LogType.MESSAGE_RECEIVED, appName, content.take(50))
    }

    fun logMessageFiltered(packageName: String, appName: String, reason: String) {
        log(LogType.MESSAGE_FILTERED, appName, "消息被过滤", reason)
    }

    fun logReplyGenerated(packageName: String, appName: String, source: String, confidence: Float) {
        log(LogType.REPLY_GENERATED, appName, "生成${source}回复", "置信度: ${(confidence * 100).toInt()}%")
    }

    fun logFloatingWindowShown(packageName: String, appName: String) {
        log(LogType.FLOATING_WINDOW_SHOWN, appName, "显示悬浮窗")
    }

    fun logError(packageName: String, appName: String, error: String) {
        log(LogType.ERROR, appName, "发生错误", error)
    }
}