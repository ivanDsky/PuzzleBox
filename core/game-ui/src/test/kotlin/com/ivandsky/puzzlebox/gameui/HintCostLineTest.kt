package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A11: the first applied hint says hints are counted on the result, once, under PlayFeel and a route that offers it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HintCostLineTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class Hints : PlayHints {
        override var hintsUsed by mutableIntStateOf(0)
        override var hintEnabled by mutableStateOf(true)
        override var hintCaption: String? by mutableStateOf(null)
        override fun requestHint() = Unit

        /** What a game's hint does: a counted hint with its explanation. */
        fun applyHint(explanation: String?) {
            hintsUsed++
            hintCaption = explanation
        }
    }

    private var seen by mutableStateOf(false)
    private var marks = 0
    private val hints = Hints()

    /** The slot's merged node and its text child both carry a note-only caption, so a text is found by its drawn node. */
    private fun shown(text: String) = composeRule.onAllNodesWithText(text, useUnmergedTree = true).onLast().assertIsDisplayed()

    private fun gone(text: String) = composeRule.onAllNodesWithText(text, useUnmergedTree = true).assertCountEquals(0)

    private fun show(feel: Boolean = true, offered: Boolean = true, startUsed: Int = 0) {
        hints.hintsUsed = startUsed
        composeRule.setContent {
            val line = HintCostLine(seen = seen, markSeen = { marks++; seen = true }).takeIf { offered }
            CompositionLocalProvider(LocalPlayFeel provides feel, LocalHintCostLine provides line) {
                PuzzleBoxTheme(darkTheme = false) {
                    PlayChrome(clock = null, hints = hints, modifier = Modifier.requiredSize(width = 360.dp, height = 200.dp))
                }
            }
        }
    }

    @Test
    fun `the first hint shows the line after its explanation, and the next move clears both`() {
        show()
        hints.applyHint("Started your path over")
        composeRule.waitForIdle()
        shown("Started your path over")
        shown(HINT_COST_TEXT)
        assertEquals(1, marks)

        hints.hintCaption = null // a move retires the hint's caption
        composeRule.waitForIdle()
        gone(HINT_COST_TEXT)
    }

    @Test
    fun `a hint with no explanation shows the line alone, then lets it go on its own`() {
        show()
        hints.applyHint(null)
        composeRule.waitForIdle()
        shown(HINT_COST_TEXT)
        assertEquals(1, marks)
        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.waitForIdle()
        gone(HINT_COST_TEXT)
    }

    @Test
    fun `the second hint does not show it again`() {
        show()
        hints.applyHint("one")
        composeRule.waitForIdle()
        hints.hintCaption = null
        composeRule.waitForIdle()
        hints.applyHint("two")
        composeRule.waitForIdle()
        shown("two")
        gone(HINT_COST_TEXT)
        assertEquals(1, marks)
    }

    @Test
    fun `a tap that applies no hint consumes nothing`() {
        show()
        hints.hintEnabled = false // cooldown or a finished board: the tap raises no count
        hints.requestHint()
        composeRule.waitForIdle()
        gone(HINT_COST_TEXT)
        assertEquals(0, marks)
    }

    @Test
    fun `a restored session's earlier hints do not show it`() {
        show(startUsed = 2)
        composeRule.waitForIdle()
        gone(HINT_COST_TEXT)
        assertEquals(0, marks)
    }

    @Test
    fun `PlayFeel off never shows it`() {
        show(feel = false)
        hints.applyHint("one")
        composeRule.waitForIdle()
        gone(HINT_COST_TEXT)
        assertEquals(0, marks)
    }

    @Test
    fun `a route that offers none never shows it`() {
        show(offered = false)
        hints.applyHint("one")
        composeRule.waitForIdle()
        gone(HINT_COST_TEXT)
    }

    @Test
    fun `both lines show at 360 dp over the longest Zip explanation`() = bothLinesFit(ZIP_LONGEST)

    @Test
    fun `both lines show at 360 dp over the longest Sudoku explanation`() = bothLinesFit(SUDOKU_LONGEST)

    private fun bothLinesFit(explanation: String) {
        show()
        val slot = composeRule.onNodeWithTag("play.caption_slot")
        val reserved = slot.getBoundsInRoot()
        hints.applyHint(explanation)
        composeRule.waitForIdle()
        val note = composeRule.onAllNodesWithText(HINT_COST_TEXT, useUnmergedTree = true).onLast().getBoundsInRoot()
        assertEquals("the slot keeps its height", reserved, slot.getBoundsInRoot())
        assertTrue("the cost line is the second line", note.top > reserved.top)
        shown(HINT_COST_TEXT)
    }

    private companion object {
        const val ZIP_LONGEST = "Fixed your path up to 12"
        const val SUDOKU_LONGEST =
            "There's no simple step from here, so here's one cell to get you going: row 9, column 9 is 9."
    }
}
