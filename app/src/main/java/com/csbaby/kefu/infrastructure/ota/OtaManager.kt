package com.csbaby.kefu.infrastructure.ota

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.csbaby.kefu.BuildConfig
import com.csbaby.kefu.data.model.OtaUpdate
import com.csbaby.kefu.data.model.UpdateStatus
import com.csbaby.kefu.data.repository.OtaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OTA更新管理器
 */
@Singleton
class OtaManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: OtaRepository
) {
    
    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.IDLE)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()
    
    private val _availableUpdate = MutableStateFlow<OtaUpdate?>(null)
    val availableUpdate: StateFlow<OtaUpdate?> = _availableUpdate.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private var downloadId: Long = -1
    private var downloadManager: DownloadManager? = null
    private var downloadReceiver: BroadcastReceiver? = null
    
    companion object {
        private const val TAG = "OtaManager"
    }
    
    /**
     * 检查更新
     */
    suspend fun checkForUpdate(): Boolean {
        _updateStatus.value = UpdateStatus.CHECKING
        _errorMessage.value = null
        
        return try {
            val result = repository.checkForUpdate(BuildConfig.VERSION_CODE)
            
            if (result.isSuccess) {
                val update = result.getOrNull()
                
                if (update != null && update.needsUpdate(BuildConfig.VERSION_CODE)) {
                    _availableUpdate.value = update
                    _updateStatus.value = UpdateStatus.UPDATE_AVAILABLE
                    true
                } else {
                    _updateStatus.value = UpdateStatus.IDLE
                    false
                }
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message
                _updateStatus.value = UpdateStatus.FAILED
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
            _errorMessage.value = "检查更新失败: ${e.message}"
            _updateStatus.value = UpdateStatus.FAILED
            false
        }
    }
    
    /**
     * 开始下载更新
     */
    fun startDownload(update: OtaUpdate): Boolean {
        _updateStatus.value = UpdateStatus.DOWNLOADING
        _errorMessage.value = null
        
        try {
            downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            // 创建下载目录
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDir = File(downloadsDir, "KefuUpdates")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }
            
            val fileName = "kefu_v${update.versionName}_${update.versionCode}.apk"
            val downloadFile = File(appDir, fileName)
            
            // 如果文件已存在，先删除
            if (downloadFile.exists()) {
                downloadFile.delete()
            }
            
            val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
                .setTitle("客服助手更新 v${update.versionName}")
                .setDescription("正在下载更新...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(downloadFile))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                request.setRequiresCharging(false)
            }
            
            downloadId = downloadManager?.enqueue(request) ?: return false
            
            // 注册下载完成广播接收器
            registerDownloadReceiver()
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "开始下载失败", e)
            _errorMessage.value = "开始下载失败: ${e.message}"
            _updateStatus.value = UpdateStatus.FAILED
            return false
        }
    }
    
    /**
     * 注册下载完成广播接收器
     */
    private fun registerDownloadReceiver() {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                
                if (id == downloadId) {
                    val query = DownloadManager.Query()
                    query.setFilterById(id)
                    
                    val cursor = downloadManager?.query(query)
                    
                    if (cursor?.moveToFirst() == true) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        if (statusIdx >= 0) {
                            val status = cursor.getInt(statusIdx)
                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    _updateStatus.value = UpdateStatus.DOWNLOADED
                                    val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                                    if (uriIdx >= 0) {
                                        val uri = cursor.getString(uriIdx)
                                        if (uri != null) {
                                            val downloadedFile = File(Uri.parse(uri).path ?: "")
                                            _availableUpdate.value?.let { update ->
                                                prepareInstallation(downloadedFile)
                                            }
                                        }
                                    }
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                    val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else -1
                                    _errorMessage.value = "下载失败: ${getDownloadErrorReason(reason)}"
                                    _updateStatus.value = UpdateStatus.FAILED
                                }
                            }
                        }
                    }
                    cursor?.close()
                }
            }
        }
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(context, downloadReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(downloadReceiver, filter)
        }
    }
    
    /**
     * 准备安装APK
     */
    private fun prepareInstallation(apkFile: File) {
        try {
            _updateStatus.value = UpdateStatus.INSTALLING
            
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ 使用FileProvider
                FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
            
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            
            context.startActivity(installIntent)
            
        } catch (e: Exception) {
            Log.e(TAG, "安装准备失败", e)
            _errorMessage.value = "安装准备失败: ${e.message}"
            _updateStatus.value = UpdateStatus.FAILED
        }
    }
    
    /**
     * 获取下载错误原因
     */
    private fun getDownloadErrorReason(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "无法恢复下载"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "设备未找到"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "文件已存在"
            DownloadManager.ERROR_FILE_ERROR -> "文件错误"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP数据错误"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "重定向过多"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "未处理的HTTP代码"
            DownloadManager.ERROR_UNKNOWN -> "未知错误"
            else -> "错误代码: $reason"
        }
    }
    
    /**
     * 取消下载
     */
    fun cancelDownload() {
        if (downloadId != -1L) {
            downloadManager?.remove(downloadId)
            downloadId = -1
        }
        
        downloadReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // 忽略取消注册错误
            }
            downloadReceiver = null
        }
        
        _updateStatus.value = UpdateStatus.IDLE
        _errorMessage.value = null
    }
    
    /**
     * 清理状态
     */
    fun cleanup() {
        cancelDownload()
        _updateStatus.value = UpdateStatus.IDLE
        _availableUpdate.value = null
        _errorMessage.value = null
    }
}