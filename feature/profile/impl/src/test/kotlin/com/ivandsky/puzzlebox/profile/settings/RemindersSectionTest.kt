package com.ivandsky.puzzlebox.profile.settings

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Settings Reminders block: 48 dp touch targets, and its taps reach the callbacks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-420dpi")
class RemindersSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(on: Boolean, blocked: Boolean, onSetEnabled: (Boolean) -> Unit = {}, onOpenSettings: () -> Unit = {}) {
        composeRule.setContent {
            PuzzleBoxTheme {
                RemindersSection(
                    state = RemindersState(on = on, time = LocalTime.of(19, 0), blocked = blocked, askPermission = false),
                    onSetEnabled = onSetEnabled,
                    onOpenSettings = onOpenSettings,
                    onSetTime = {},
                )
            }
        }
    }

    @Test
    fun `the switch row is a 48 dp touch target with the switch on`() {
        show(on = true, blocked = false)

        composeRule.onNodeWithTag("settings.reminders.switch").assertExists()
        composeRule.assertTouchTargetsAtLeast48()
    }

    @Test
    fun `the switch row is a 48 dp touch target with the switch off`() {
        show(on = false, blocked = false)

        composeRule.assertTouchTargetsAtLeast48()
    }

    @Test
    fun `the blocked line and its Open settings button are 48 dp touch targets`() {
        show(on = false, blocked = true)

        composeRule.onNodeWithTag("settings.reminders.blocked").assertExists()
        composeRule.onNodeWithTag("settings.reminders.opensettings").assertExists()
        composeRule.assertTouchTargetsAtLeast48()
    }

    @Test
    fun `the blocked line is absent when notifications are not blocked`() {
        show(on = true, blocked = false)

        composeRule.onNodeWithTag("settings.reminders.blocked").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.reminders.opensettings").assertDoesNotExist()
    }

    @Test
    fun `tapping the row reports the new value and Open settings is reported`() {
        val set = mutableListOf<Boolean>()
        var opened = 0
        show(on = false, blocked = true, onSetEnabled = { set += it }, onOpenSettings = { opened++ })

        composeRule.onNodeWithTag("settings.reminders.switch").performClick()
        composeRule.onNodeWithTag("settings.reminders.opensettings").performClick()

        assertEquals(listOf(true), set)
        assertEquals(1, opened)
    }
}
