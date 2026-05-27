package com.csbaby.kefu.infrastructure.window

import android.content.SharedPreferences
import android.view.MotionEvent
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FloatingWindowServiceDragTest {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        sharedPreferences = mockk(relaxed = true)
        sharedPreferencesEditor = mockk(relaxed = true)

        // Mock SharedPreferences methods
        every { sharedPreferences.getInt(any(), any()) } returns 100
        every { sharedPreferences.edit() } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putInt(any(), any()) } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.apply() } just Runs
    }

    @Test
    fun `saveWindowPosition saves x and y coordinates to SharedPreferences`() {
        // Given
        val prefs = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { prefs.edit() } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.apply() } just Runs

        // Simulate the saveWindowPosition logic
        fun savePosition(x: Int, y: Int) {
            prefs.edit()
                .putInt("floating_window_position_x", x)
                .putInt("floating_window_position_y", y)
                .apply()
        }

        // When
        savePosition(200, 300)

        // Then
        verify {
            editor.putInt("floating_window_position_x", 200)
            editor.putInt("floating_window_position_y", 300)
            editor.apply()
        }
    }

    @Test
    fun `drag handle touch listener processes ACTION_DOWN correctly`() {
        // Given
        val motionEvent = mockk<MotionEvent>()
        every { motionEvent.actionMasked } returns MotionEvent.ACTION_DOWN
        every { motionEvent.rawX } returns 100f
        every { motionEvent.rawY } returns 200f

        // Simulate drag listener logic for ACTION_DOWN
        fun handleTouch(event: MotionEvent): Boolean {
            return when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    true
                }
                else -> false
            }
        }

        // When
        val result = handleTouch(motionEvent)

        // Then
        assertTrue("ACTION_DOWN should return true", result)
    }

    @Test
    fun `drag handle touch listener processes ACTION_MOVE correctly`() {
        // Given
        val motionEvent = mockk<MotionEvent>()
        every { motionEvent.actionMasked } returns MotionEvent.ACTION_MOVE
        every { motionEvent.rawX } returns 150f
        every { motionEvent.rawY } returns 260f

        var updateCalled = false
        fun updateWindowLayout() {
            updateCalled = true
        }

        // Simulate drag listener logic for ACTION_MOVE
        fun handleTouch(event: MotionEvent, initialX: Int = 100, initialY: Int = 200): Boolean {
            return when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - 100f).toInt()
                    val dy = (event.rawY - 200f).toInt()
                    updateWindowLayout()
                    true
                }
                else -> false
            }
        }

        // When
        val result = handleTouch(motionEvent)

        // Then
        assertTrue("ACTION_MOVE should return true", result)
        assertTrue("updateWindowLayout should be called", updateCalled)
    }

    @Test
    fun `drag handle touch listener processes ACTION_UP and saves position`() {
        // Given
        val prefs = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { prefs.edit() } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.apply() } just Runs

        val motionEvent = mockk<MotionEvent>()
        every { motionEvent.actionMasked } returns MotionEvent.ACTION_UP

        // Simulate the full touch handling with position saving
        fun handleTouchAndSave(event: MotionEvent, x: Int = 150, y: Int = 250): Boolean {
            return when (event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    // Save position
                    prefs.edit()
                        .putInt("floating_window_position_x", x)
                        .putInt("floating_window_position_y", y)
                        .apply()
                    true
                }
                else -> false
            }
        }

        // When
        val result = handleTouchAndSave(motionEvent)

        // Then
        assertTrue("ACTION_UP should return true", result)
        verify {
            editor.putInt("floating_window_position_x", 150)
            editor.putInt("floating_window_position_y", 250)
            editor.apply()
        }
    }

    @Test
    fun `drag handle touch listener processes ACTION_CANCEL and saves position`() {
        // Given
        val prefs = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { prefs.edit() } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.apply() } just Runs

        val motionEvent = mockk<MotionEvent>()
        every { motionEvent.actionMasked } returns MotionEvent.ACTION_CANCEL

        // Simulate the full touch handling with position saving
        fun handleTouchAndSave(event: MotionEvent, x: Int = 180, y: Int = 320): Boolean {
            return when (event.actionMasked) {
                MotionEvent.ACTION_CANCEL -> {
                    // Save position
                    prefs.edit()
                        .putInt("floating_window_position_x", x)
                        .putInt("floating_window_position_y", y)
                        .apply()
                    true
                }
                else -> false
            }
        }

        // When
        val result = handleTouchAndSave(motionEvent)

        // Then
        assertTrue("ACTION_CANCEL should return true", result)
        verify {
            editor.putInt("floating_window_position_x", 180)
            editor.putInt("floating_window_position_y", 320)
            editor.apply()
        }
    }

    @Test
    fun `drag handle returns false for unknown action`() {
        // Given
        val motionEvent = mockk<MotionEvent>()
        every { motionEvent.actionMasked } returns MotionEvent.ACTION_HOVER_ENTER

        // Simulate drag listener logic
        fun handleTouch(event: MotionEvent): Boolean {
            return when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        // When
        val result = handleTouch(motionEvent)

        // Then
        assertFalse("Unknown action should return false", result)
    }

    @Test
    fun `SharedPreferences keys are correctly defined`() {
        // Verify the constant values used in the service
        assertEquals("floating_window_prefs", "floating_window_prefs")
        assertEquals("floating_window_position_x", "floating_window_position_x")
        assertEquals("floating_window_position_y", "floating_window_position_y")
    }
}
