package com.kefu.xiaomi.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MessageMonitorService : NotificationListenerService() {

    companion object {
        private val _messageFlow = MutableSharedFlow<NewMessageData>()
        val messageFlow: SharedFlow<NewMessageData> = _messageFlow.asSharedFlow()

        private val monitoredPackages = mutableSetOf<String>()
    }

    data class NewMessageData(
        val packageName: String,
        val appName: String,
        val title: String,
        val content: String,
        val timestamp: Long
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            if (monitoredPackages.contains(it.packageName)) {
                val extras = it.notification.extras
                val title = extras.getCharSequence("android.title")?.toString() ?: ""
                val content = extras.getCharSequence("android.text")?.toString() ?: ""

                if (content.isNotBlank()) {
                    _messageFlow.tryEmit(
                        NewMessageData(
                            packageName = it.packageName,
                            appName = it.appInfo.loadLabel(packageManager).toString(),
                            title = title,
                            content = content,
                            timestamp = it.postTime
                        )
                    )
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // 可以记录移除的通知
    }

    fun addMonitoredPackage(packageName: String) {
        monitoredPackages.add(packageName)
    }

    fun removeMonitoredPackage(packageName: String) {
        monitoredPackages.remove(packageName)
    }

    fun setMonitoredPackages(packages: Set<String>) {
        monitoredPackages.clear()
        monitoredPackages.addAll(packages)
    }

    fun isMonitoring(): Boolean = monitoredPackages.isNotEmpty()
}
