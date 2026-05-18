package com.kefu.xiaomi.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AccessibilityServiceImpl : AccessibilityService() {

    companion object {
        var instance: AccessibilityServiceImpl? = null
            private set

        private val monitoredPackages = mutableSetOf<String>()

        fun addMonitoredPackage(packageName: String) {
            monitoredPackages.add(packageName)
        }

        fun removeMonitoredPackage(packageName: String) {
            monitoredPackages.remove(packageName)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (monitoredPackages.contains(it.packageName?.toString())) {
                when (it.eventType) {
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                        // 窗口状态变化
                    }
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                        // 窗口内容变化
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        // 服务中断
    }

    fun getRecentMessages(): List<String> {
        // 可以返回最近获取的消息
        return emptyList()
    }
}
