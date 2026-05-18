package com.csbaby.kefu.infrastructure.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.infrastructure.window.FloatingWindowService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.IO).launch {
                val preferences = preferencesManager.userPreferencesFlow.first()
                if (preferences.floatingIconEnabled) {
                    // 开机后如果开启了悬浮图标，则默认显示悬浮图标
                    FloatingWindowService.showIconOnly(context)
                }
            }
        }
    }
}
