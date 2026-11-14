package com.ivandsky.puzzlebox.play.linkedin

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.play.GOLDEN_PHONE
import com.ivandsky.puzzlebox.play.settleAndCapture
import com.ivandsky.puzzlebox.play.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The day card's saved-board line on the golden phone, light and dark: a Final board, a Partial one, a day never saved,
 * and the settled restoring skeleton (a day still inside the window, and one past it) while the first cloud restore
 * runs. Goldens in `snapshots/saved-board/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class SavedBoardLineGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `final light`() = capture("final", dark = false) { SavedBoardLineFinalPreview() }
    @Test fun `final dark`() = capture("final", dark = true) { SavedBoardLineFinalPreview() }

    @Test fun `partial light`() = capture("partial", dark = false) { SavedBoardLinePartialPreview() }
    @Test fun `partial dark`() = capture("partial", dark = true) { SavedBoardLinePartialPreview() }

    @Test fun `never saved light`() = capture("never-saved", dark = false) { SavedBoardLineNeverSavedPreview() }
    @Test fun `never saved dark`() = capture("never-saved", dark = true) { SavedBoardLineNeverSavedPreview() }

    @Test fun `restoring light`() = capture("restoring", dark = false) { SavedBoardLineRestoringPreview() }
    @Test fun `restoring dark`() = capture("restoring", dark = true) { SavedBoardLineRestoringPreview() }

    @Test fun `restoring past the window light`() = capture("restoring-past-window", dark = false) { SavedBoardLineRestoringPastWindowPreview() }
    @Test fun `restoring past the window dark`() = capture("restoring-past-window", dark = true) { SavedBoardLineRestoringPastWindowPreview() }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        rule.showGolden(dark, content = content)
        rule.settleAndCapture("saved-board", name, "phone", dark)
    }
}
