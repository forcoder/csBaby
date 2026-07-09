package com.csbaby.kefu.infrastructure.ota

import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * APK 完整性校验器 — 纯函数,基于 MD5 摘要。
 *
 * 根因(解析包出现错误):
 *   服务端 404/CDN 故障/网络中断下, DownloadManager 写入的可能是 HTML 错误页
 *   或截断的 APK,文件大小看似正常但内容损坏,PackageInstaller 解析时报错。
 *   OtaUpdate.md5 字段已定义但从未使用,本类补齐校验链。
 *
 * 行为:
 * - expectedMd5 为空 → 跳过校验(兼容老版本服务端)
 * - 文件不存在 → Result.failure
 * - 校验失败 → 删除损坏文件 + Result.failure(防止下次启动再撞同一颗雷)
 * - MD5 比较不区分大小写
 */
object ApkIntegrityChecker {

    private const val TAG = "ApkIntegrityChecker"

    /**
     * 校验 [apkFile] 的 MD5 是否与 [expectedMd5] 一致。
     *
     * @return Result.success(file) 表示通过; Result.failure 表示不通过, 损坏文件已删除
     */
    fun verify(apkFile: File, expectedMd5: String): Result<File> {
        if (!apkFile.exists()) {
            return Result.failure(IllegalStateException("APK file not found: ${apkFile.absolutePath}"))
        }

        // 兼容老版本服务端: 期望 MD5 为空时跳过校验
        if (expectedMd5.isBlank()) {
            Log.w(TAG, "expectedMd5 为空, 跳过 MD5 校验 (兼容老版本服务端): ${apkFile.name}")
            return Result.success(apkFile)
        }

        return try {
            val actualMd5 = computeMd5(apkFile)
            if (actualMd5.equals(expectedMd5, ignoreCase = true)) {
                Result.success(apkFile)
            } else {
                Log.e(TAG, "APK MD5 不匹配: expected=$expectedMd5, actual=$actualMd5, file=${apkFile.name}")
                // 删除损坏文件, 防止下次启动重复触发"解析包错误"
                runCatching { apkFile.delete() }
                Result.failure(
                    IllegalStateException("APK MD5 mismatch: expected=$expectedMd5, actual=$actualMd5")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "MD5 计算失败", e)
            Result.failure(IllegalStateException("MD5 计算失败: ${e.message}", e))
        }
    }

    private fun computeMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
