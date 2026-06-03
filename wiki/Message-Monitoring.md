# Message Monitoring

## Service Overview

消息监控是csBaby的核心功能，通过NotificationListenerService实现：

### Core Components

1. **NotificationListenerService**
   - 监听系统通知
   - 过滤指定应用
   - 提取消息内容

2. **AccessibilityService**
   - 读取通知内容
   - 处理复杂场景

3. **FloatingWindowManager**
   - 管理浮动回复窗口
   - 用户交互处理

## Message Flow

```
系统通知 → NotificationListener → 消息过滤 → 关键词匹配
    ↓
匹配结果 → AI生成(可选) → 浮动窗口 → 用户确认 → 发送回复
```

## Implementation

### NotificationListenerService
```kotlin
class KefuNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (isMonitoredApp(sbn.packageName)) {
            val message = extractMessage(sbn)
            triggerReplyWindow(message)
        }
    }
}
```

### App Filter
- 从 [[Database Schema]] 读取已配置应用
- 支持启用/禁用切换
- 实时生效

## Configuration

| 配置项 | 说明 |
|--------|------|
| monitoredApps | 监听的应用列表 |
| autoReply | 是否自动发送 |
| confirmBeforeSend | 发送前确认 |

## Related
- [[Knowledge Base]]
- [[Reply Generator]]
- [[Notification Service]]
- [[Database Schema]]