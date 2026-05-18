package com.csbaby.kefu.infrastructure.notification

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Central message monitor that coordinates message events across the app.
 * Acts as a bridge between NotificationListenerService and other components.
 */
@Singleton
class MessageMonitor @Inject constructor() {

    private val _messageFlow = MutableSharedFlow<MonitoredMessage>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val messageFlow: SharedFlow<MonitoredMessage> = _messageFlow.asSharedFlow()

    private val listeners = mutableListOf<MessageListener>()

    interface MessageListener {
        fun onNewMessage(message: MonitoredMessage)
    }

    fun addListener(listener: MessageListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }

    fun removeListener(listener: MessageListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    suspend fun emitMessage(message: MonitoredMessage) {
        // Emit to flow for coroutine-based consumers
        _messageFlow.emit(message)

        // Notify listeners
        synchronized(listeners) {
            listeners.forEach { listener ->
                try {
                    listener.onNewMessage(message)
                } catch (e: Exception) {
                    // Continue with other listeners
                }
            }
        }
    }

    data class MonitoredMessage(
        val packageName: String,
        val appName: String,
        val title: String,
        val content: String,
        val conversationTitle: String? = null,
        val isGroupConversation: Boolean? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
}

