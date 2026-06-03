# Notification Service

## Service Types

### 1. NotificationListenerService
```kotlin
class KefuNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        processNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // 处理通知移除
    }
}
```

### 2. AccessibilityService
- 读取通知详情
- 模拟用户操作
- 处理特殊场景

## Permission Requirements

| Permission | 用途 |
|------------|------|
| BIND_NOTIFICATION_LISTENER_SERVICE | 监听通知 |
| SYSTEM_ALERT_WINDOW | 悬浮窗 |
| FOREGROUND_SERVICE | 前台服务 |
| RECEIVE_BOOT_COMPLETED | 开机自启 |

## Message Extraction

### Extract Strategy
```kotlin
fun extractMessage(sbn: StatusBarNotification): String? {
    val extras = sbn.notification.extras
    return extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
}
```

## Package Filtering

```kotlin
fun isMonitoredPackage(packageName: String): Boolean {
    return monitoredApps.any { it.packageName == packageName }
}
```

## Related
- [[Message Monitoring]]
- [[Reply Generator]]
- [[Database Schema]]