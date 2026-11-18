package com.ivandsky.puzzlebox.profile.settings

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Reminders block's Time row: drawn only while the reminder is on, shows the stored time, opens the picker. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-420dpi")
class RemindersTimeRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val picked = mutableListOf<LocalTime>()

    private fun show(on: Boolean, time: LocalTime = LocalTime.of(19, 0)) {
        composeRule.setContent {
            PuzzleBoxTheme {
                RemindersSection(
                    state = RemindersState(on = on, time = time, blocked = false, askPermission = false),
                    onSetEnabled = {},
                    onOpenSettings = {},
                    onSetTime = { picked += it },
                )
            }
        }
    }

    @Test
    fun `the Time row is not drawn while the reminder is off`() {
        show(on = false)

        composeRule.onNodeWithTag("settings.reminders.time").assertDoesNotExist()
    }

    @Test
    fun `the Time row is drawn while the reminder is on, at least 48 dp, with the stored time in the short format`() {
        show(on = true, time = LocalTime.of(19, 0))

        composeRule.onNodeWithTag("settings.reminders.time").assertHeightIsAtLeast(48.dp)
        val said = composeRule.onNodeWithTag("settings.reminders.time").fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf("Time", "7:00 PM"), said.map { it.replace('\u202f', ' ').replace('\u00a0', ' ') })
        composeRule.assertTouchTargetsAtLeast48()
    }

    @Test
    fun `tapping the row opens the picker, and Set reports the picked time and closes it`() {
        show(on = true, time = LocalTime.of(9, 30))

        composeRule.onNodeWithTag("settings.reminders.dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.reminders.time").performClick()
        composeRule.onNodeWithTag("settings.reminders.dialog").assertExists()
        composeRule.onNodeWithText("PM").performClick()
        composeRule.onNodeWithTag("settings.reminders.dialog.set").performClick()

        assertEquals(listOf(LocalTime.of(21, 30)), picked)
        composeRule.onNodeWithTag("settings.reminders.dialog").assertDoesNotExist()
    }

    @Test
    fun `Cancel closes the picker and reports nothing`() {
        show(on = true)

        composeRule.onNodeWithTag("settings.reminders.time").performClick()
        composeRule.onNodeWithTag("settings.reminders.dialog.cancel").performClick()

        assertTrue(picked.isEmpty())
        composeRule.onNodeWithTag("settings.reminders.dialog").assertDoesNotExist()
    }

    @Test
    fun `a 24-hour device reads the stored time as 19 colon 00`() {
        use24HourClock()
        show(on = true, time = LocalTime.of(19, 0))

        val said = composeRule.onNodeWithTag("settings.reminders.time").fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf("Time", "19:00"), said)
    }
}
