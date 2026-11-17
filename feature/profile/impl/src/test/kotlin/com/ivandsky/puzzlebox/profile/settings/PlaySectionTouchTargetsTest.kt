package com.ivandsky.puzzlebox.profile.settings

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Settings Play row (the time-left switch for duelling yourself) is a 48 dp touch target, on and off. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-420dpi")
class PlaySectionTouchTargetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the time-left row is a 48 dp touch target with the switch on`() {
        composeRule.setContent { PuzzleBoxTheme { PlaySection(timeLeft = true, onSetTimeLeft = {}) } }

        composeRule.onNodeWithTag("settings.play.timeleft").assertExists()
        composeRule.assertTouchTargetsAtLeast48()
    }

    @Test
    fun `the time-left row is a 48 dp touch target with the switch off`() {
        composeRule.setContent { PuzzleBoxTheme { PlaySection(timeLeft = false, onSetTimeLeft = {}) } }

        composeRule.assertTouchTargetsAtLeast48()
    }
}
