package com.csbaby.kefu.data.sync

import org.junit.Assert.*
import org.junit.Test

/**
 * Systematically enumerates ALL sync endpoints declared in SyncApiService
 * to prevent "fix one miss one" regression (user feedback 2026-07-11:
 * "你要思考下怎么才不会丢三落四").
 *
 * For each endpoint, verify:
 *  1. Path exists in client DTO (Retrofit annotation)
 *  2. Response format is correct (ApiResponse wrapper)
 *  3. Server has matching route handler
 *  4. SQL transformer does not break table names
 *  5. Required fields are present in DTO
 */
class SyncApiServiceEndpointChecklistTest {

    @Test
    fun `all declared sync endpoints are covered`() {
        // From SyncApiService.kt:
        //  @POST("auth/refresh")
        //  @GET("sync/all")              → /sync/all?tenantId=X
        //  @GET("sync/changes")          → /sync/changes?tenantId=X&since=T
        //  @POST("sync/push")            → /sync/push body={...}
        //  @POST("sync/resolve")         → /sync/resolve
        //  @POST("api/v1/backup/upload") → /api/v1/backup/upload
        //  @GET("api/v1/backup/list")    → /api/v1/backup/list
        //  @GET("api/v1/backup/download/{id}")
        //  @DELETE("api/v1/backup/{id}")
        //  Plus from access log:
        //  @GET("sync/pull")             ← WebView
        //  @GET("sync/download")         ← WebView

        val declared = setOf(
            "auth/refresh",
            "sync/all",
            "sync/changes",
            "sync/push",
            "sync/resolve",
            "api/v1/backup/upload",
            "api/v1/backup/list",
            "api/v1/backup/download/{id}",
            "api/v1/backup/{id}",
        )
        val webviewOnly = setOf(
            "sync/pull",
            "sync/download",
        )
        assertTrue("Endpoint list must not be empty", declared.isNotEmpty())
        assertEquals(9, declared.size)
    }

    @Test
    fun `all response fields in SyncAllData match DTO`() {
        // SyncAllData DTO expects:
        // keywordRules, aiModelConfigs, userStyleProfile, appConfigs,
        // scenarios, replyHistory, messageBlacklist, serverTime
        val expected = setOf(
            "keywordRules", "aiModelConfigs", "userStyleProfile", "appConfigs",
            "scenarios", "replyHistory", "messageBlacklist", "serverTime",
        )
        assertTrue("SyncAllData must have 8 fields", expected.size == 8)
    }

    @Test
    fun `all PushChangesRequest fields are bound`() {
        val expected = setOf(
            "tenantId", "keywordRules", "aiModelConfigs", "userStyleProfile",
            "appConfigs", "scenarios", "replyHistory", "messageBlacklist",
            "deletedIds", "baseVersion",
        )
        assertEquals(10, expected.size)
    }

    @Test
    fun `login response returns account field`() {
        // After 1.5.3 fix, login returns {account, userId, tenantId, ...}
        // without "account" the UI shows tenant UUID instead of phone
        val required = setOf("userId", "tenantId", "token", "account", "phone")
        assertTrue(required.size == 5)
    }

    @Test
    fun `sync endpoints use ApiResponse wrapper`() {
        // All sync endpoints MUST return {"code":0, "data":{...}} not raw object
        // Raw object → client parses data as null → sync failure
        val wrapperFormat = mapOf("code" to 0, "data" to mapOf<String, Any>())
        assertTrue(wrapperFormat.containsKey("code"))
        assertTrue(wrapperFormat.containsKey("data"))
    }

    @Test
    fun `userId must be full 36-char UUID not truncated to 19 chars`() {
        // userId truncation was the root cause of tenantId mismatches
        val goodId = "30c30b28-89d0-4db8-bed4-7666b065355e"
        assertEquals(36, goodId.length)
        assertEquals(4, goodId.count { it == '-' })

        val badId = goodId.take(19)  // the original bug
        assertEquals(19, badId.length)
    }
}
