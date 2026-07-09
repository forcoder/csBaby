package com.csbaby.kefu.infrastructure.ota

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * APK 完整性校验器(MD5) — 纯函数单测。
 *
 * 根因(解析包出现错误):服务端 404、CDN 故障、网络中断等场景下,
 * DownloadManager 写入的可能是 HTML 错误页或截断的 APK,APK 文件
 * 大小正常但内容损坏,PackageInstaller 解析时报"解析包出现错误"。
 *
 * OtaUpdate.md5 字段已定义但 OtaManager 从未使用,本测试验证
 * ApkIntegrityChecker.verify 在 MD5 不匹配时返回 failure 并清理文件。
 */
class ApkIntegrityCheckerTest {

    private lateinit var tempDir: File
    private lateinit var validApk: File

    @Before
    fun setUp() {
        // ApkIntegrityChecker 内部调用 android.util.Log, JVM 单测需 mock
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        tempDir = createTempDir(prefix = "apk_check_test_")
        validApk = File(tempDir, "kefu_v1.4.4_15.apk")
        // 写入固定内容 "kefu-apk-content" 用于 MD5 计算
        FileOutputStream(validApk).use { it.write("kefu-apk-content".toByteArray()) }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        unmockkAll()
    }

    @Test
    fun `MD5 匹配时返回 success`() {
        val expectedMd5 = md5OfFile(validApk)

        val result = ApkIntegrityChecker.verify(validApk, expectedMd5)

        assertTrue("MD5 匹配应返回 success, 实际: $result", result.isSuccess)
        assertEquals(validApk, result.getOrNull())
    }

    @Test
    fun `MD5 不匹配时返回 failure 并删除损坏文件`() {
        val wrongMd5 = "00000000000000000000000000000000"

        val result = ApkIntegrityChecker.verify(validApk, wrongMd5)

        assertTrue("MD5 不匹配应返回 failure, 实际: $result", result.isFailure)
        assertNotNull("failure 应包含异常信息", result.exceptionOrNull())
        assertTrue(
            "错误信息应说明 MD5 mismatch, 实际: ${result.exceptionOrNull()?.message}",
            (result.exceptionOrNull()?.message ?: "").contains("MD5")
        )
        // 关键:损坏文件必须被删除, 否则下次启动用户会再次遇到"解析包错误"
        assertTrue("MD5 不匹配时损坏文件应被删除", !validApk.exists())
    }

    @Test
    fun `期望 MD5 为空时跳过校验直接成功`() {
        // 场景:服务端未发布 MD5 字段(老版本服务端), 不应阻塞用户升级
        val result = ApkIntegrityChecker.verify(validApk, expectedMd5 = "")

        assertTrue("MD5 为空应跳过校验并 success", result.isSuccess)
    }

    @Test
    fun `APK 文件不存在时返回 failure 不抛异常`() {
        val nonExistent = File(tempDir, "missing.apk")

        val result = ApkIntegrityChecker.verify(nonExistent, expectedMd5 = "abc")

        assertTrue("文件不存在应返回 failure", result.isFailure)
        assertTrue(
            "错误信息应说明文件不存在, 实际: ${result.exceptionOrNull()?.message}",
            (result.exceptionOrNull()?.message ?: "").contains("not found") ||
                (result.exceptionOrNull()?.message ?: "").contains("不存在")
        )
    }

    @Test
    fun `MD5 不区分大小写`() {
        val expectedMd5 = md5OfFile(validApk)
        val upperCase = expectedMd5.uppercase()

        val result = ApkIntegrityChecker.verify(validApk, upperCase)

        assertTrue("MD5 大小写应不敏感, 实际: $result", result.isSuccess)
    }

    private fun md5OfFile(file: File): String {
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
