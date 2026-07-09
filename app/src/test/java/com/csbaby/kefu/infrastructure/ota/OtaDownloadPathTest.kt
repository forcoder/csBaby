package com.csbaby.kefu.infrastructure.ota

import com.csbaby.kefu.data.model.OtaUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * OTA 下载路径解析器 — 纯函数单测。
 *
 * 根因(解析包出现错误):原 OtaManager 把 APK 写到
 * Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)/KefuUpdates/,
 * 该路径在 Android 10+ Scoped Storage 下与 file_paths.xml 的
 * <external-path path="Download/KefuUpdates/"> 不一致,
 * FileProvider.getUriForFile() 抛 IllegalArgumentException,Intent URI 无法解析,
 * PackageInstaller 报"解析包出现错误"。
 *
 * 修复后:路径必须在 getExternalFilesDir("ota_updates") 内,
 * 与 file_paths.xml 的 <external-files-path name="external_app_files" path="."/> 严格对齐。
 *
 * 设计:OtaDownloadPath.resolve(update) 只产出 (子目录, 文件名) 决策,
 * OtaManager 负责把决策落地到 baseDir = context.getExternalFilesDir(subdir)。
 */
class OtaDownloadPathTest {

    private val sampleUpdate = OtaUpdate(
        versionCode = 15,
        versionName = "1.4.4",
        downloadUrl = "https://example.com/kefu.apk",
        fileSize = 12_345_678L,
        md5 = "",
        releaseNotes = "fix",
        releaseDate = "2026-07-09",
        isForceUpdate = false,
        minRequiredVersion = 1
    )

    @Test
    fun `正常情况下文件名以 apk 结尾且命名规范符合预期`() {
        val decision = OtaDownloadPath.resolve(sampleUpdate)

        assertEquals(OtaDownloadPath.SUBDIR, decision.subdir)
        assertEquals("kefu_v1.4.4_15.apk", decision.fileName)
    }

    @Test
    fun `OtaManager 按 decision 拼装后路径在 ota_updates 子目录`() {
        val decision = OtaDownloadPath.resolve(sampleUpdate)
        // 模拟 OtaManager 落地: File(getExternalFilesDir(decision.subdir), decision.fileName)
        // 关键校验: decision.subdir 必须是 ota_updates, FileProvider external-files-path 才能匹配
        assertEquals("ota_updates", decision.subdir)
        // 模拟一次 baseDir 拼接, 验证不会因 File 解析导致文件被截断到奇怪位置
        val baseDir = File("/storage/emulated/0/Android/data/com.csbaby.kefu/files")
        val file = File(baseDir, decision.fileName)
        assertEquals("kefu_v1.4.4_15.apk", file.name)
    }

    @Test
    fun `versionName 为空时抛 IllegalArgumentException`() {
        val invalid = sampleUpdate.copy(versionName = "")
        try {
            OtaDownloadPath.resolve(invalid)
            fail("versionName 为空应当抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
            assertTrue(
                "错误信息应提到 versionName, 实际: ${e.message}",
                e.message!!.contains("versionName")
            )
        }
    }

    @Test
    fun `versionCode 小于等于 0 时抛 IllegalArgumentException`() {
        val invalid = sampleUpdate.copy(versionCode = 0)
        try {
            OtaDownloadPath.resolve(invalid)
            fail("versionCode <= 0 应当抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "错误信息应提到 versionCode, 实际: ${e.message}",
                e.message!!.contains("versionCode")
            )
        }
    }

    @Test
    fun `versionName 含特殊字符时文件名仍然合法`() {
        val update = sampleUpdate.copy(versionName = "1.4.4-rc.1")
        val decision = OtaDownloadPath.resolve(update)

        // 文件名不应包含路径分隔符
        assertFalse(
            "文件名不应包含 '/', 实际: ${decision.fileName}",
            decision.fileName.contains("/")
        )
        assertFalse(
            "文件名不应包含 '\\\\', 实际: ${decision.fileName}",
            decision.fileName.contains("\\")
        )
        assertTrue("文件名应以 .apk 结尾", decision.fileName.endsWith(".apk"))
    }
}
