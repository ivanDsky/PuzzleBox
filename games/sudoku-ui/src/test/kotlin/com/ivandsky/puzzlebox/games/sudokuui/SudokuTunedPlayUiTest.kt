package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
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
import com.ivandsky.puzzlebox.gameui.LandscapeControls
import com.ivandsky.puzzlebox.gameui.LocalPlayTuning
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlayTuning
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

// nav-rethink B5: under a non-null PlayTuning the shared control row sits under the board and
// holds Undo, Redo, Hint and Clear; the keypad keeps the digits, Notes and Erase.
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

    private fun setScreen(controller: SudokuPlayController, layout: PlayLayout, tuning: PlayTuning? = PlayTuning.Starting) {
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayTuning provides tuning) {
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
    fun `two pane under the starting tuning keeps the row under the board and drops the toolbar`() {
        setScreen(adopted(), PlayLayout.TwoPane)

        val board = bounds("sudoku.board_area")
        val clear = bounds("play.clear")
        assertTrue("clear $clear under board $board", clear.top >= board.bottom - 1.dp)
        composeRule.onNodeWithTag("sudoku.clear").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `two pane with a side column stacks the controls beside the board`() {
        setScreen(adopted(), PlayLayout.TwoPane, PlayTuning.Starting.copy(landscapeControls = LandscapeControls.SideColumn))

        val board = bounds("sudoku.board_area")
        val undo = bounds("play.undo")
        val hint = bounds("play.hint")
        val clear = bounds("play.clear")
        assertTrue("controls $undo right of the board $board", undo.left >= board.right - 1.dp)
        assertTrue("undo above hint above clear", undo.top < hint.top && hint.top < clear.top)
        assertEquals(undo.left.value, clear.left.value, 1f)
    }

    @Test
    fun `without a tuning Clear stays in the keypad and the row is not there`() {
        setScreen(adopted(), PlayLayout.SinglePane, tuning = null)

        composeRule.onNode(hasTestTag("sudoku.clear") and hasAnyAncestor(hasTestTag("sudoku.keypad"))).assertExists()
        composeRule.onNodeWithTag("play.clear").assertDoesNotExist()
    }
}
