# Sync Protocol

## Protocol Overview

云端同步协议定义数据交换格式：

### Message Format

```json
{
  "type": "sync",
  "entity": "keyword_rule",
  "action": "create|update|delete",
  "timestamp": 1717180800000,
  "data": {
    "id": 1,
    "keyword": "退货",
    "matchType": "EXACT"
  }
}
```

## Conflict Resolution

### Strategy: Last-Write-Wins
```kotlin
fun resolveConflict(local: Entity, remote: Entity): Entity {
    return if (local.timestamp > remote.timestamp) local else remote
}
```

### Merge Scenarios
- 新建 + 新建 → 保留两者
- 修改 + 修改 → 时间戳优先
- 删除 + 修改 → 删除优先

## Sync Queue

```kotlin
class SyncQueue {
    fun enqueue(entity: Entity, action: SyncAction)
    fun dequeue(): SyncItem?
    fun flush(): Result<Unit>
}
```

### Offline Support
- 离线时入队
- 上线后批量同步
- 重试机制

## Error Handling

| 错误类型 | 处理策略 |
|----------|----------|
| 网络超时 | 重试3次 |
| 认证失效 | 刷新token |
| 冲突 | 自动合并 |
| 服务器错误 | 延迟重试 |

## Related
- [[Cloud Sync]]
- [[Repository Pattern]]
- [[Database Schema]]