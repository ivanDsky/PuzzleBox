package com.ivandsky.puzzlebox.profile.settings

import android.app.Application
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
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

/** The reminder time picker dialog on its own: what Set and Cancel hand back, and that both are 48 dp targets. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-420dpi")
class ReminderTimeDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val set = mutableListOf<LocalTime>()
    private var dismissed = 0

    private fun show(initial: LocalTime) {
        composeRule.setContent {
            PuzzleBoxTheme { ReminderTimeDialog(initial = initial, onSet = { set += it }, onDismiss = { dismissed++ }) }
        }
    }

    @Test
    fun `Set returns the shown time when nothing was changed`() {
        show(LocalTime.of(19, 5))

        composeRule.onNodeWithTag("settings.reminders.dialog.set").performClick()

        assertEquals(listOf(LocalTime.of(19, 5)), set)
        assertEquals(0, dismissed)
    }

    @Test
    fun `Set returns the time the player picked on the dial`() {
        show(LocalTime.of(9, 30))

        composeRule.onNodeWithText("PM").performClick()
        composeRule.onNodeWithTag("settings.reminders.dialog.set").performClick()

        assertEquals(listOf(LocalTime.of(21, 30)), set)
    }

    @Test
    fun `Cancel returns nothing and only dismisses`() {
        show(LocalTime.of(19, 0))

        composeRule.onNodeWithText("PM").performClick()
        composeRule.onNodeWithTag("settings.reminders.dialog.cancel").performClick()

        assertEquals(emptyList<LocalTime>(), set)
        assertEquals(1, dismissed)
    }

    @Test
    fun `Cancel and Set are at least 48 dp`() {
        show(LocalTime.of(19, 0))

        listOf("settings.reminders.dialog.cancel", "settings.reminders.dialog.set").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-420dpi")
    fun `in landscape Set is on screen and still taps`() {
        show(LocalTime.of(19, 5))

        composeRule.onNodeWithTag("settings.reminders.dialog.set").assertIsDisplayed()
        val bottom = composeRule.onNodeWithTag("settings.reminders.dialog.set").getBoundsInRoot().bottom
        assertTrue("Set ends at $bottom, below the 411 dp screen", bottom <= 411.dp)
        composeRule.onNodeWithTag("settings.reminders.dialog.set").performClick()

        assertEquals(listOf(LocalTime.of(19, 5)), set)
    }

    @Test
    fun `a 24-hour device gets the 24-hour dial, without the AM and PM toggle`() {
        use24HourClock()
        show(LocalTime.of(19, 5))

        composeRule.onNodeWithText("PM").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.reminders.dialog.set").performClick()

        assertEquals(listOf(LocalTime.of(19, 5)), set)
    }
}
