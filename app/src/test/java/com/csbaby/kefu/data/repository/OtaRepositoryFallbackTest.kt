package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.model.OtaUpdate
import com.csbaby.kefu.data.remote.OtaApiService
import io.mockk.*
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * BUG-RT1 regression: v1.4.7 点击"检查更新"直接报错。
 *
 * 根因: 当前 main 上的 OtaRepositoryImpl.checkForUpdate 只查询版本专用 URL
 *      (~csBabyLog_v{versionCode}), 该 URL 在 OTA 部署时未上传(workflow 改为
 *      1aebc1b2 之后只上传版本专用文件, 而且该文件可能是字面模板) →
 *      HttpException 404 → UI 显示"网络连接失败"。
 *
 * 修复后行为:
 *   1. 优先 master URL (~csBabyLog) — 长期稳定, 由 OTA 维护者主动管理
 *   2. master URL 返回 404 或 HttpException → fallback 到版本专用 URL
 *   3. 两个 URL 都 404 → 视为无更新 (success(null)), 不报错
 *   4. 其他 HTTP 错误 (5xx 等) → 返回 failure
 *   5. 网络异常 (IOException) → 返回 failure
 *   6. 解析字面模板 (JsonSyntaxException) → 视为 404, 走 fallback
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OtaRepositoryFallbackTest {

    private lateinit var apiService: OtaApiService
    private lateinit var repo: OtaRepositoryImpl

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        val context = mockk<android.content.Context>(relaxed = true)
        every { context.getExternalFilesDir(any()) } returns null
        apiService = mockk(relaxed = true)
        repo = OtaRepositoryImpl(context, apiService)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `BUG-RT1 master URL 返回 200 且有新版时直接使用, 不走版本专用 URL`() = runBlocking {
        // 模拟: master URL 返回 v19 (currentVersionCode=18, 19>18, 有更新)
        coEvery { apiService.checkForUpdate(match { it == "https://shz.al/~csBabyLog" }) } returns
            OtaUpdate(versionCode = 19, versionName = "1.4.8", downloadUrl = "https://x.com/a.apk",
                fileSize = 0L, md5 = "", releaseNotes = "", releaseDate = "",
                isForceUpdate = false, minRequiredVersion = 1)

        val result = repo.checkForUpdate(currentVersionCode = 18)

        assertTrue("master 200 应返回 success", result.isSuccess)
        assertEquals(19, result.getOrNull()?.versionCode)
        // 只调了 master, 不调版本专用 URL
        coVerify(exactly = 1) { apiService.checkForUpdate(any()) }
    }

    @Test
    fun `BUG-RT1 master URL 返回 404 时 fallback 到版本专用 URL`() = runBlocking {
        // 模拟: master URL 404, 版本专用 URL 返回 v18
        coEvery { apiService.checkForUpdate(match { it == "https://shz.al/~csBabyLog" }) } throws http404()
        coEvery { apiService.checkForUpdate(match { it.contains("csBabyLog_v18") }) } returns
            OtaUpdate(versionCode = 19, versionName = "1.4.8", downloadUrl = "https://x.com/a.apk",
                fileSize = 0L, md5 = "", releaseNotes = "", releaseDate = "",
                isForceUpdate = false, minRequiredVersion = 1)

        val result = repo.checkForUpdate(currentVersionCode = 18)

        assertTrue("fallback 成功应该 success", result.isSuccess)
        assertEquals(19, result.getOrNull()?.versionCode)
        // master → 404 → fallback 到 v18 URL, 共 2 次调用
        coVerify(exactly = 2) { apiService.checkForUpdate(any()) }
    }

    @Test
    fun `BUG-RT1 master URL 返回字面模板(非 JSON)时应 fallback 到版本专用 URL`() = runBlocking {
        // 模拟: master URL 返回 200 但内容是字面模板 (workflow bug 产物)
        // Gson 解析 OtaUpdate 时抛 JsonSyntaxException, 应被识别为不可用, 走 fallback
        coEvery { apiService.checkForUpdate(match { it == "https://shz.al/~csBabyLog" }) } throws
            com.google.gson.JsonSyntaxException("Expected int but was STRING")
        coEvery { apiService.checkForUpdate(match { it.contains("csBabyLog_v18") }) } returns
            OtaUpdate(versionCode = 19, versionName = "1.4.8", downloadUrl = "https://x.com/a.apk",
                fileSize = 0L, md5 = "", releaseNotes = "", releaseDate = "",
                isForceUpdate = false, minRequiredVersion = 1)

        val result = repo.checkForUpdate(currentVersionCode = 18)

        assertTrue("JsonSyntaxException 应被吞掉, fallback 成功", result.isSuccess)
        assertEquals(19, result.getOrNull()?.versionCode)
        coVerify(exactly = 2) { apiService.checkForUpdate(any()) }
    }

    @Test
    fun `BUG-RT1 两个 URL 都 404 视为无更新而非错误`() = runBlocking {
        coEvery { apiService.checkForUpdate(any()) } throws http404()

        val result = repo.checkForUpdate(currentVersionCode = 18)

        assertTrue("两个 URL 都 404 应该 success (视为无更新), 不是 failure",
            result.isSuccess)
        assertNull("返回值应为 null (无更新)", result.getOrNull())
    }

    @Test
    fun `BUG-RT1 500 错误应该返回 failure 不再 fallback`() = runBlocking {
        val http500 = HttpException(Response.error<Any>(500,
            "boom".toResponseBody("text/plain".toMediaTypeOrNull())))
        coEvery { apiService.checkForUpdate(match { it == "https://shz.al/~csBabyLog" }) } throws http500

        val result = repo.checkForUpdate(currentVersionCode = 18)

        assertTrue("500 错误应返回 failure", result.isFailure)
        // 500 不应触发 fallback, 只调用 1 次
        coVerify(exactly = 1) { apiService.checkForUpdate(any()) }
    }

    @Test
    fun `BUG-RT1 IOException 网络错误返回 failure`() = runBlocking {
        coEvery { apiService.checkForUpdate(any()) } throws IOException("network unreachable")

        val result = repo.checkForUpdate(currentVersionCode = 18)

        assertTrue("网络错误应返回 failure", result.isFailure)
        assertTrue("错误信息应包含'网络连接失败'",
            (result.exceptionOrNull()?.message ?: "").contains("网络连接失败"))
    }

    @Test
    fun `正常情况 master URL 有更新时直接返回`() = runBlocking {
        // 首选 master URL 即拿到新版本, 不应 fallback 到版本 URL
        coEvery { apiService.checkForUpdate(match { it == "https://shz.al/~csBabyLog" }) } returns
            OtaUpdate(versionCode = 19, versionName = "1.4.8", downloadUrl = "",
                fileSize = 0L, md5 = "", releaseNotes = "", releaseDate = "",
                isForceUpdate = false, minRequiredVersion = 1)

        val result = repo.checkForUpdate(currentVersionCode = 18)

        assertTrue(result.isSuccess)
        assertEquals(19, result.getOrNull()?.versionCode)
        // 不需要 fallback, 只调用 1 次
        coVerify(exactly = 1) { apiService.checkForUpdate(any()) }
    }

    @Test
    fun `正常情况 master URL 返回同版本视为无更新`() = runBlocking {
        coEvery { apiService.checkForUpdate(any()) } returns
            OtaUpdate(versionCode = 18, versionName = "1.4.7", downloadUrl = "",
                fileSize = 0L, md5 = "", releaseNotes = "", releaseDate = "",
                isForceUpdate = false, minRequiredVersion = 1)

        val result = repo.checkForUpdate(currentVersionCode = 18)

        assertTrue(result.isSuccess)
        assertNull("同版本应返回 null", result.getOrNull())
    }

    private fun http404(): HttpException =
        HttpException(Response.error<Any>(404,
            "Not Found".toResponseBody("text/plain".toMediaTypeOrNull())))
}
