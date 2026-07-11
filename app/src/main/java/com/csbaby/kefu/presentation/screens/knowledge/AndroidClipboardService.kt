package com.csbaby.kefu.presentation.screens.knowledge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidClipboardService @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : ClipboardService {
    override fun putText(label: String, text: String) {
        val manager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        manager.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
