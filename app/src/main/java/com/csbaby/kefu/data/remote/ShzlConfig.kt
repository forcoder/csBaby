package com.csbaby.kefu.data.remote

import com.csbaby.kefu.BuildConfig

/**
 * shz.al 文件分享服务配置
 * 集中管理所有与 shz.al 相关的配置
 */
object ShzlConfig {

    /**
     * 版本文件名称
     * 访问URL: https://shz.al/~csBabyLog
     */
    const val VERSION_FILE_NAME = "csBabyLog"

    /**
     * APK文件名称
     * 访问URL: https://shz.al/~csBabyApk
     */
    const val APK_FILE_NAME = "csBabyApk"

    /**
     * 版本文件完整URL
     */
    const val VERSION_FILE_URL = "https://shz.al/~$VERSION_FILE_NAME"

    /**
     * APK文件完整URL
     */
    const val APK_FILE_URL = "https://shz.al/~$APK_FILE_NAME"

    /**
     * 上传密码（从 BuildConfig 读取，避免硬编码）
     */
    val UPLOAD_PASSWORD: String = BuildConfig.SHZL_UPLOAD_PASSWORD

    /**
     * 过期时间（7天）
     */
    const val EXPIRE_TIME = "7d"
}
