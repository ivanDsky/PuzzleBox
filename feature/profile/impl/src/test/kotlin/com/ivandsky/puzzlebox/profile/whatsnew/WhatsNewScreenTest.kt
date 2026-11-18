package com.ivandsky.puzzlebox.profile.whatsnew

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.profile.settings.assertTouchTargetsAtLeast48
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The What's new page: a card per release (version, date, bullets), the load-failed line, Back and 48 dp targets. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class WhatsNewScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var backClicks = 0

    private val two = WhatsNewUiState(
        listOf(
            WhatsNewCard("0.36.2", "18 Nov 2026", listOf("First note", "Second note")),
            WhatsNewCard("0.36.1", "17 Nov 2026", listOf("Older note")),
        ),
    )

    private fun show(state: WhatsNewUiState) {
        composeRule.setContent { PuzzleBoxTheme { WhatsNewScreen(state = state, onBack = { backClicks++ }) } }
    }

    @Test
    fun `each release shows its version, date and bullets, newest first`() {
        show(two)

        composeRule.onNodeWithText("What's new").assertExists()
        composeRule.onNodeWithText("0.36.2").assertExists()
        composeRule.onNodeWithText("18 Nov 2026").assertExists()
        composeRule.onNodeWithText("First note").assertExists()
        composeRule.onNodeWithText("Second note").assertExists()
        composeRule.onNodeWithText("Older note").assertExists()
        composeRule.onNodeWithTag("whatsNew.failed").assertDoesNotExist()
        val newer = composeRule.onNodeWithTag("whatsNew.release.0.36.2").fetchSemanticsNode().positionInRoot.y
        val older = composeRule.onNodeWithTag("whatsNew.release.0.36.1").fetchSemanticsNode().positionInRoot.y
        assertTrue("0.36.2 at $newer must sit above 0.36.1 at $older", newer < older)
    }

    @Test
    fun `a failed load says so in plain words and lists no release`() {
        show(WhatsNewUiState(loadFailed = true))

        composeRule.onNodeWithText("What's new could not load.").assertExists()
        composeRule.onNodeWithTag("whatsNew.release.0.36.2").assertDoesNotExist()
    }

    @Test
    fun `the back icon calls back and every touch target is at least 48 dp`() {
        show(two)

        composeRule.assertTouchTargetsAtLeast48()
        composeRule.onNodeWithTag("whatsNew.back").performClick()

        assertEquals(1, backClicks)
    }
}
