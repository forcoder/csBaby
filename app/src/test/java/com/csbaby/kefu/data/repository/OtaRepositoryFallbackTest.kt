package com.csbaby.kefu.data.repository

import com.csbaby.kefu.data.model.OtaUpdate
import com.csbaby.kefu.data.remote.OtaApiService
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * BUG-R9 regression: 应用更新 HTTP 404 被当作错误,UI 提示"网络连接失败"。
 *
 * 根因: OtaRepositoryImpl.checkForUpdate 只尝试版本专用 URL
 *      (shz.al/~csBabyLog_v{versionCode}),文件不存在 (404) 时
 *      没有 fallback 到 master URL (shz.al/~csBabyLog),
 *      用户体验为 "网络连接失败" 而非 "已是最新版本"。
 *
 * 修复后行为 (OTA-R10 调整):
 *   1. **优先** master URL (shz.al/~csBabyLog) — 因为版本号会变,带版本的 URL 不再作首选
 *   2. master URL 返回 404 → fallback 到版本专用 URL
 *   3. 两个 URL 都 404 → 视为无更新 (success(null)),不报错
 *   4. 其他 HTTP 错误 (5xx 等) → 返回 failure
 *   5. 网络异常 (IOException) → 返回 failure
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OtaRepositoryFallbackTest {

    private lateinit var apiService: OtaApiService
    private lateinit var repo: OtaRepositoryImpl

    @Before
    fun setup() {
        // mock android.util.Log
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
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
    fun `BUG-R9 master URL返回404时fallback到版本专用URL`() = runBlocking {
        // 模拟: master URL 404 (首选失败), 版本专用 URL 返 200
        coEvery { apiService.checkForUpdate(match { it == "https://shz.al/~csBabyLog" }) } throws http404()
        coEvery { apiService.checkForUpdate(match { it.contains("csBabyLog_v14") }) } returns
            OtaUpdate(versionCode = 15, versionName = "1.4.4", downloadUrl = "https://x.com/a.apk",
                     fileSize = 0L, md5 = "", releaseNotes = "", releaseDate = "",
                     isForceUpdate = false, minRequiredVersion = 1)

        val result = repo.checkForUpdate(currentVersionCode = 14)

        assertTrue("fallback 成功应该 success", result.isSuccess)
        assertNotNull("应该返回新版本", result.getOrNull())
        assertEquals(15, result.getOrNull()?.versionCode)
        // 优先 master → 失败 → 走版本 URL,共 2 次调用
        coVerify(exactly = 2) { apiService.checkForUpdate(any()) }
    }

    @Test
    fun `BUG-R9 两个URL都404视为无更新而非错误`() = runBlocking {
        coEvery { apiService.checkForUpdate(any()) } throws http404()

        val result = repo.checkForUpdate(currentVersionCode = 14)

        assertTrue("两个 URL 都 404 应该 success (视为无更新),不是 failure",
                  result.isSuccess)
        assertNull("返回值应为 null (无更新)", result.getOrNull())
    }

    @Test
    fun `BUG-R9 500错误应该返回failure不再fallback`() = runBlocking {
        val http500 = HttpException(Response.error<Any>(500,
            "boom".toResponseBody("text/plain".toMediaTypeOrNull())))
        coEvery { apiService.checkForUpdate(any()) } throws http500

        val result = repo.checkForUpdate(currentVersionCode = 14)

        assertTrue("500 错误应返回 failure", result.isFailure)
        // 500 不应触发 fallback, 只调用 1 次
        coVerify(exactly = 1) { apiService.checkForUpdate(any()) }
    }

    @Test
    fun `BUG-R9 IOException网络错误返回failure`() = runBlocking {
        coEvery { apiService.checkForUpdate(any()) } throws IOException("network unreachable")

        val result = repo.checkForUpdate(currentVersionCode = 14)

        assertTrue("网络错误应返回 failure", result.isFailure)
        assertTrue("错误信息应包含'网络连接失败'",
                  (result.exceptionOrNull()?.message ?: "").contains("网络连接失败"))
    }

    @Test
    fun `正常情况 master URL有更新时直接返回`() = runBlocking {
        // 首选 master URL 即拿到新版本, 不应 fallback 到版本 URL
        coEvery { apiService.checkForUpdate(match { it == "https://shz.al/~csBabyLog" }) } returns
            OtaUpdate(versionCode = 15, versionName = "1.4.4", downloadUrl = "https://x.com/a.apk",
                     fileSize = 0L, md5 = "", releaseNotes = "", releaseDate = "",
                     isForceUpdate = false, minRequiredVersion = 1)

        val result = repo.checkForUpdate(currentVersionCode = 14)

        assertTrue(result.isSuccess)
        assertEquals(15, result.getOrNull()?.versionCode)
        // 不需要 fallback, 只调用 1 次
        coVerify(exactly = 1) { apiService.checkForUpdate(any()) }
    }

    @Test
    fun `正常情况 master URL返回同版本视为无更新`() = runBlocking {
        coEvery { apiService.checkForUpdate(any()) } returns
            OtaUpdate(versionCode = 14, versionName = "1.4.3", downloadUrl = "",
                     fileSize = 0L, md5 = "", releaseNotes = "", releaseDate = "",
                     isForceUpdate = false, minRequiredVersion = 1)

        val result = repo.checkForUpdate(currentVersionCode = 14)

        assertTrue(result.isSuccess)
        assertNull("同版本应返回 null", result.getOrNull())
    }

    private fun http404(): HttpException =
        HttpException(Response.error<Any>(404,
            "Not Found".toResponseBody("text/plain".toMediaTypeOrNull())))
}
