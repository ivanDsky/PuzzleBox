package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuSamples
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.games.sudoku.digit
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuPlayUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val givens = grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3..")
    private val solution = grid("463251 521436 142563 356142 234615 615324")
    private val peanut450 = SudokuPuzzle(SudokuGames.MINI.variant, givens, solution)
    private val ref = SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Daily(LocalDate.of(2026, 11, 4), 450))
    private val ui = SudokuPlayUi(SudokuGames.MINI, InMemorySudokuHighlightSettings())
    private val solved = ArrayList<Pair<Long, Int>>()

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    private fun adopted(game: SudokuPlayUi = ui, puzzle: SudokuPuzzle = peanut450, puzzleRef: SudokuPuzzleRef = ref): SudokuPlayController {
        val controller = game.createController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})) as SudokuPlayController
        val envelope = Puzzle(game.gameId, puzzle.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(puzzle))
        assertEquals(Adopt.Adopted, controller.adoptToday(envelope, puzzleRef))
        return controller
    }

    private fun setScreen(controller: SudokuPlayController, layout: PlayLayout, game: SudokuPlayUi = ui) {
        composeRule.setContent {
            PuzzleBoxTheme {
                game.Screen(
                    controller = controller,
                    status = PlayBoardStatus.Ready,
                    layout = layout,
                    sidePanel = null,
                    onRetry = {},
                    onSolved = { time, hints -> solved += time to hints },
                    modifier = Modifier,
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-mdpi")
    fun `the 6x6 fits a 360x640 phone with grid, keypad and chrome apart`() {
        assertFitsSinglePane(ui, adopted(), size = 6)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-mdpi")
    fun `the 9x9 fits a 360x640 phone with grid, keypad and chrome apart`() {
        val classic = SudokuPlayUi(SudokuGames.CLASSIC, InMemorySudokuHighlightSettings())
        assertFitsSinglePane(classic, adopted(classic, SudokuSamples.classic, SudokuPuzzleRef(SudokuGames.CLASSIC.id, PuzzleKey.Daily((ref.key as PuzzleKey.Daily).date, null))), size = 9)
    }

    @Test
    fun `the 6x6 cell takes all the height the 411x891 control row leaves`() {
        assertFitsSinglePane(ui, adopted(), size = 6)
    }

    @Test
    fun `the 9x9 cell takes all the height the 411x891 control row leaves`() {
        val classic = SudokuPlayUi(SudokuGames.CLASSIC, InMemorySudokuHighlightSettings())
        assertFitsSinglePane(classic, adopted(classic, SudokuSamples.classic, SudokuPuzzleRef(SudokuGames.CLASSIC.id, PuzzleKey.Daily((ref.key as PuzzleKey.Daily).date, null))), size = 9)
    }

    /** Grid, keypad and control row stack apart on screen, the row as the last thing on it (R1). */
    private fun assertFitsSinglePane(game: SudokuPlayUi, controller: SudokuPlayController, size: Int) {
        setScreen(controller, PlayLayout.SinglePane, game)
        val grid = composeRule.onNodeWithTag("sudoku.grid").getUnclippedBoundsInRoot()
        val keypad = composeRule.onNodeWithTag("sudoku.keypad").getUnclippedBoundsInRoot()
        val bar = composeRule.onNodeWithTag("play.action_bar").getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue("${size}x$size keypad under grid ($grid, $keypad)", keypad.top >= grid.bottom)
        assertTrue("${size}x$size control row under keypad ($keypad, $bar)", bar.top >= keypad.bottom)
        assertTrue("${size}x$size control row on screen ($bar, $root)", bar.bottom <= root.bottom)
    }

    private fun session(controller: SudokuPlayController): SudokuSession = composeRule.runOnIdle { controller.board!!.session }

    private fun cell(idx: Int) = composeRule.onNodeWithTag("sudoku.cell.$idx")

    /** A digit key, in either mode: its label is "<d>, <n> left" or "Note <d>, <n> left". */
    private fun key(digit: Int): SemanticsNodeInteraction = composeRule.onNode(
        SemanticsMatcher("digit key $digit") { node ->
            node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                .any { Regex("^(Note )?$digit, \\d+ left$").matches(it) }
        },
    )

    private fun hint() = composeRule.onNode(hasClickAction() and hasText("Hint", substring = true))

    @Test
    fun `the tour plays 450 from first entry to the solved hand-off`() {
        val controller = adopted()
        setScreen(controller, PlayLayout.SinglePane)

        cell(0).performClick().assertIsSelected()
        key(4).performClick()
        assertEquals(SudokuCell.Entry(4), session(controller).board.cells[0])

        composeRule.onNodeWithTag("sudoku.notes_toggle").performClick()
        cell(1).performClick()
        key(1).performClick()
        assertEquals(SudokuCell.Notes(1), session(controller).board.cells[1])

        composeRule.onNodeWithTag("sudoku.erase").performClick()
        assertEquals(SudokuCell.Empty, session(controller).board.cells[1])

        composeRule.onNodeWithTag("play.undo").performClick()
        assertEquals(SudokuCell.Notes(1), session(controller).board.cells[1])

        val beforeHints = session(controller).board
        hint().performClick()
        assertEquals(1, session(controller).hintsUsed)
        assertEquals(beforeHints, session(controller).board)
        hint().performClick()
        assertEquals(1, session(controller).hintsUsed)
        assertNotEquals(beforeHints, session(controller).board)

        repeat(givens.size * 2) { if (!session(controller).isComplete) hint().performClick() }

        val won = session(controller)
        assertTrue(won.isComplete)
        assertEquals(solution, won.board.cells.map { it.digit })
        composeRule.runOnIdle { assertEquals(listOf(won.elapsedMs to won.hintsUsed), solved) }
        hint().assertIsNotEnabled()
        composeRule.onNodeWithTag("sudoku.notes_toggle").assertIsNotEnabled()
        composeRule.onNodeWithTag("sudoku.erase").assertIsNotEnabled()
        // Clear is never disabled; on a solved board it is a no-op (the win is final).
        composeRule.onNodeWithTag("play.clear").assertIsEnabled().performClick()
        assertEquals(won, session(controller))
        composeRule.runOnIdle { assertEquals(null, controller.board!!.selected) }
        cell(0).performClick().assertIsNotSelected()
    }

    @Test
    fun `clear on an empty board stays enabled and changes nothing`() {
        val controller = adopted()
        setScreen(controller, PlayLayout.SinglePane)
        val before = session(controller)

        composeRule.onNodeWithTag("play.clear").assertIsEnabled().performClick()

        assertEquals(before, session(controller))
        assertFalse(session(controller).history.canUndo)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `two pane clear is enabled on an empty board and a tap changes nothing`() {
        val controller = adopted()
        setScreen(controller, PlayLayout.TwoPane)
        val before = session(controller)

        composeRule.onNodeWithTag("play.clear").assertIsEnabled().performClick()

        assertEquals(before, session(controller))
    }

    @Test
    fun `clear wipes the board in one undoable step`() {
        val controller = adopted()
        setScreen(controller, PlayLayout.SinglePane)
        val clear = composeRule.onNodeWithTag("play.clear")
        clear.assertIsEnabled()

        cell(0).performClick()
        key(4).performClick()
        cell(1).performClick()
        key(6).performClick()
        clear.assertIsEnabled().performClick()

        assertEquals(SudokuSession(ref = ref, puzzle = peanut450).board, session(controller).board)
        composeRule.onNodeWithTag("play.undo").performClick()
        assertEquals(listOf(4, 6), session(controller).board.cells.take(2).map { it.digit })
    }

    @Test
    fun `the tutorial board carries the keypad`() {
        val controller = adopted()
        composeRule.setContent {
            PuzzleBoxTheme { ui.Board(controller, Modifier) }
        }

        composeRule.onNodeWithTag("sudoku.grid").assertExists()
        composeRule.onNodeWithTag("sudoku.keypad").assertExists()
    }
}
