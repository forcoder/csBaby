package com.csbaby.kefu.domain.model

/**
 * 同步状态 — 描述当前同步操作的状态。
 * 属于 domain 层，供 UI 层（ProfileViewModel）和 data 层（SyncManager）共享。
 */
sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
    data class Success(val message: String, val stats: String = "") : SyncState()
}
