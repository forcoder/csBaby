package com.csbaby.kefu.infrastructure.oss

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.util.Properties

/**
 * OSS配置管理类
 * 从外部配置文件读取OSS配置信息
 */
class OssConfig(private val context: Context) {
    
    companion object {
        // 默认配置
        private const val DEFAULT_ENDPOINT = ""
        private const val DEFAULT_BUCKET = ""
        private const val DEFAULT_ACCESS_KEY_ID = ""
        private const val DEFAULT_ACCESS_KEY_SECRET = ""
        private const val DEFAULT_APP_NAME = "kefu"
        private const val DEFAULT_APK_FOLDER = "apks/"
        private const val DEFAULT_AUTO_UPLOAD_ENABLED = true.toString()
        private const val DEFAULT_AUTO_UPGRADE = true.toString()
        
        // 配置键名
        private const val KEY_ENDPOINT = "oss.endpoint"
        private const val KEY_BUCKET = "oss.bucket"
        private const val KEY_ACCESS_KEY_ID = "oss.access_key_id"
        private const val KEY_ACCESS_KEY_SECRET = "oss.access_key_secret"
        private const val KEY_APP_NAME = "app.name"
        private const val KEY_APK_FOLDER = "oss.apk_folder"
        private const val KEY_AUTO_UPLOAD_ENABLED = "auto_upload.enabled"
        private const val KEY_AUTO_UPGRADE = "auto_upload.auto_upgrade"
    }
    
    private var properties: Properties? = null
    
    init {
        loadConfig()
    }
    
    /**
     * 加载配置文件
     */
    private fun loadConfig() {
        try {
            // 尝试从多个位置加载配置文件
            val configFile = getConfigFile()
            if (configFile.exists()) {
                FileInputStream(configFile).use { input ->
                    properties = Properties().apply {
                        load(input)
                    }
                }
            } else {
                // 文件不存在，使用默认配置
                properties = Properties()
                Timber.w("OSS配置文件不存在: ${configFile.absolutePath}")
                Timber.w("请创建 oss-config.properties 文件或使用示例文件")
            }
        } catch (e: Exception) {
            Timber.e(e, "加载OSS配置文件失败")
            properties = Properties()
        }
    }
    
    /**
     * 获取配置文件路径
     * 优先级: 1.外部配置文件 2.assets目录 3.默认配置
     */
    private fun getConfigFile(): File {
        // 优先使用项目根目录的配置文件
        val projectDir = File(context.getExternalFilesDir(null)?.parentFile?.parentFile?.parentFile?.parentFile, "..")
        val externalConfig = File(projectDir, "oss-config.properties")
        
        if (externalConfig.exists()) {
            return externalConfig
        }
        
        // 尝试assets目录
        return File("") // 返回空文件表示未找到
    }
    
    /**
     * 获取配置值
     */
    private fun getProperty(key: String, defaultValue: String = ""): String {
        return properties?.getProperty(key, defaultValue) ?: defaultValue
    }
    
    /**
     * 获取OSS域名
     */
    val endpoint: String
        get() = getProperty(KEY_ENDPOINT, DEFAULT_ENDPOINT)
    
    /**
     * 获取Bucket名称
     */
    val bucket: String
        get() = getProperty(KEY_BUCKET, DEFAULT_BUCKET)
    
    /**
     * 获取AccessKey ID
     */
    val accessKeyId: String
        get() = getProperty(KEY_ACCESS_KEY_ID, DEFAULT_ACCESS_KEY_ID)
    
    /**
     * 获取AccessKey Secret
     */
    val accessKeySecret: String
        get() = getProperty(KEY_ACCESS_KEY_SECRET, DEFAULT_ACCESS_KEY_SECRET)
    
    /**
     * 获取应用名称
     */
    val appName: String
        get() = getProperty(KEY_APP_NAME, DEFAULT_APP_NAME)
    
    /**
     * 获取APK文件夹路径
     */
    val apkFolder: String
        get() = getProperty(KEY_APK_FOLDER, DEFAULT_APK_FOLDER)
    
    /**
     * 获取是否启用自动上传
     */
    val autoUploadEnabled: Boolean
        get() = getProperty(KEY_AUTO_UPLOAD_ENABLED, DEFAULT_AUTO_UPLOAD_ENABLED).toBoolean()
    
    /**
     * 获取是否自动升级版本
     */
    val autoUpgrade: Boolean
        get() = getProperty(KEY_AUTO_UPGRADE, DEFAULT_AUTO_UPGRADE).toBoolean()
    
    /**
     * 验证OSS配置是否有效
     */
    fun isValid(): Boolean {
        return endpoint.isNotBlank() && 
               bucket.isNotBlank() && 
               accessKeyId.isNotBlank() && 
               accessKeySecret.isNotBlank()
    }
    
    /**
     * 获取OSS域名（完整的URL）
     */
    fun getOssDomain(): String {
        return "https://$bucket.$endpoint/"
    }
    
    /**
     * 重新加载配置
     */
    fun reload() {
        loadConfig()
    }
}