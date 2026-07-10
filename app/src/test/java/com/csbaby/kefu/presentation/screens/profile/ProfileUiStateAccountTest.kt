package com.csbaby.kefu.presentation.screens.profile

import org.junit.Assert.*
import org.junit.Test

/**
 * Regression tests for [ProfileUiState.account] — ensures the UI exposes the
 * logged-in user's account (phone/email) so the screen can render
 * "账号: 15088670554" instead of "租户: <uuid>".
 *
 * Bug history: prior to this fix, ProfileUiState had only `currentTenantId`
 * but no `currentAccount`, so SyncSettingsCard displayed the internal ID.
 */
class ProfileUiStateAccountTest {

    @Test
    fun `ProfileUiState has currentAccount field for UI display`() {
        val ui = ProfileUiState(
            isLoggedIn = true,
            currentTenantId = "30c30b28-89d0-4db8-",
            currentAccount = "15088670554"
        )

        assertTrue(ui.isLoggedIn)
        assertEquals("30c30b28-89d0-4db8-", ui.currentTenantId)
        assertEquals("15088670554", ui.currentAccount)
    }

    @Test
    fun `currentAccount is null when not logged in`() {
        val ui = ProfileUiState(isLoggedIn = false)
        assertFalse(ui.isLoggedIn)
        assertNull(ui.currentAccount)
        assertNull(ui.currentTenantId)
    }

    @Test
    fun `currentAccount preserves value through state copy`() {
        val ui = ProfileUiState(
            isLoggedIn = true,
            currentAccount = "test@example.com"
        )
        val updated = ui.copy(lastSyncTime = 12345L)
        assertEquals("test@example.com", updated.currentAccount)
        assertEquals(12345L, updated.lastSyncTime)
    }
}
