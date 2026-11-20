package com.ivandsky.puzzlebox.train.drill

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.train.round.RoundState
import com.ivandsky.puzzlebox.train.round.RoundSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DrillScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class PlayCall(val title: String, val label: String, val layout: PlayLayout, val onBack: () -> Unit, val onSolved: () -> Unit)

    private var call: PlayCall? = null
    private var closed = 0
    private var solved = 0
    private var tryAgain = 0
    private var tryAnother = 0

    private val recordingPlay = DrillPlaySlot { _, _, title, label, layout, onBack, onSolved, _ ->
        call = PlayCall(title, label, layout, onBack, onSolved)
    }

    private fun show(state: DrillState) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                DrillScreen(
                    name = "Line lock",
                    state = state,
                    showBack = true,
                    onClose = { closed++ },
                    play = recordingPlay,
                    onSolved = { solved++ },
                    onTryAgain = { tryAgain++ },
                    onTryAnother = { tryAnother++ },
                )
            }
        }
    }

    /**
     * Catches a drill that is not drawn by the app's play slot, one that titles itself with anything but the technique and
     * "Practice puzzle", one laid out for a side panel the pane has no room for, and a back or solved report that is not
     * the slot's own.
     */
    @Test
    fun `a playing drill hands the technique, the practice label and a single pane to the slot`() {
        show(standInPlaying)

        val played = call!!
        assertEquals("Line lock", played.title)
        assertEquals("Practice puzzle", played.label)
        assertEquals(PlayLayout.SinglePane, played.layout)
        played.onBack()
        played.onSolved()
        assertEquals(1, closed)
        assertEquals(1, solved)
    }

    /** Catches a solved page with no way back to the technique list, and one that would count as a second solve. */
    @Test
    fun `the solved page names the solve and goes back to the techniques`() {
        show(DrillState.Solved)

        composeRule.onNodeWithTag("train.drill.solved").assertIsDisplayed()
        composeRule.onNodeWithText("Solved").assertIsDisplayed()
        composeRule.onNodeWithText("Back to techniques").performClick()

        assertEquals(1, closed)
        assertEquals(0, solved)
        assertNull(call)
    }

    /** Catches the needs-a-connection page wired to the can't-load action, which would open a puzzle instead of fetching one. */
    @Test
    fun `the needs-a-connection page's action fetches`() {
        show(DrillState.NeedsConnection)
        composeRule.onNodeWithTag("train.drill").assertIsDisplayed()
        composeRule.onNodeWithTag("train.needsConnection").assertIsDisplayed()

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(1, tryAgain)
        assertEquals(0, tryAnother)
    }

    /** Catches the can't-load page wired to the fetch action, which would ask the network again when a puzzle is already on the device. */
    @Test
    fun `the can't-load page's action opens one on the device`() {
        show(DrillState.CantLoad(DrillCandidate.Archive(number = 918, targetSteps = 4)))
        composeRule.onNodeWithTag("train.cantLoad").assertIsDisplayed()

        composeRule.onNodeWithText("Try another puzzle").performClick()

        assertEquals(1, tryAnother)
        assertEquals(0, tryAgain)
    }

    /** Catches a round summary with no way into the drill, and a button that closes instead. */
    @Test
    fun `the round summary offers Play a full puzzle and its tap opens the drill`() {
        var drills = 0
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                RoundSummary(RoundState.Summary(right = 4, total = 5), onClose = { closed++ }, onDrill = { drills++ })
            }
        }

        composeRule.onNodeWithTag("train.playPuzzle").assertIsDisplayed()
        composeRule.onNodeWithText("Play a full puzzle").performClick()

        assertEquals(1, drills)
        assertEquals(0, closed)
    }
}
