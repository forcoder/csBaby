package com.csbaby.kefu.data.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Regression tests for [SyncAuthState] — ensures the human-readable `account`
 * field (phone/email) is preserved from login through persistence so UI
 * can display "账号: 13800138000" instead of "租户: 30c30b28-89d0-4db8-".
 *
 * Bug history: previously the UI showed `currentTenantId` because no
 * account field existed; the fix introduces `account` and updates
 * UI/AuthManager/SyncManager/SyncSettingsCard to use it.
 */
class SyncAuthStateAccountTest {

    @Test
    fun `fromLoginResponse preserves account for phone`() {
        val state = SyncAuthState.fromLoginResponse(
            userId = "30c30b28-89d0-4db8",
            tenantId = "30c30b28-89d0-4db8",
            token = "eyJ...",
            expiresAt = System.currentTimeMillis() + 3600_000L,
            account = "15088670554"
        )

        assertEquals("15088670554", state.account)
        assertEquals("30c30b28-89d0-4db8", state.userId)
        assertEquals("30c30b28-89d0-4db8", state.tenantId) // fallback to userId
    }

    @Test
    fun `fromLoginResponse preserves account for email`() {
        val state = SyncAuthState.fromLoginResponse(
            userId = "u-123",
            tenantId = "t-abc",
            token = "eyJ...",
            expiresAt = 0L,
            account = "test@example.com"
        )

        assertEquals("test@example.com", state.account)
        assertEquals("t-abc", state.tenantId)
    }

    @Test
    fun `account defaults to empty when omitted`() {
        // Backwards compat: legacy callers without account parameter
        val state = SyncAuthState.fromLoginResponse(
            userId = "u-1",
            tenantId = "t-1",
            token = "t",
            expiresAt = 0L
        )

        assertEquals("", state.account)
    }

    @Test
    fun `account survives round trip through equality`() {
        val s1 = SyncAuthState(
            userId = "u", tenantId = "t", accessToken = "tk",
            refreshToken = "rt", expiresAt = 999L, account = "15088670554"
        )
        val s2 = s1.copy()
        assertEquals("15088670554", s2.account)
        assertEquals(s1, s2)

        val s3 = s1.copy(account = "")
        assertNotEquals(s1, s3)
    }
}
