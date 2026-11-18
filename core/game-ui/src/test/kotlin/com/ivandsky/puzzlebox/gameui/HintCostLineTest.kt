package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A11: the first applied hint says hints are counted on the result, once, on a route that offers it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HintCostLineTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** What a game's hint does: it counts and explains only when it applies; a refused press does neither. */
    private class Hints : PlayHints {
        override var hintsUsed by mutableIntStateOf(0)
        override var hintEnabled by mutableStateOf(true)
        override var hintCaption: String? by mutableStateOf(null)
        var explanation: String? = "Fixed your path up to 3"
        var applies = true

        override fun requestHint() {
            if (hintEnabled && applies) applyHint(explanation)
        }

        fun applyHint(text: String?) {
            hintsUsed++
            hintCaption = text
        }
    }

    private object NoUndo : PlayUndo {
        override val canUndo = false
        override val canRedo = false
        override fun undo() = Unit
        override fun redo() = Unit
    }

    private var seen by mutableStateOf(false)
    private var inputs by mutableIntStateOf(0)
    private var marks = 0
    private val hints = Hints()

    private fun text(text: String) = composeRule.onAllNodesWithText(text, useUnmergedTree = true)

    private fun shown(text: String) = text(text).onLast().assertIsDisplayed()

    private fun gone(text: String) = text(text).assertCountEquals(0)

    private fun show(
        offered: Boolean = true,
        enabled: Boolean = true,
        withActionBar: Boolean = false,
        cooldown: Boolean = false,
    ) {
        hints.hintEnabled = enabled
        composeRule.setContent {
            val line = HintCostLine(seen = { seen }, inputs = { inputs }, markSeen = { marks++; seen = true })
            CompositionLocalProvider(
                LocalHintCostLine provides line.takeIf { offered },
                LocalHintCooldown provides HINT_COOLDOWN.takeIf { cooldown },
            ) {
                PuzzleBoxTheme(darkTheme = false) {
                    Column(Modifier.requiredSize(width = 360.dp, height = 400.dp)) {
                        PlayChrome(clock = null, hints = hints)
                        if (withActionBar) PlayActionBar(NoUndo, hints, clear = null, accent = AccentSlot.Teal)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the first hint shows the line after its explanation, and the next input clears both`() {
        show()
        hints.applyHint("Started your path over")
        composeRule.waitForIdle()
        shown("Started your path over")
        shown(HINT_COST_TEXT)
        assertEquals(1, marks)

        inputs++
        hints.hintCaption = null // the move retires the hint's caption
        composeRule.waitForIdle()
        gone(HINT_COST_TEXT)
    }

    @Test
    fun `a hint with no explanation shows the line alone until the next input`() {
        show()
        hints.applyHint(null)
        composeRule.waitForIdle()
        shown(HINT_COST_TEXT)
        assertEquals(1, marks)
        composeRule.mainClock.advanceTimeBy(60_000)
        shown(HINT_COST_TEXT) // no timer: it waits for the player

        inputs++
        composeRule.waitForIdle()
        gone(HINT_COST_TEXT)
    }

    @Test
    fun `the second hint does not show it again`() {
        show()
        hints.applyHint("one")
        composeRule.waitForIdle()
        inputs++
        hints.hintCaption = null
        composeRule.waitForIdle()
        hints.applyHint("two")
        composeRule.waitForIdle()
        shown("two")
        gone(HINT_COST_TEXT)
        assertEquals(1, marks)
    }

    @Test
    fun `a saved game restored with hints used never shows it or marks it seen`() {
        show(enabled = false) // the board is still loading: nothing loaded, so no hint is enabled
        Snapshot.withMutableSnapshot { // the restore lands: the session's hints and an enabled Hint at once
            hints.hintsUsed = 2
            hints.hintEnabled = true
        }
        composeRule.waitForIdle()
        gone(HINT_COST_TEXT)
        assertEquals(0, marks)

        hints.applyHint("a real hint") // the first hint the player applies still shows it
        composeRule.waitForIdle()
        shown(HINT_COST_TEXT)
        assertEquals(1, marks)
    }

    @Test
    fun `a restore after the line was seen never shows it again when the input count restarts`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            val line = HintCostLine(seen = { seen }, inputs = { inputs }, markSeen = { marks++; seen = true })
            CompositionLocalProvider(LocalHintCostLine provides line) {
                PuzzleBoxTheme(darkTheme = false) {
                    Column(Modifier.requiredSize(width = 360.dp, height = 400.dp)) { PlayChrome(clock = null, hints = hints) }
                }
            }
        }
        composeRule.waitForIdle()
        hints.applyHint(null) // armed at input 0, then the process dies with the line seen and the player idle
        composeRule.waitForIdle()
        shown(HINT_COST_TEXT)

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        gone(HINT_COST_TEXT) // the seen flag is persisted, the input count is back at 0 and the saved mark would match
        assertEquals(1, marks)
    }

    @Test
    fun `a tap on a disabled Hint consumes nothing, and the next enabled one shows it`() {
        show(enabled = false, withActionBar = true)
        composeRule.onNodeWithTag("play.hint").performClick()
        composeRule.waitForIdle()
        gone(HINT_COST_TEXT)
        assertEquals(0, marks)

        hints.hintEnabled = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("play.hint").performClick()
        composeRule.waitForIdle()
        shown(HINT_COST_TEXT)
        assertEquals(1, marks)
    }

    @Test
    fun `a tap swallowed by the cooldown consumes nothing`() {
        show(withActionBar = true, cooldown = true)
        composeRule.mainClock.autoAdvance = false // the wait must not run out between the two taps
        hints.applies = false // the first press applies nothing, and starts the wait
        composeRule.onNodeWithTag("play.hint").performClick()
        hints.applies = true
        composeRule.onNodeWithTag("play.hint").performClick() // inside the cooldown
        composeRule.mainClock.advanceTimeByFrame()
        gone(HINT_COST_TEXT)
        assertEquals(0, marks)
    }

    @Test
    fun `a route that offers none never shows it`() {
        show(offered = false)
        hints.applyHint("one")
        composeRule.waitForIdle()
        gone(HINT_COST_TEXT)
        assertEquals(0, marks)
    }

    @Test
    fun `a two-line explanation keeps both lines, the note waits and a later hint shows it`() {
        show()
        hints.applyHint(SUDOKU_LONGEST)
        composeRule.waitForIdle()
        shown(SUDOKU_LONGEST)
        gone(HINT_COST_TEXT)
        assertEquals(0, marks)
        assertEquals("the hint keeps both lines", 2, layoutOf(SUDOKU_LONGEST).lineCount)

        inputs++
        hints.hintCaption = null
        composeRule.waitForIdle()
        hints.applyHint("Fixed your path up to 3")
        composeRule.waitForIdle()
        shown(HINT_COST_TEXT)
        assertEquals(1, marks)
    }

    @Test
    fun `both lines show at 360 dp over the longest Zip explanation without moving the board`() {
        show()
        val slot = composeRule.onNodeWithTag("play.caption_slot")
        val reserved = slot.getBoundsInRoot()
        hints.applyHint(ZIP_LONGEST)
        composeRule.waitForIdle()
        val note = text(HINT_COST_TEXT).onLast().getBoundsInRoot()
        assertEquals("the slot keeps its height", reserved, slot.getBoundsInRoot())
        assertTrue("the cost line is the second line", note.top > reserved.top)
        shown(HINT_COST_TEXT)
        shown(ZIP_LONGEST)
    }

    private fun layoutOf(caption: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val node = text(caption).onLast().fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private companion object {
        const val ZIP_LONGEST = "Fixed your path up to 12"

        /** Sudoku's longest hint caption (a hidden single's reason), as `SudokuHintCaptions.place` words it. */
        const val SUDOKU_LONGEST =
            "After ruling out some numbers, row 9, column 9 already sees every other number in its row, " +
                "column and box, so the only number left for it is 9."
    }
}
