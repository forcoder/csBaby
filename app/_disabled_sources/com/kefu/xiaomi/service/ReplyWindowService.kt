package com.kefu.xiaomi.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ReplyWindowService : Service() {

    companion object {
        var isRunning = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
