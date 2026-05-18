package com.csbaby.kefu.infrastructure.notification

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

import com.csbaby.kefu.data.local.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationListenerServiceImpl : NotificationListenerService() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var messageMonitor: MessageMonitor

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        serviceScope.launch {
            try {
                val preferences = preferencesManager.userPreferencesFlow.first()
                Log.d(TAG, "NL.onNotificationPosted: monitoringEnabled=${preferences.monitoringEnabled}, selectedApps=${preferences.selectedApps.size}, package=${sbn.packageName}")
                if (!preferences.monitoringEnabled) {
                    Log.d(TAG, "NL: monitoring disabled, skip")
                    return@launch
                }
                if (preferences.selectedApps.isEmpty()) {
                    Log.d(TAG, "NL: no apps selected, skip")
                    return@launch
                }

                val packageName = sbn.packageName
                val isMonitored = isAppMonitored(packageName, preferences.selectedApps)
                Log.d(TAG, "NL: package=$packageName isMonitored=$isMonitored, selectedApps=${preferences.selectedApps}")
                if (!isMonitored) return@launch

                val notificationText = extractNotificationText(sbn)
                logBaijuyiNotification(packageName, notificationText, sbn.postTime)
                if (notificationText.content.isBlank()) {
                    Log.w(
                        TAG,
                        "Skip monitored notification without readable content. package=$packageName source=${notificationText.contentSource} title=${notificationText.title} conversation=${notificationText.conversationTitle}"
                    )
                    return@launch
                }


                val appName = runCatching {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                }.getOrDefault(packageName)


                val isGroupConversation = notificationText.conversationTitle.isNotBlank() &&
                    notificationText.conversationTitle != notificationText.title

                messageMonitor.emitMessage(
                    MessageMonitor.MonitoredMessage(
                        packageName = packageName,
                        appName = appName,
                        title = notificationText.title,
                        content = notificationText.content,
                        conversationTitle = notificationText.conversationTitle.ifBlank { null },
                        isGroupConversation = isGroupConversation,
                        timestamp = sbn.postTime
                    )
                )

                if (packageName == PreferencesManager.BAIJUYI_PACKAGE) {
                    Log.d(
                        TAG,
                        "Baijuyi notification forwarded to MessageMonitor. source=${notificationText.contentSource}, title=${notificationText.title}, conversation=${notificationText.conversationTitle}, isGroup=$isGroupConversation, postTime=${sbn.postTime}"
                    )
                }

                Log.d(
                    TAG,
                    "Captured message from $packageName: title=${notificationText.title}, conversation=${notificationText.conversationTitle}, content=${notificationText.content}"
                )



            } catch (e: Exception) {
                Log.e(TAG, "Failed to process notification", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun isAppMonitored(packageName: String, selectedApps: Set<String>): Boolean {
        return packageName in selectedApps
    }



    private fun extractNotificationText(sbn: StatusBarNotification): NotificationText {
        val extras = sbn.notification.extras
        val title = listOf(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString().orEmpty()
        ).firstOrNull { it.isNotBlank() }.orEmpty()
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty().trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
        val inboxLineText = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map { it?.toString().orEmpty().trim() }
            ?.lastOrNull { it.isNotBlank() }
            .orEmpty()
        val messagingText = extractMessagingStyleText(extras)
        val tickerText = sbn.notification.tickerText?.toString().orEmpty().trim()
        val contentCandidate = listOf(
            "messaging" to messagingText,
            "big_text" to bigText,
            "text" to text,
            "text_lines" to inboxLineText,
            "ticker" to tickerText
        ).firstOrNull { it.second.isNotBlank() }

        return NotificationText(
            title = title,
            content = contentCandidate?.second.orEmpty(),
            conversationTitle = conversationTitle,
            contentSource = contentCandidate?.first ?: "none",
            rawText = text,
            rawBigText = bigText,
            rawInboxLineText = inboxLineText,
            rawMessagingText = messagingText,
            rawTickerText = tickerText
        )
    }


    private fun extractMessagingStyleText(extras: Bundle): String {
        val rawMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                ?.mapNotNull { it as? Bundle }
                ?.toTypedArray()
                .orEmpty()
        }

        return rawMessages.asSequence()
            .mapNotNull { message ->
                message.getCharSequence("text")
                    ?.toString()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
            .lastOrNull()
            .orEmpty()
    }



    private fun logBaijuyiNotification(
        packageName: String,
        notificationText: NotificationText,
        postTime: Long
    ) {
        if (packageName != PreferencesManager.BAIJUYI_PACKAGE) return

        Log.d(
            TAG,
            "Baijuyi notification parsed. source=${notificationText.contentSource}, title=${previewForLog(notificationText.title)}, conversation=${previewForLog(notificationText.conversationTitle)}, content=${previewForLog(notificationText.content)}, text=${previewForLog(notificationText.rawText)}, bigText=${previewForLog(notificationText.rawBigText)}, textLines=${previewForLog(notificationText.rawInboxLineText)}, messages=${previewForLog(notificationText.rawMessagingText)}, ticker=${previewForLog(notificationText.rawTickerText)}, postTime=$postTime"
        )
    }

    private fun previewForLog(value: String?): String {
        val sanitized = value.orEmpty()
            .replace("\n", "\\n")
            .trim()
        return if (sanitized.length <= 120) sanitized else sanitized.take(117) + "..."
    }

    private data class NotificationText(
        val title: String,
        val content: String,
        val conversationTitle: String,
        val contentSource: String,
        val rawText: String,
        val rawBigText: String,
        val rawInboxLineText: String,
        val rawMessagingText: String,
        val rawTickerText: String
    )


    companion object {
        private const val TAG = "NotificationListener"

        fun isNotificationAccessEnabled(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ).orEmpty()

            return enabledListeners
                .split(":")
                .mapNotNull { ComponentName.unflattenFromString(it) }
                .any { it.packageName == context.packageName }
        }
    }
}


