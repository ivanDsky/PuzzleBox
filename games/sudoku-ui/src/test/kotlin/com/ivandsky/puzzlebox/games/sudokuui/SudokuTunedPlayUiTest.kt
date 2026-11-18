package com.ivandsky.puzzlebox.games.sudokuui

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// nav-rethink B5: the shared control row sits under the board and holds Undo, Redo, Hint and
// Clear; the keypad keeps the digits, a corner Notes toggle and, on a phone, Erase (B5; ticket 57b).
// Two panes stack the row, with Erase, in a column beside the board.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
class SudokuTunedPlayUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val givens = grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3..")
    private val solution = grid("463251 521436 142563 356142 234615 615324")
    private val puzzle = SudokuPuzzle(SudokuGames.MINI.variant, givens, solution)
    private val ref = SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Daily(LocalDate.of(2026, 11, 4), 450))
    private val ui = SudokuPlayUi(SudokuGames.MINI, InMemorySudokuHighlightSettings())

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    private fun adopted(): SudokuPlayController {
        val controller = ui.createController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})) as SudokuPlayController
        val envelope = Puzzle(ui.gameId, puzzle.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(puzzle))
        assertEquals(Adopt.Adopted, controller.adoptToday(envelope, ref))
        return controller
    }

    private fun setScreen(controller: SudokuPlayController, layout: PlayLayout) {
        composeRule.setContent {
            PuzzleBoxTheme {
                ui.Screen(
                    controller = controller,
                    status = PlayBoardStatus.Ready,
                    layout = layout,
                    sidePanel = null,
                    onRetry = {},
                    onSolved = { _, _ -> },
                    modifier = Modifier,
                )
            }
        }
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun `Clear leaves the keypad for the control row, after Hint`() {
        setScreen(adopted(), PlayLayout.SinglePane)

        composeRule.onNodeWithTag("sudoku.clear").assertDoesNotExist()
        val clear = bounds("play.clear")
        val hint = bounds("play.hint")
        assertTrue("hint $hint before clear $clear", hint.right <= clear.left)
        assertTrue("one row", kotlin.math.abs(hint.top.value - clear.top.value) < 1f)
    }

    @Test
    fun `the control row sits directly under the keypad, no wider than the grid`() {
        setScreen(adopted(), PlayLayout.SinglePane)

        val keypad = bounds("sudoku.keypad")
        val grid = bounds("sudoku.grid")
        val bar = bounds("play.action_bar")
        assertTrue("bar $bar under keypad $keypad", bar.top >= keypad.bottom)
        assertTrue("bar $bar a board pane's inset or two under keypad $keypad", bar.top - keypad.bottom <= 24.dp)
        assertTrue("bar ${bar.right - bar.left} wider than grid ${grid.right - grid.left}", (bar.right - bar.left).value <= (grid.right - grid.left).value + 1f)
    }

    @Test
    fun `every key and control holds the 48dp touch floor on a phone`() {
        setScreen(adopted(), PlayLayout.SinglePane)

        for (tag in listOf("play.undo", "play.redo", "play.hint", "play.clear")) {
            val b = bounds(tag)
            assertTrue("$tag ${b.right - b.left}x${b.bottom - b.top}", (b.bottom - b.top).value >= 47.5f && (b.right - b.left).value >= 47.5f)
        }
    }

    @Test
    fun `the Clear in the control row clears the selected cell`() {
        val controller = adopted()
        setScreen(controller, PlayLayout.SinglePane)

        composeRule.onNodeWithTag("sudoku.cell.0").performClick()
        composeRule.runOnIdle { controller.board!!.input(4) }
        composeRule.onNodeWithTag("play.clear").performClick()

        composeRule.runOnIdle { assertEquals(SudokuCell.Empty, controller.board!!.session.board.cells[0]) }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `two pane stacks the controls beside the board`() {
        setScreen(adopted(), PlayLayout.TwoPane)

        val board = bounds("sudoku.board_area")
        val undo = bounds("play.undo")
        val hint = bounds("play.hint")
        val clear = bounds("play.clear")
        assertTrue("controls $undo right of the board $board", undo.left >= board.right - 1.dp)
        assertTrue("undo above hint above clear", undo.top < hint.top && hint.top < clear.top)
        assertEquals(undo.left.value, clear.left.value, 1f)
    }

    private fun digitKey(digit: Int) = composeRule.onNode(hasContentDescription("$digit, ", substring = true) and hasAnyAncestor(hasTestTag("sudoku.keypad")))

    private fun digitFontStyles(): List<FontStyle?> =
        composeRule.onAllNodesWithTag("sudoku.digit_face", useUnmergedTree = true).fetchSemanticsNodes().map { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
            results.single().layoutInput.style.fontStyle
        }

    private fun enterFour(controller: SudokuPlayController) {
        composeRule.onNodeWithTag("sudoku.cell.0").performClick()
        composeRule.runOnIdle { controller.board!!.input(4) }
    }

    @Test
    fun `Notes is a corner toggle, 48dp or more, top right of the digits, and toggles notes`() {
        val controller = adopted()
        setScreen(controller, PlayLayout.SinglePane)

        val toggle = bounds("sudoku.notes_toggle")
        val keypad = bounds("sudoku.keypad")
        assertTrue("toggle $toggle", (toggle.right - toggle.left).value >= 47.5f && (toggle.bottom - toggle.top).value >= 47.5f)
        assertTrue("a small corner control, not a key: $toggle", (toggle.right - toggle.left).value < 64f)
        assertEquals("the pad's right edge", keypad.right.value, toggle.right.value, 1f)
        val three = digitKey(3).getUnclippedBoundsInRoot()
        assertTrue("toggle $toggle above the digits $three", toggle.bottom <= three.top + 1.dp)

        composeRule.onNodeWithTag("sudoku.notes_toggle").performClick()
        composeRule.runOnIdle { assertTrue(controller.board!!.session.notesMode) }
        composeRule.onNodeWithTag("sudoku.notes_toggle").performClick()
        composeRule.runOnIdle { assertTrue(!controller.board!!.session.notesMode) }
    }

    @Test
    fun `the digits take the pad's full width in three columns`() {
        setScreen(adopted(), PlayLayout.SinglePane)

        val keypad = bounds("sudoku.keypad")
        val one = digitKey(1).getUnclippedBoundsInRoot()
        val three = digitKey(3).getUnclippedBoundsInRoot()
        val four = digitKey(4).getUnclippedBoundsInRoot()
        assertEquals(keypad.left.value, one.left.value, 1f)
        assertEquals(keypad.right.value, three.right.value, 1f)
        assertEquals("three to a row", one.left.value, four.left.value, 1f)
        assertTrue("4 under 1", four.top > one.bottom - 1.dp)
    }

    @Test
    fun `on a phone Erase sits on the pad, 48dp or more, and erases the selected cell`() {
        val controller = adopted()
        setScreen(controller, PlayLayout.SinglePane)

        val erase = bounds("sudoku.erase")
        val one = digitKey(1).getUnclippedBoundsInRoot()
        assertTrue("erase $erase", (erase.right - erase.left).value >= 47.5f && (erase.bottom - erase.top).value >= 47.5f)
        assertTrue("erase $erase above the digits $one, in the pad's strip", erase.bottom <= one.top + 1.dp)
        enterFour(controller)
        composeRule.onNodeWithTag("sudoku.erase").performClick()

        composeRule.runOnIdle { assertEquals(SudokuCell.Empty, controller.board!!.session.board.cells[0]) }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `the side column holds Erase with the other controls and it erases the selected cell`() {
        val controller = adopted()
        setScreen(controller, PlayLayout.TwoPane)

        assertEquals("one Erase, in the column", 1, composeRule.onAllNodesWithTag("sudoku.erase").fetchSemanticsNodes().size)
        val board = bounds("sudoku.board_area")
        val erase = bounds("sudoku.erase")
        val hint = bounds("play.hint")
        val clear = bounds("play.clear")
        assertTrue("erase $erase right of the board $board", erase.left >= board.right - 1.dp)
        assertEquals(hint.left.value, erase.left.value, 1f)
        assertEquals(hint.right.value, erase.right.value, 1f)
        assertTrue("erase between hint and clear", hint.top < erase.top && erase.top < clear.top)

        enterFour(controller)
        composeRule.onNodeWithTag("sudoku.erase").performClick()

        composeRule.runOnIdle { assertEquals(SudokuCell.Empty, controller.board!!.session.board.cells[0]) }
        composeRule.onNodeWithTag("sudoku.notes_toggle").assertExists()
    }

    @Test
    fun `notes mode shows the large digit in italic, and turning it off restores the upright digit`() {
        setScreen(adopted(), PlayLayout.SinglePane)
        assertEquals(List(6) { false }, digitFontStyles().map { it == FontStyle.Italic })

        composeRule.onNodeWithTag("sudoku.notes_toggle").performClick()
        composeRule.waitForIdle()
        assertEquals(List(6) { true }, digitFontStyles().map { it == FontStyle.Italic })

        composeRule.onNodeWithTag("sudoku.notes_toggle").performClick()
        composeRule.waitForIdle()
        assertEquals(List(6) { false }, digitFontStyles().map { it == FontStyle.Italic })
    }

    @Test
    fun `the notes switch animates by default`() {
        composeRule.mainClock.autoAdvance = false
        setScreen(adopted(), PlayLayout.SinglePane)
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag("sudoku.notes_toggle").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals("two frames in, still upright", List(6) { false }, digitFontStyles().map { it == FontStyle.Italic })

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(List(6) { true }, digitFontStyles().map { it == FontStyle.Italic })
    }

    @Test
    fun `reduced motion makes the notes switch instant`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        composeRule.mainClock.autoAdvance = false
        setScreen(adopted(), PlayLayout.SinglePane)
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag("sudoku.notes_toggle").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()

        assertEquals("italic within two frames", List(6) { true }, digitFontStyles().map { it == FontStyle.Italic })
    }
}
